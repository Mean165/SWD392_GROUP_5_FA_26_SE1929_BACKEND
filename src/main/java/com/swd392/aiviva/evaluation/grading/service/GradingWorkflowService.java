package com.swd392.aiviva.evaluation.grading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.evaluation.entity.FinalGradeReport;
import com.swd392.aiviva.evaluation.grading.ai.GradingLlmProvider;
import com.swd392.aiviva.evaluation.grading.domain.GradingScoreCalculator;
import com.swd392.aiviva.evaluation.grading.domain.GradingStatus;
import com.swd392.aiviva.evaluation.grading.persistence.GradingWorkflowJdbcRepository;
import com.swd392.aiviva.evaluation.grading.web.GradingController.CriterionScoreInput;
import com.swd392.aiviva.evaluation.grading.web.GradingController.FinalizeRequest;
import com.swd392.aiviva.evaluation.grading.web.GradingController.ReopenRequest;
import com.swd392.aiviva.evaluation.grading.web.GradingController.SaveScoresRequest;
import com.swd392.aiviva.evaluation.grading.web.GradingController;
import com.swd392.aiviva.evaluation.grading.web.GradingApiException;
import com.swd392.aiviva.exam.attempt.service.OfficialExamResultService;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.user.entity.AppUser;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingWorkflowService {
    private final GradingWorkflowJdbcRepository repository;
    private final FinalGradeReportRepository gradeReports;
    private final ObjectMapper mapper;
    private final BigDecimal roundingStep;
    private final int configuredSamples;
    private final int maxManualRuns;
    private final OfficialExamResultService officialExamResultService;

    public GradingWorkflowService(GradingWorkflowJdbcRepository repository,
            FinalGradeReportRepository gradeReports, ObjectMapper mapper,
            @Value("${grading.rounding-step:0.25}") BigDecimal roundingStep,
            @Value("${grading.ai.samples:3}") int configuredSamples,
            @Value("${grading.ai.max-manual-runs:3}") int maxManualRuns,
            OfficialExamResultService officialExamResultService) {
        this.repository = repository;
        this.gradeReports = gradeReports;
        this.mapper = mapper;
        this.roundingStep = roundingStep;
        this.configuredSamples = Math.max(1, Math.min(10, configuredSamples));
        this.maxManualRuns = Math.max(1, maxManualRuns);
        this.officialExamResultService = officialExamResultService;
    }

    public Map<String, Object> capabilities(GradingLlmProvider provider) {
        return Map.of("available", true,
                "ai", Map.of("ready", provider.isReady(), "provider", provider.providerName(),
                        "model", provider.modelName(), "reason", provider.reason() == null ? "" : provider.reason()),
                "features", Map.of("autoScoring", provider.isReady(), "bulkAccept", false, "release", true), "version", "1");
    }

    @Transactional
    public Map<String, Object> queue(UUID examId, String status, Integer attemptNo, int page, int size,
            OffsetDateTime finishedFrom, OffsetDateTime finishedTo, UUID actor, boolean admin) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException("page must be >= 0 and size must be 1..100");
        if (page > 100_000) throw new BusinessException("page is too large");
        if (finishedFrom != null && finishedTo != null && finishedFrom.isAfter(finishedTo)) {
            throw new BusinessException("finishedFrom must be before finishedTo");
        }
        if (status != null && !java.util.Arrays.stream(GradingStatus.values()).map(Enum::name).anyMatch(status::equals)) {
            throw new BusinessException("Unknown grading status");
        }
        UUID filterExam = examId;
        if (filterExam != null) requireExamAccess(filterExam, actor, admin);
        List<Map<String, Object>> items = repository.queue(filterExam, status, attemptNo, finishedFrom, finishedTo, actor, admin, size, page * size);
        long total = items.isEmpty() ? 0 : ((Number) items.get(0).get("total_count")).longValue();
        items.forEach(item -> item.remove("total_count"));
        return Map.of("items", items, "page", page, "size", size, "total", total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID examId, UUID actor, boolean admin) {
        String sql = "select gs.status,count(*) total from grading_submissions gs where (? is null or gs.exam_id=?) "
                + "and (? or gs.assigned_lecturer_id=? or exists (select 1 from exam_session es join subject_topic t on t.topic_id=es.topic_id "
                + "join subject s on s.subject_id=t.subject_id where es.session_id=gs.exam_id and s.assigned_lecturer_ids_json @> jsonb_build_array(cast(? as text)))) group by gs.status";
        List<Map<String, Object>> rows = repository.queryList(sql, examId, examId, admin, actor, actor);
        Map<String, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(String.valueOf(row.get("status")), ((Number) row.get("total")).longValue()));
        return Map.of("counts", counts, "total", counts.values().stream().mapToLong(Long::longValue).sum());
    }

    @Transactional
    public Map<String, Object> reconcile(UUID actor, boolean admin, GradingLlmProvider provider) {
        if (!admin) throw new org.springframework.security.access.AccessDeniedException("Only administrators may reconcile submissions");
        var result = repository.reconcileCompletedAttempts(actor, provider.isReady() ? provider.providerName() : "disabled",
                provider.modelName(), configuredSamples);
        return Map.of("created", result.created(), "aiRunsQueued", result.queued());
    }

    @Transactional
    public Map<String, Object> enqueueAiRun(UUID id, UUID actor, boolean admin, GradingLlmProvider provider) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        if (!provider.isReady()) throw new GradingApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AI_NOT_READY", provider.reason());
        if (repository.manualRunCount(id) >= maxManualRuns) throw new RateLimitExceededException(3600,
                "AI rerun limit reached for this submission");
        if (List.of("FINALIZED", "RELEASED").contains(String.valueOf(submission.get("status")))) {
            throw new ConflictException("Reopen the submission before running AI again", "ALREADY_FINALIZED");
        }
        String snapshotHash = String.valueOf(repository.queryForMap(
                "select content_hash from grading_rubric_snapshots where snapshot_id=?", submission.get("rubric_snapshot_id")).get("content_hash"));
        int runNo = repository.nextRunNumber(id);
        String inputHash = digest(snapshotHash + ":" + runNo + ":" + provider.providerName() + ":" + provider.modelName());
        UUID runId = UUID.randomUUID();
        int k = repository.samplesForExam((UUID) submission.get("exam_id"), configuredSamples);
        repository.update("insert into ai_grading_runs(run_id,submission_id,run_no,trigger,requested_by,status,provider,model,prompt_version,temperature,k_samples,input_hash) values(?,?,?,'MANUAL',?,'PENDING',?,?,?,0,?,?)",
                runId, id, runNo, actor, provider.providerName(), provider.modelName(), "group4-v1", k, inputHash);
        repository.update("update grading_submissions set ai_status='PENDING',updated_at=current_timestamp where submission_id=?", id);
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "AI_RUN_QUEUED",
                Map.of("runId", runId, "runNo", runNo, "provider", provider.providerName(), "model", provider.modelName()));
        return Map.of("runId", runId, "runNo", runNo, "status", "PENDING", "provider", provider.providerName(), "model", provider.modelName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> aiRuns(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        return repository.queryList("select run_id,run_no,trigger,requested_by,status,provider,model,prompt_version,k_samples,started_at,finished_at,latency_ms,token_usage,error_code,created_at from ai_grading_runs where submission_id=? order by run_no desc", id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        return repository.auditEvents(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> revisions(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        return repository.revisions(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> agreement(UUID examId, UUID actor, boolean admin) {
        requireExamAccess(examId, actor, admin);
        Map<String, Object> result = repository.queryForMap("""
                with comparisons as (
                  select a.ai_suggested_points ai_score,l.score lecturer_score,
                    abs(a.ai_suggested_points-l.score) delta,
                    (a.ai_suggested_points=l.score) exact_match,
                    (abs(a.ai_suggested_points-l.score)<=1) within_one
                  from grading_submissions s join ai_grading_runs r on r.submission_id=s.submission_id and r.status='COMPLETED'
                  join ai_rubric_assessment a on a.run_id=r.run_id
                  join interaction_turn t on t.turn_id=a.turn_id
                  join lecturer_criterion_scores l on l.submission_id=s.submission_id and l.question_id=t.main_question_id and l.criteria_id=a.criteria_id
                  where s.exam_id=? and s.status <> 'VOIDED'
                    and r.run_no=(select max(r2.run_no) from ai_grading_runs r2 where r2.submission_id=s.submission_id and r2.status='COMPLETED')
                )
                select count(*) comparisons,coalesce(avg(delta),0) mean_absolute_error,
                  coalesce(avg(case when exact_match then 1.0 else 0.0 end),0) exact_agreement,
                  coalesce(avg(case when within_one then 1.0 else 0.0 end),0) within_one_agreement
                from comparisons
                """, examId);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settings(UUID examId, UUID actor, boolean admin) {
        requireExamAccess(examId, actor, admin);
        List<Map<String, Object>> rows = repository.queryList("select * from grading_exam_settings where exam_id=?", examId);
        if (!rows.isEmpty()) return rows.get(0);
        return Map.of("exam_id", examId, "auto_ai_scoring", true, "ai_k_samples", configuredSamples,
                "delta_reason_threshold", new BigDecimal("2.0"), "include_signals_in_grading", false,
                "allow_bulk_accept", false, "release_policy", "MANUAL", "rounding_step", roundingStep,
                "scale_max", BigDecimal.TEN, "unanswered_policy", "ZERO_REQUIRES_REVIEW");
    }

    @Transactional
    public Map<String, Object> updateSettings(UUID examId, GradingController.SettingsRequest request, UUID actor, boolean admin) {
        requireExamAccess(examId, actor, admin);
        if (request.allowBulkAccept()) throw new BusinessException("Bulk acceptance is not available in this release");
        if (request.includeSignalsInGrading()) throw new BusinessException("Fairness signals are display-only in this release; includeSignalsInGrading must remain false");
        if (request.deltaReasonThreshold() == null || request.roundingStep() == null || request.scaleMax() == null) {
            throw new BusinessException("All grading thresholds and scales are required");
        }
        if (request.scaleMax().compareTo(BigDecimal.TEN) != 0) {
            throw new BusinessException("The score scale is fixed at 10 in this release");
        }
        if (request.releasePolicy() == null || request.unansweredPolicy() == null) {
            throw new BusinessException("releasePolicy and unansweredPolicy are required");
        }
        if (!"ZERO_REQUIRES_REVIEW".equals(request.unansweredPolicy())) {
            throw new BusinessException("Only ZERO_REQUIRES_REVIEW is supported in this release");
        }
        repository.update("""
                insert into grading_exam_settings(exam_id,auto_ai_scoring,ai_k_samples,delta_reason_threshold,
                  include_signals_in_grading,allow_bulk_accept,release_policy,rounding_step,scale_max,unanswered_policy,updated_by)
                values(?,?,?,?,false,?,?,?,?,?,?) on conflict(exam_id) do update set auto_ai_scoring=excluded.auto_ai_scoring,
                  ai_k_samples=excluded.ai_k_samples,delta_reason_threshold=excluded.delta_reason_threshold,
                  include_signals_in_grading=false,allow_bulk_accept=excluded.allow_bulk_accept,
                  release_policy=excluded.release_policy,rounding_step=excluded.rounding_step,
                  scale_max=excluded.scale_max,unanswered_policy=excluded.unanswered_policy,
                  updated_by=excluded.updated_by,updated_at=current_timestamp
                """, examId, request.autoAiScoring(), request.aiKSamples(), request.deltaReasonThreshold(),
                request.allowBulkAccept(), request.releasePolicy(), request.roundingStep(), request.scaleMax(),
                request.unansweredPolicy(), actor);
        repository.auditExamAction(examId, actor, actorType(admin), "GRADING_SETTINGS_UPDATED", Map.of(
                "autoAiScoring", request.autoAiScoring(), "aiKSamples", request.aiKSamples(),
                "allowBulkAccept", request.allowBulkAccept(), "releasePolicy", request.releasePolicy(),
                "roundingStep", request.roundingStep(), "scaleMax", request.scaleMax(),
                "unansweredPolicy", request.unansweredPolicy()));
        return settings(examId, actor, admin);
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(UUID id, UUID actor, boolean admin) {
        Map<String, Object> detail = repository.detail(id);
        requireSubmissionAccess((UUID) detail.get("exam_id"), actor, admin);
        return detail;
    }

    @Transactional
    public Map<String, Object> claim(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        String status = String.valueOf(submission.get("status"));
        if (!List.of("AI_PENDING", "AI_SCORED", "AI_FAILED", "IN_REVIEW", "REOPENED").contains(status)) {
            throw new ConflictException("Submission cannot be claimed in its current state", "ALREADY_FINALIZED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        UUID holder = (UUID) submission.get("lock_holder_id");
        OffsetDateTime expires = (OffsetDateTime) submission.get("lock_expires_at");
        if (holder != null && !holder.equals(actor) && expires != null && expires.isAfter(now)) {
            throw new GradingApiException(org.springframework.http.HttpStatus.LOCKED, "LOCKED_BY_OTHER",
                    "Submission is currently claimed by another lecturer");
        }
        long version = ((Number) submission.get("version")).longValue();
        int updated = repository.update("update grading_submissions set lock_holder_id=?, lock_expires_at=?, status=case when status in ('AI_SCORED','AI_FAILED','AI_PENDING','REOPENED') then 'IN_REVIEW' else status end, version=version+1, updated_at=? where submission_id=? and version=?",
                actor, now.plusMinutes(10), now, id, version);
        if (updated == 0) throw new ConflictException("Submission version changed; reload the item", "STALE_VERSION");
        if (GradingStatus.valueOf(String.valueOf(submission.get("status"))).canTransitionTo(GradingStatus.IN_REVIEW)) {
            repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "CLAIMED", Map.of());
        }
        return repository.submission(id);
    }

    @Transactional
    public Map<String, Object> saveScores(UUID id, SaveScoresRequest request, UUID actor, boolean admin) {
        Map<String, Object> submission = requireEditable(id, actor, admin);
        checkVersion(submission, request.version());
        advanceVersion(submission, request.version(), "IN_REVIEW");
        java.util.Set<String> cells = new java.util.HashSet<>();
        List<Map<String, Object>> auditScores = new java.util.ArrayList<>();
        for (CriterionScoreInput input : request.scores()) {
            if (input.questionId() == null || input.criterionId() == null || input.score() == null || input.maxScore() == null) {
                throw new BusinessException("Each score requires questionId, criterionId, score and maxScore");
            }
            if (input.score().signum() < 0 || input.maxScore().signum() <= 0) {
                throw new BusinessException("Score must be nonnegative and maxScore must be positive");
            }
            if (input.score().compareTo(input.maxScore()) > 0) throw new BusinessException("Score cannot exceed maxScore");
            String cellKey = input.questionId() + ":" + input.criterionId();
            if (!cells.add(cellKey)) throw new BusinessException("Duplicate question/criterion score in request");
            Map<String, Object> criterionInfo = repository.queryForMap("""
                    select exists(select 1 from grading_rubric_snapshots s
                      cross join lateral jsonb_array_elements(s.content->'turns') t
                      cross join lateral jsonb_array_elements(s.content->'criteria') c
                      where s.snapshot_id=? and t->>'turnType'='MAIN' and t->>'questionId'=? and c->>'criterionId'=?
                    ) allowed,
                    (select (c->>'weight')::numeric*10/(select count(distinct t->>'questionId') from grading_rubric_snapshots ss
                       cross join lateral jsonb_array_elements(ss.content->'turns') t where ss.snapshot_id=? and t->>'turnType'='MAIN')
                     from grading_rubric_snapshots s cross join lateral jsonb_array_elements(s.content->'criteria') c
                     where s.snapshot_id=? and c->>'criterionId'=? limit 1) expected_max
                    """, submission.get("rubric_snapshot_id"), input.questionId().toString(), input.criterionId().toString(),
                    submission.get("rubric_snapshot_id"), submission.get("rubric_snapshot_id"), input.criterionId().toString());
            if (!Boolean.TRUE.equals(criterionInfo.get("allowed")) || criterionInfo.get("expected_max") == null) {
                throw new ConflictException("Question or criterion is outside this submission's immutable rubric snapshot", "RUBRIC_MISMATCH");
            }
            BigDecimal expectedMax = new BigDecimal(criterionInfo.get("expected_max").toString());
            if (input.maxScore().subtract(expectedMax).abs().compareTo(new BigDecimal("0.01")) > 0
                    || input.score().compareTo(expectedMax) > 0) {
                throw new BusinessException("Score exceeds the criterion maximum from the immutable rubric snapshot");
            }
            String source = input.source();
            if (source == null) throw new BusinessException("A lecturer score source is required");
            if ("OVERRIDDEN".equals(source) && (input.overrideReason() == null || input.overrideReason().isBlank())) {
                BigDecimal threshold = repository.queryForMap("select coalesce((select delta_reason_threshold from grading_exam_settings where exam_id=?),2.0) threshold",
                        submission.get("exam_id")).get("threshold") instanceof BigDecimal value ? value : new BigDecimal("2.0");
                List<Map<String, Object>> previousAi = repository.queryList("""
                        select a.ai_suggested_points from ai_rubric_assessment a join interaction_turn t on t.turn_id=a.turn_id
                        where t.attempt_id=? and t.main_question_id=? and a.criteria_id=?
                          and a.run_id=(select run_id from ai_grading_runs where submission_id=? and status='COMPLETED' order by run_no desc limit 1)
                        limit 1
                        """, submission.get("attempt_id"), input.questionId(), input.criterionId(), id);
                boolean reasonRequired = previousAi.isEmpty()
                        || ((BigDecimal) previousAi.get(0).get("ai_suggested_points")).subtract(input.score()).abs().compareTo(threshold) >= 0;
                if (reasonRequired) throw new GradingApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "REASON_REQUIRED", "overrideReason is required for this override");
            }
            if (!List.of("ACCEPTED_AI", "OVERRIDDEN", "MANUAL").contains(source)) throw new BusinessException("Invalid lecturer score source");
            if ("ACCEPTED_AI".equals(source)) throw new BusinessException("Use the explicit accept-ai action to accept AI suggestions");
            repository.update("""
                    insert into lecturer_criterion_scores(submission_id,question_id,criteria_id,score,max_score,source,override_reason,comment,updated_by)
                    values(?,?,?,?,?,?::varchar,?,?,?)
                    on conflict (submission_id,question_id,criteria_id) do update set score=excluded.score,max_score=excluded.max_score,
                    source=excluded.source,override_reason=excluded.override_reason,comment=excluded.comment,
                    updated_by=excluded.updated_by,updated_at=current_timestamp,version=lecturer_criterion_scores.version+1
                    """, id, input.questionId(), input.criterionId(), input.score(), input.maxScore(), source,
                    input.overrideReason(), input.comment(), actor);
            auditScores.add(Map.of("questionId", input.questionId(), "criterionId", input.criterionId(),
                    "score", input.score(), "maxScore", input.maxScore(), "source", source));
        }
        long nextVersion = request.version() + 1;
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "LECTURER_SCORES_SAVED",
                Map.of("scores", auditScores, "version", nextVersion));
        return repository.detail(id);
    }

    @Transactional
    public Map<String, Object> renewLock(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        if (!actor.equals(submission.get("lock_holder_id"))) throw new GradingApiException(org.springframework.http.HttpStatus.LOCKED,
                "LOCKED_BY_OTHER", "Only the current claim holder can renew it");
        repository.update("update grading_submissions set lock_expires_at=?,updated_at=current_timestamp where submission_id=?",
                OffsetDateTime.now().plusMinutes(10), id);
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "LOCK_RENEWED", Map.of());
        return repository.submission(id);
    }

    @Transactional
    public Map<String, Object> releaseLock(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        if (submission.get("lock_holder_id") != null && !actor.equals(submission.get("lock_holder_id")) && !admin) {
            throw new GradingApiException(org.springframework.http.HttpStatus.LOCKED, "LOCKED_BY_OTHER",
                    "Only the current claim holder can release it");
        }
        repository.update("update grading_submissions set lock_holder_id=null,lock_expires_at=null,version=version+1,updated_at=current_timestamp where submission_id=?", id);
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "LOCK_RELEASED", Map.of());
        return repository.submission(id);
    }

    @Transactional
    public Map<String, Object> acceptAi(UUID id, long version, UUID actor, boolean admin) {
        Map<String, Object> submission = requireEditable(id, actor, admin);
        checkVersion(submission, version);
        advanceVersion(submission, version, "IN_REVIEW");
        int count = repository.update("""
                insert into lecturer_criterion_scores(submission_id,question_id,criteria_id,score,max_score,source,based_on_run_id,updated_by)
                select ?, t.main_question_id, a.criteria_id, a.ai_suggested_points,
                       rc.weight_ratio*10/(select count(distinct main.main_question_id) from interaction_turn main where main.attempt_id=t.attempt_id and main.turn_type='MAIN'),
                       'ACCEPTED_AI', a.run_id, ?
                from ai_rubric_assessment a join interaction_turn t on t.turn_id=a.turn_id
                join rubric_criteria rc on rc.criteria_id=a.criteria_id
                where a.run_id=(select run_id from ai_grading_runs where submission_id=? and status='COMPLETED' order by run_no desc limit 1)
                  and t.attempt_id=?
                on conflict (submission_id,question_id,criteria_id) do update set score=excluded.score,
                max_score=excluded.max_score,source='ACCEPTED_AI',based_on_run_id=excluded.based_on_run_id,
                override_reason=null,updated_by=excluded.updated_by,updated_at=current_timestamp,
                version=lecturer_criterion_scores.version+1
                """, id, actor, id, submission.get("attempt_id"));
        if (count == 0) throw new ConflictException("No completed AI suggestions are available", "AI_SUGGESTION_NOT_FOUND");
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "AI_SUGGESTIONS_ACCEPTED", Map.of("scoreCount", count));
        return repository.detail(id);
    }

    @Transactional
    public Map<String, Object> saveQuestionNote(UUID id, UUID questionId,
            GradingController.NoteRequest request, UUID actor, boolean admin) {
        Map<String, Object> submission = requireEditable(id, actor, admin);
        checkVersion(submission, request.version());
        advanceVersion(submission, request.version(), "IN_REVIEW");
        Boolean belongs = (Boolean) repository.queryForMap("""
                select exists(select 1 from grading_rubric_snapshots s
                  cross join lateral jsonb_array_elements(s.content->'turns') t
                  where s.snapshot_id=? and t->>'turnType'='MAIN' and t->>'questionId'=?) allowed
                """, submission.get("rubric_snapshot_id"), questionId.toString()).get("allowed");
        if (!Boolean.TRUE.equals(belongs)) throw new ConflictException("Question is outside this submission", "RUBRIC_MISMATCH");
        String status = request.commentStatus() == null ? "DRAFT" : request.commentStatus();
        if ("APPROVED".equals(status) && (request.studentVisibleComment() == null || request.studentVisibleComment().isBlank())) {
            throw new BusinessException("Student-visible comment text is required before approval");
        }
        repository.update("""
                insert into lecturer_question_notes(submission_id,question_id,lecturer_comment,student_visible_comment,comment_status,updated_by)
                values(?,?,?,?,?,?) on conflict(submission_id,question_id) do update set lecturer_comment=excluded.lecturer_comment,
                  student_visible_comment=excluded.student_visible_comment,comment_status=excluded.comment_status,
                  updated_by=excluded.updated_by,updated_at=current_timestamp,version=lecturer_question_notes.version+1
                """, id, questionId, request.lecturerComment(), request.studentVisibleComment(), status, actor);
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin),
                "QUESTION_NOTE_" + status, Map.of("questionId", questionId));
        return repository.detail(id);
    }

    @Transactional
    public Map<String, Object> finalizeSubmission(UUID id, FinalizeRequest request, UUID actor, boolean admin) {
        Map<String, Object> submission = requireEditable(id, actor, admin);
        checkVersion(submission, request.version());
        if (!request.confirm()) throw new BusinessException("Explicit confirmation is required to finalize");
        Map<String, Object> snapshot = repository.queryForMap("select content,content_hash from grading_rubric_snapshots where snapshot_id=?",
                submission.get("rubric_snapshot_id"));
        List<Map<String, Object>> expected = repository.queryList("""
                select (turn->>'questionId')::uuid question_id, (criterion->>'criterionId')::uuid criterion_id,
                       coalesce(((criterion->>'weight')::numeric*10 /
                         (select count(distinct t2->>'questionId') from grading_rubric_snapshots ss
                          cross join lateral jsonb_array_elements(ss.content->'turns') t2
                          where ss.snapshot_id=s.snapshot_id and t2->>'turnType'='MAIN')),0) max_score
                from grading_rubric_snapshots s
                cross join lateral jsonb_array_elements(s.content->'turns') turn
                cross join lateral jsonb_array_elements(s.content->'criteria') criterion
                where s.snapshot_id=? and turn->>'turnType'='MAIN' and turn->>'questionId' <> ''
                """, submission.get("rubric_snapshot_id"));
        if (expected.isEmpty()) throw new ConflictException("Rubric or main-question snapshot is empty", "RUBRIC_MISMATCH");
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> criteriaSnapshot = new java.util.ArrayList<>();
        for (Map<String, Object> cell : expected) {
            List<Map<String, Object>> scored = repository.queryList("select score,max_score,source from lecturer_criterion_scores where submission_id=? and question_id=? and criteria_id=?",
                    id, cell.get("question_id"), cell.get("criterion_id"));
            if (scored.isEmpty()) throw new GradingApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "INCOMPLETE_SCORES", "Every question criterion needs an explicit lecturer score");
            BigDecimal score = (BigDecimal) scored.get(0).get("score");
            total = total.add(score);
            criteriaSnapshot.add(Map.of("questionId", cell.get("question_id"), "criterionId", cell.get("criterion_id"), "score", score,
                    "maxScore", scored.get(0).get("max_score"), "source", scored.get(0).get("source")));
        }
        BigDecimal scaleMax = BigDecimal.TEN;
        BigDecimal maxPossible = expected.stream().map(cell -> (BigDecimal) cell.get("max_score"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (maxPossible.signum() <= 0) throw new ConflictException("Rubric criteria have no valid score weights", "RUBRIC_MISMATCH");
        total = total.multiply(scaleMax).divide(maxPossible, 4, java.math.RoundingMode.HALF_UP);
        BigDecimal configuredRounding = repository.queryForMap(
                "select coalesce((select rounding_step from grading_exam_settings where exam_id=?),?) rounding_step",
                submission.get("exam_id"), roundingStep).get("rounding_step") instanceof BigDecimal value ? value : roundingStep;
        total = GradingScoreCalculator.applyStep(total, scaleMax, configuredRounding);
        UUID reportId = gradeReports.findByAttempt_AttemptId((UUID) submission.get("attempt_id"))
                .map(FinalGradeReport::getReportId).orElse(null);
        if (reportId == null) {
            FinalGradeReport report = FinalGradeReport.builder().attempt(com.swd392.aiviva.interview.entity.VivaAttempt.builder()
                    .attemptId((UUID) submission.get("attempt_id")).build()).finalizedByLecturer(
                    AppUser.builder().userId(actor).build()).totalAiScore(null).finalTeacherScore(total)
                    .teacherAdjustmentReason(request.note()).finalizedAt(OffsetDateTime.now()).build();
            gradeReports.saveAndFlush(report);
        } else {
            repository.update("update final_grade_report set final_teacher_score=?,finalized_by_lecturer_id=?,finalized_at=?,teacher_adjustment_reason=? where report_id=?",
                    total, actor, OffsetDateTime.now(), request.note(), reportId);
        }
        int revisionNo = ((Number) submission.get("current_revision_no")).intValue() + 1;
        UUID revisionId = UUID.randomUUID();
        String snapshotJson = write(Map.of("criteria", criteriaSnapshot, "total", total,
                "rubricHash", snapshot.get("content_hash"), "note", request.note() == null ? "" : request.note()));
        repository.update("""
                insert into grade_revisions(revision_id,submission_id,revision_no,reason,snapshot,total_score,max_score,
                  rubric_snapshot_id,finalized_by,finalized_actor_type,finalized_at,supersedes_revision_id,note)
                values(?,?,?,case when ?=1 then 'INITIAL' else 'REGRADE' end,?::jsonb,?,10,?,?,?,current_timestamp,
                  (select revision_id from grade_revisions where submission_id=? order by revision_no desc limit 1),?)
                """, revisionId, id, revisionNo, revisionNo, snapshotJson, total, submission.get("rubric_snapshot_id"), actor,
                actorType(admin), id, request.note());
        int updated = repository.update("""
                update grading_submissions set status='FINALIZED',finalized_by=?,finalized_actor_type=?,finalized_at=current_timestamp,
                  current_revision_no=?,version=version+1,updated_at=current_timestamp
                where submission_id=? and version=? and status='IN_REVIEW'
                """, actor, actorType(admin), revisionNo, id, request.version());
        if (updated == 0) throw new ConflictException("Submission version changed or is not in review", "STALE_VERSION");
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "FINALIZED",
                Map.of("revisionId", revisionId, "revisionNo", revisionNo, "totalScore", total));
        String releasePolicy = String.valueOf(repository.queryForMap("select coalesce((select release_policy from grading_exam_settings where exam_id=?),'MANUAL') release_policy",
                submission.get("exam_id")).get("release_policy"));
        if ("AUTO_AFTER_FINALIZE".equals(releasePolicy)) {
            repository.update("update grading_submissions set status='RELEASED',release_status='RELEASED',released_at=current_timestamp,version=version+1 where submission_id=? and version=? and status='FINALIZED'", id, request.version() + 1);
            repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "AUTO_RELEASED_AFTER_FINALIZE", Map.of());
            officialExamResultService.markReleased((UUID) submission.get("exam_id"), (UUID) submission.get("student_id"), actor, actorType(admin));
        }
        officialExamResultService.recompute((UUID) submission.get("exam_id"), (UUID) submission.get("student_id"),
                actor, actorType(admin), "ATTEMPT_FINALIZED");
        return repository.detail(id);
    }

    @Transactional
    public Map<String, Object> release(UUID id, long version, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        checkVersion(submission, version);
        if (!"FINALIZED".equals(submission.get("status"))) throw new ConflictException("Only finalized grades can be released", "NOT_FINALIZED");
        int updated = repository.update("update grading_submissions set status='RELEASED',release_status='RELEASED',released_at=current_timestamp,version=version+1 where submission_id=? and version=? and status='FINALIZED'", id, version);
        if (updated == 0) throw new ConflictException("Submission version changed; reload the item", "STALE_VERSION");
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "RELEASED", Map.of());
        officialExamResultService.markReleased((UUID) submission.get("exam_id"), (UUID) submission.get("student_id"), actor, actorType(admin));
        return repository.submission(id);
    }

    @Transactional
    public Map<String, Object> reopen(UUID id, ReopenRequest request, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        checkVersion(submission, request.version());
        if (!List.of("FINALIZED", "RELEASED").contains(String.valueOf(submission.get("status")))) {
            throw new ConflictException("Only finalized grades can be reopened", "NOT_FINALIZED");
        }
        int updated = repository.update("update grading_submissions set status='REOPENED',release_status='NOT_RELEASED',released_at=null,finalized_at=null,finalized_by=null,finalized_actor_type=null,version=version+1,updated_at=current_timestamp where submission_id=? and version=? and status in ('FINALIZED','RELEASED')", id, request.version());
        if (updated == 0) throw new ConflictException("Submission version changed; reload the item", "STALE_VERSION");
        repository.update("update final_grade_report set final_teacher_score=null,finalized_by_lecturer_id=null,finalized_at=null where attempt_id=?",
                submission.get("attempt_id"));
        officialExamResultService.recompute((UUID) submission.get("exam_id"), (UUID) submission.get("student_id"),
                actor, actorType(admin), "GRADING_REOPENED");
        repository.auditAction(id, (UUID) submission.get("exam_id"), actor, actorType(admin), "REOPENED",
                Map.of("reason", request.reason(), "note", request.note() == null ? "" : request.note()));
        return repository.submission(id);
    }

    private Map<String, Object> requireEditable(UUID id, UUID actor, boolean admin) {
        Map<String, Object> submission = repository.submission(id);
        requireSubmissionAccess((UUID) submission.get("exam_id"), actor, admin);
        if (!"IN_REVIEW".equals(submission.get("status"))) {
            throw new ConflictException("Claim the submission before editing", "NOT_IN_REVIEW");
        }
        if (!actor.equals(submission.get("lock_holder_id"))) throw new GradingApiException(org.springframework.http.HttpStatus.LOCKED,
                "LOCKED_BY_OTHER", "Claim the submission before editing");
        Object expires = submission.get("lock_expires_at");
        if (expires instanceof OffsetDateTime value && value.isBefore(OffsetDateTime.now())) {
            throw new GradingApiException(org.springframework.http.HttpStatus.LOCKED, "LOCK_EXPIRED",
                    "The grading claim expired; claim it again");
        }
        return submission;
    }

    private void checkVersion(Map<String, Object> submission, long version) {
        if (((Number) submission.get("version")).longValue() != version) throw new ConflictException("Submission version changed; reload the item", "STALE_VERSION");
    }

    private void advanceVersion(Map<String, Object> submission, long version, String expectedStatus) {
        int updated = repository.update("update grading_submissions set version=version+1,updated_at=current_timestamp where submission_id=? and version=? and status=?",
                submission.get("submission_id"), version, expectedStatus);
        if (updated == 0) throw new ConflictException("Submission version changed; reload the item", "STALE_VERSION");
    }

    private void requireSubmissionAccess(UUID examId, UUID actor, boolean admin) {
        requireExamAccess(examId, actor, admin);
    }

    private void requireExamAccess(UUID examId, UUID actor, boolean admin) {
        if (admin) return;
        Boolean allowed = repository.queryForMap("""
                select exists(select 1 from exam_session es join subject_topic t on t.topic_id=es.topic_id
                join subject s on s.subject_id=t.subject_id where es.session_id=? and
                (es.created_by=? or s.assigned_lecturer_ids_json @> jsonb_build_array(cast(? as text)))) allowed
                """, examId, actor, actor).get("allowed") instanceof Boolean b ? b : false;
        if (!allowed) throw new org.springframework.security.access.AccessDeniedException("Lecturer is not assigned to this exam");
    }

    private String actorType(boolean admin) { return admin ? "ADMIN" : "LECTURER"; }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize grading revision", e); }
    }
}
