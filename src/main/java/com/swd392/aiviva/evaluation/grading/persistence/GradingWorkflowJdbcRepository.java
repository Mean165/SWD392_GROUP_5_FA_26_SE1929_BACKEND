package com.swd392.aiviva.evaluation.grading.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GradingWorkflowJdbcRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper mapper;

    public GradingWorkflowJdbcRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.named = named;
        this.mapper = mapper;
    }

    public ReconcileResult reconcileCompletedAttempts(UUID actorId, String provider, String model, int configuredSamples) {
        List<AttemptRow> attempts = jdbc.query("""
                select va.attempt_id, va.exam_attempt_id, ea.attempt_no, ass.session_id exam_id, ass.student_id,
                       coalesce(va.end_time, current_timestamp) finished_at, gs.submission_id existing_submission_id,gs.ai_status
                from viva_attempt va
                join student_session_assignment ass on ass.assignment_id=va.assignment_id
                join exam_attempts ea on ea.attempt_id=va.exam_attempt_id
                left join grading_submissions gs on gs.attempt_id=va.attempt_id
                where upper(va.status)='COMPLETED' and ea.status='COMPLETED' and ea.counted=true
                  and (gs.submission_id is null or (? <> 'disabled' and gs.ai_status='NONE'
                    and coalesce((select auto_ai_scoring from grading_exam_settings where exam_id=ass.session_id),true)))
                  and not exists (select 1 from final_grade_report f where f.attempt_id=va.attempt_id and f.final_teacher_score is not null)
                order by va.end_time nulls last, va.attempt_id
                limit 500
                """, (rs, row) -> new AttemptRow(rs.getObject("attempt_id", UUID.class),
                rs.getObject("exam_attempt_id", UUID.class), rs.getInt("attempt_no"),
                rs.getObject("exam_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getObject("existing_submission_id", UUID.class), rs.getString("ai_status")), provider);
        int created = 0;
        int queued = 0;
        for (AttemptRow attempt : attempts) {
            UUID submissionId = attempt.existingSubmissionId();
            if (submissionId == null) {
                submissionId = UUID.randomUUID();
                boolean autoScore = !"disabled".equals(provider) && Boolean.TRUE.equals(jdbc.queryForObject(
                        "select coalesce((select auto_ai_scoring from grading_exam_settings where exam_id=?),true)", Boolean.class, attempt.examId()));
                String status = autoScore ? "AI_PENDING" : "IN_REVIEW";
                UUID assigned = jdbc.queryForObject("select created_by from exam_session where session_id=?", UUID.class, attempt.examId());
                int inserted = jdbc.update("""
                    insert into grading_submissions(submission_id, attempt_id, exam_attempt_id, attempt_no, exam_id, student_id,
                          assigned_lecturer_id, status, ai_status, session_finished_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (attempt_id) do nothing
                    """, submissionId, attempt.attemptId(), attempt.examAttemptId(), attempt.attemptNo(),
                        attempt.examId(), attempt.studentId(), assigned, status,
                        autoScore ? "PENDING" : "NONE", attempt.finishedAt());
                if (inserted == 0) continue;
                UUID rubricSnapshot = snapshot(attempt, submissionId);
                jdbc.update("update grading_submissions set rubric_snapshot_id=?, updated_at=current_timestamp where submission_id=?",
                        rubricSnapshot, submissionId);
                audit(submissionId, attempt.examId(), actorId, "SYSTEM", "SUBMISSION_CREATED", Map.of("source", "completed_attempt"));
                created++;
                if (!autoScore) continue;
            }
            if (!"disabled".equals(provider) && ("NONE".equals(attempt.aiStatus()) || attempt.existingSubmissionId() == null)) {
                boolean autoScore = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select coalesce((select auto_ai_scoring from grading_exam_settings where exam_id=?),true)", Boolean.class, attempt.examId()));
                if (!autoScore) continue;
                UUID snapshotId = jdbc.queryForObject("select rubric_snapshot_id from grading_submissions where submission_id=?", UUID.class, submissionId);
                String hash = jdbc.queryForObject("select content_hash from grading_rubric_snapshots where snapshot_id=?", String.class, snapshotId);
                int runNo = jdbc.queryForObject("select coalesce(max(run_no),0)+1 from ai_grading_runs where submission_id=?", Integer.class, submissionId);
                int k = samplesForExam(attempt.examId(), configuredSamples);
                jdbc.update("insert into ai_grading_runs(submission_id,run_no,trigger,status,provider,model,prompt_version,k_samples,input_hash) values(?,?,'AUTO','PENDING',?,?,?, ?,?)",
                        submissionId, runNo, provider, model, "group4-v1", k, hash);
                jdbc.update("update grading_submissions set ai_status='PENDING',status='AI_PENDING',updated_at=current_timestamp where submission_id=?", submissionId);
                audit(submissionId, attempt.examId(), actorId, "SYSTEM", "AI_RUN_QUEUED", Map.of("runNo", runNo, "provider", provider, "model", model));
                queued++;
            }
        }
        return new ReconcileResult(created, queued);
    }

    private UUID snapshot(AttemptRow attempt, UUID submissionId) {
        List<Map<String, Object>> turns = jdbc.query("""
                select t.turn_id, t.main_question_id question_id, t.turn_order, t.turn_type,
                       t.ai_question_text question_text, t.transcript_text,
                       sr.text_normalized transcript_normalized, sr.needs_human_review,
                       sr.review_reasons_json, sr.corrections_json,
                       q.bloom_level
                from interaction_turn t
                left join stt_result sr on sr.turn_id=t.turn_id
                left join question q on q.question_id=t.main_question_id
                where t.attempt_id=? order by t.turn_order
                """, (rs, row) -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("turnId", rs.getObject("turn_id", UUID.class));
                    result.put("questionId", rs.getObject("question_id", UUID.class) == null ? "" : rs.getObject("question_id", UUID.class));
                    result.put("order", rs.getInt("turn_order"));
                    result.put("turnType", rs.getString("turn_type"));
                    result.put("questionText", rs.getString("question_text"));
                    result.put("transcriptRaw", rs.getString("transcript_text") == null ? "" : rs.getString("transcript_text"));
                    result.put("transcriptNormalized", rs.getString("transcript_normalized") == null ? "" : rs.getString("transcript_normalized"));
                    result.put("needsHumanReview", rs.getObject("needs_human_review") == null ? false : rs.getBoolean("needs_human_review"));
                    result.put("reviewReasons", rs.getString("review_reasons_json") == null ? List.of() : parseJson(rs.getString("review_reasons_json")));
                    result.put("corrections", rs.getString("corrections_json") == null ? List.of() : parseJson(rs.getString("corrections_json")));
                    result.put("bloomLevel", rs.getString("bloom_level") == null ? "" : rs.getString("bloom_level"));
                    return result;
                });
        List<Map<String, Object>> criteria = jdbc.query("""
                select rc.criteria_id, rc.criteria_name, rc.expected_knowledge_points, rc.weight_ratio
                from viva_attempt va join student_session_assignment ass on ass.assignment_id=va.assignment_id
                join exam_session es on es.session_id=ass.session_id
                join subject_topic topic on topic.topic_id=es.topic_id
                join rubric r on r.subject_id=topic.subject_id and r.status='ACTIVE'
                join rubric_criteria rc on rc.rubric_id=r.rubric_id
                where va.attempt_id=? order by rc.criteria_id
                """, (rs, row) -> Map.of("criterionId", rs.getObject("criteria_id", UUID.class),
                "name", rs.getString("criteria_name"), "expectedPoints", rs.getString("expected_knowledge_points"),
                "weight", rs.getBigDecimal("weight_ratio")) , attempt.attemptId());
        Map<String, Object> content = Map.of("examId", attempt.examId(), "turns", turns, "criteria", criteria,
                "scoreScale", BigDecimal.TEN, "createdAt", OffsetDateTime.now());
        String json = write(content);
        String hash = sha256(json);
        UUID snapshotId = UUID.randomUUID();
        jdbc.update("insert into grading_rubric_snapshots(snapshot_id,submission_id,snapshot_no,content,content_hash) values(?,?,1,?::jsonb,?)",
                snapshotId, submissionId, json, hash);
        return snapshotId;
    }

    private void audit(UUID submissionId, UUID examId, UUID actorId, String actorType, String action, Object payload) {
        jdbc.execute("select pg_advisory_xact_lock(hashtextextended('grading:' || '" + submissionId + "', 0))");
        String previous = jdbc.query("select hash from grading_audit_events where submission_id=? order by seq desc limit 1",
                rs -> rs.next() ? rs.getString(1) : null, submissionId);
        Long current = jdbc.query("select coalesce(max(seq),0) from grading_audit_events where submission_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L, submissionId);
        String body = write(Map.of("submissionId", submissionId, "examId", examId, "actorType", actorType,
                "action", action, "payload", payload));
        jdbc.update("insert into grading_audit_events(submission_id,exam_id,seq,actor_id,actor_type,action,payload,prev_hash,hash) values(?,?,?,?,?,?,?::jsonb,?,?)",
                submissionId, examId, current + 1, actorId, actorType, action, write(payload), previous, sha256((previous == null ? "" : previous) + "\n" + body));
    }

    public List<Map<String, Object>> queue(UUID examId, String status, Integer attemptNo, OffsetDateTime finishedFrom,
            OffsetDateTime finishedTo, UUID lecturerId, boolean admin, int limit, int offset) {
        String sql = """
                select gs.submission_id, gs.attempt_id, gs.exam_attempt_id, gs.attempt_no, gs.exam_id, gs.student_id, u.full_name student_name,
                  es.session_name exam_name, gs.status, gs.ai_status, gs.priority_score, gs.priority_reasons::text priority_reasons,
                  gs.session_finished_at, gs.ai_scored_at, gs.finalized_at, gs.version,
                  count(*) over() total_count
                from grading_submissions gs join app_user u on u.user_id=gs.student_id
                join exam_session es on es.session_id=gs.exam_id
                where (:examId is null or gs.exam_id=:examId) and (:status is null or gs.status=:status)
                  and (:attemptNo is null or gs.attempt_no=:attemptNo)
                  and (:finishedFrom is null or gs.session_finished_at >= :finishedFrom)
                  and (:finishedTo is null or gs.session_finished_at <= :finishedTo)
                  and (:admin or gs.assigned_lecturer_id=:lecturerId or exists (
                    select 1 from subject s join subject_topic t on t.subject_id=s.subject_id
                    where t.topic_id=es.topic_id and s.assigned_lecturer_ids_json @> jsonb_build_array(cast(:lecturerId as text))))
                order by gs.priority_score desc, gs.session_finished_at desc
                limit :limit offset :offset
                """;
        return named.queryForList(sql, new MapSqlParameterSource().addValue("examId", examId)
                .addValue("status", status).addValue("attemptNo", attemptNo).addValue("finishedFrom", finishedFrom).addValue("finishedTo", finishedTo)
                .addValue("admin", admin).addValue("lecturerId", lecturerId)
                .addValue("limit", limit).addValue("offset", offset));
    }

    public Map<String, Object> detail(UUID submissionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select gs.submission_id,gs.attempt_id,gs.exam_attempt_id,gs.attempt_no,gs.exam_id,gs.student_id,gs.assigned_lecturer_id,gs.status,
                  gs.ai_status,gs.release_status,gs.lock_holder_id,gs.lock_expires_at,gs.session_finished_at,
                  gs.ai_scored_at,gs.finalized_at,gs.finalized_by,gs.finalized_actor_type,gs.released_at,
                  gs.priority_score,gs.priority_reasons::text priority_reasons,gs.current_revision_no,gs.version,
                  gs.created_at,gs.updated_at,gs.rubric_snapshot_id,es.session_name exam_name, u.full_name student_name,
                    snap.content rubric_snapshot, snap.content_hash rubric_hash
                from grading_submissions gs join exam_session es on es.session_id=gs.exam_id
                join app_user u on u.user_id=gs.student_id
                left join grading_rubric_snapshots snap on snap.snapshot_id=gs.rubric_snapshot_id
                where gs.submission_id=?
                """, submissionId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Submission not found");
        Map<String, Object> detail = cleanRow(rows.get(0));
        UUID attemptId = (UUID) detail.get("attempt_id");
        detail.put("turns", jdbc.queryForList("""
                select t.turn_id,t.main_question_id,t.turn_order,t.turn_type,t.ai_question_text,t.transcript_text,
                  sr.text_normalized,sr.needs_human_review,sr.review_reasons_json::text review_reasons_json,
                  sr.corrections_json::text corrections_json, af.stt_confidence_score,af.words_per_minute,af.response_latency_ms,
                  af.speech_duration_ms,af.longest_pause_ms,af.filler_count
                from interaction_turn t left join stt_result sr on sr.turn_id=t.turn_id
                left join acoustic_fluency_metric af on af.turn_id=t.turn_id
                where t.attempt_id=? order by t.turn_order
                """, attemptId));
        detail.put("lecturerScores", jdbc.queryForList("select * from lecturer_criterion_scores where submission_id=?", submissionId));
        detail.put("aiRuns", jdbc.queryForList("select run_id,run_no,status,provider,model,prompt_version,started_at,finished_at,error_code from ai_grading_runs where submission_id=? order by run_no desc", submissionId));
        detail.put("aiSuggestions", jdbc.queryForList("""
                select a.assessment_id,a.run_id,t.main_question_id question_id,a.criteria_id,
                  rc.criteria_name,a.ai_suggested_points,a.missing_concepts,a.ai_explanation_reasoning,
                  a.sample_scores_json::text sample_scores_json,a.variance,a.confidence,a.evidence_verified
                from ai_rubric_assessment a join interaction_turn t on t.turn_id=a.turn_id
                join rubric_criteria rc on rc.criteria_id=a.criteria_id
                where t.attempt_id=? and a.run_id=(select run_id from ai_grading_runs where submission_id=? and status='COMPLETED' order by run_no desc limit 1)
                order by t.turn_order,rc.criteria_id
                """, attemptId, submissionId));
        detail.put("aiFeedback", jdbc.queryForList("""
                select f.question_id,f.strengths::text strengths,f.weaknesses::text weaknesses,
                  f.missing_points::text missing_points,f.draft_student_comment,f.flags::text flags,f.needs_attention
                from ai_question_feedback f where f.run_id=(select run_id from ai_grading_runs where submission_id=? and status='COMPLETED' order by run_no desc limit 1)
                """, submissionId));
        detail.put("aiEvidence", jdbc.queryForList("""
                select e.evidence_id,e.question_id,e.criteria_id,e.turn_id,e.quote,e.char_start,e.char_end,e.verified,e.verification_code
                from ai_criterion_evidence e where e.run_id=(select run_id from ai_grading_runs where submission_id=? and status='COMPLETED' order by run_no desc limit 1)
                order by e.question_id,e.criteria_id,e.char_start
                """, submissionId));
        detail.put("lecturerNotes", jdbc.queryForList("select question_id,lecturer_comment,student_visible_comment,comment_status,updated_by,updated_at,version from lecturer_question_notes where submission_id=?", submissionId));
        return detail;
    }

    public List<Map<String, Object>> auditEvents(UUID submissionId) {
        return jdbc.queryForList("select event_id,seq,ts,actor_id,actor_type,action,payload::text payload,prev_hash,hash from grading_audit_events where submission_id=? order by seq", submissionId);
    }

    public List<Map<String, Object>> revisions(UUID submissionId) {
        return jdbc.queryForList("select revision_id,revision_no,reason,snapshot::text snapshot,total_score,max_score,rubric_snapshot_id,ai_run_id,finalized_by,finalized_actor_type,finalized_at,supersedes_revision_id,note from grade_revisions where submission_id=? order by revision_no", submissionId);
    }

    public Map<String, Object> submission(UUID submissionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select submission_id,attempt_id,exam_attempt_id,attempt_no,exam_id,student_id,assigned_lecturer_id,status,ai_status,release_status,
                  lock_holder_id,lock_expires_at,session_finished_at,ai_scored_at,finalized_at,finalized_by,finalized_actor_type,
                  released_at,priority_score,priority_reasons::text priority_reasons,
                  current_revision_no,version,created_at,updated_at,rubric_snapshot_id
                from grading_submissions where submission_id=?
                """, submissionId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Submission not found");
        return cleanRow(rows.get(0));
    }

    public UUID claimNextAiRun() {
        List<UUID> ids = jdbc.query("""
                with candidate as (
                  select r.run_id from ai_grading_runs r join grading_submissions s on s.submission_id=r.submission_id
                  where s.status <> 'VOIDED' and ((r.status='PENDING' and (r.retry_at is null or r.retry_at <= current_timestamp))
                     or (r.status='RUNNING' and r.lease_expires_at < current_timestamp))
                  order by r.created_at for update of r skip locked limit 1
                )
                update ai_grading_runs r set status='RUNNING',started_at=coalesce(r.started_at,current_timestamp),
                  attempt_count=r.attempt_count+1,
                  lease_expires_at=current_timestamp + interval '5 minutes'
                from candidate c where r.run_id=c.run_id returning r.run_id
                """, (rs, row) -> rs.getObject(1, UUID.class));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void markAiRunStarted(UUID runId) {
        Map<String, Object> row = jdbc.queryForMap("select submission_id from ai_grading_runs where run_id=?", runId);
        UUID submissionId = (UUID) row.get("submission_id");
        Map<String, Object> submission = submission(submissionId);
        int updated = jdbc.update("update grading_submissions set ai_status='RUNNING',status=case when status='AI_PENDING' then 'AI_SCORING' else status end,version=version+1,updated_at=current_timestamp where submission_id=? and status <> 'VOIDED'",
                submissionId);
        if (updated == 0) {
            jdbc.update("update ai_grading_runs set status='FAILED',error_code='ATTEMPT_VOIDED',finished_at=current_timestamp,lease_expires_at=null where run_id=?",
                    runId);
            return;
        }
        audit(submissionId, (UUID) submission.get("exam_id"), null, "AI", "AI_RUN_STARTED", Map.of("runId", runId));
    }

    public boolean isSubmissionVoided(UUID submissionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select status='VOIDED' from grading_submissions where submission_id=?", Boolean.class, submissionId));
    }

    public Map<String, Object> aiRun(UUID runId) {
        return jdbc.queryForMap("""
                select r.*, s.attempt_id, s.exam_id, s.status submission_status, s.rubric_snapshot_id,
                  snap.content::text snapshot_json
                from ai_grading_runs r join grading_submissions s on s.submission_id=r.submission_id
                join grading_rubric_snapshots snap on snap.snapshot_id=s.rubric_snapshot_id
                where r.run_id=?
                """, runId);
    }

    public UUID examOwner(UUID examId) {
        return jdbc.queryForObject("select created_by from exam_session where session_id=?", UUID.class, examId);
    }

    public int nextRunNumber(UUID submissionId) {
        return jdbc.queryForObject("select coalesce(max(run_no),0)+1 from ai_grading_runs where submission_id=?", Integer.class, submissionId);
    }

    public int manualRunCount(UUID submissionId) {
        return jdbc.queryForObject("select count(*) from ai_grading_runs where submission_id=? and trigger='MANUAL'", Integer.class, submissionId);
    }

    public int samplesForExam(UUID examId, int fallback) {
        return jdbc.queryForObject("select coalesce((select ai_k_samples from grading_exam_settings where exam_id=?),?)", Integer.class, examId, fallback);
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public UUID oneUuid(String sql, Object... args) { return jdbc.queryForObject(sql, UUID.class, args); }
    public BigDecimal oneDecimal(String sql, Object... args) { return jdbc.queryForObject(sql, BigDecimal.class, args); }
    public long oneLong(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    public List<Map<String, Object>> queryList(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public void auditAction(UUID submissionId, UUID examId, UUID actorId, String actorType, String action, Object payload) {
        audit(submissionId, examId, actorId, actorType, action, payload);
    }
    public void auditExamAction(UUID examId, UUID actorId, String actorType, String action, Object payload) {
        jdbc.execute("select pg_advisory_xact_lock(hashtextextended('grading-exam:' || '" + examId + "', 0))");
        String previous = jdbc.query("select hash from grading_exam_audit_events where exam_id=? order by seq desc limit 1",
                rs -> rs.next() ? rs.getString(1) : null, examId);
        Long current = jdbc.query("select coalesce(max(seq),0) from grading_exam_audit_events where exam_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L, examId);
        String body = write(Map.of("examId", examId, "actorType", actorType, "action", action, "payload", payload));
        jdbc.update("insert into grading_exam_audit_events(exam_id,seq,actor_id,actor_type,action,payload,prev_hash,hash) values(?,?,?,?,?,?,?::jsonb,?)",
                examId, current + 1, actorId, actorType, action, write(payload), previous, sha256((previous == null ? "" : previous) + "\n" + body));
    }
    public Object parseJson(String json) {
        try { return mapper.readTree(json); } catch (JsonProcessingException e) { return json; }
    }
    private Map<String, Object> cleanRow(Map<String, Object> row) { return row; }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize grading snapshot", e); }
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record ReconcileResult(int created, int queued) { }
    private record AttemptRow(UUID attemptId, UUID examAttemptId, int attemptNo, UUID examId, UUID studentId, OffsetDateTime finishedAt,
            UUID existingSubmissionId, String aiStatus) { }
}
