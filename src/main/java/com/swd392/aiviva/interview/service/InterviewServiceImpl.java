package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.AttemptExpiredException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.exam.service.ExamEntryEligibility;
import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.dto.response.AiAssessmentResponse;
import com.swd392.aiviva.interview.dto.response.InteractionTurnResponse;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;
import com.swd392.aiviva.interview.dto.response.SubmitAnswerResponse;
import com.swd392.aiviva.interview.dto.AnswerAnalysisContext;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import com.swd392.aiviva.interview.entity.AiRubricAssessment;
import com.swd392.aiviva.interview.entity.InteractionTurn;
import com.swd392.aiviva.interview.entity.AcousticFluencyMetric;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.enums.InterviewStatus;
import com.swd392.aiviva.interview.repository.AiRubricAssessmentRepository;
import com.swd392.aiviva.interview.repository.AcousticFluencyMetricRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.RubricCriteria;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.system.application.SystemLanguageDefaultsProvider;
import com.swd392.aiviva.system.enums.Language;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewServiceImpl implements InterviewService {

    private final VivaAttemptRepository vivaAttemptRepository;
    private final InteractionTurnRepository interactionTurnRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final QuestionRepository questionRepository;
    private final RubricCriteriaRepository rubricCriteriaRepository;
    private final AiRubricAssessmentRepository rubricAssessmentRepository;
    private final AcousticFluencyMetricRepository acousticFluencyMetricRepository;
    private final UserRepository userRepository;
    private final TextToSpeechService ttsService;
    private final SpeechToTextService sttService;
    private final AnswerAnalysisService answerAnalysisService;
    private final InterviewConfig interviewConfig;
    private final InterviewAudioStorageService audioStorageService;
    private final SystemLanguageDefaultsProvider languageDefaults;

    public InterviewServiceImpl(
            VivaAttemptRepository vivaAttemptRepository,
            InteractionTurnRepository interactionTurnRepository,
            StudentSessionAssignmentRepository assignmentRepository,
            QuestionRepository questionRepository,
            RubricCriteriaRepository rubricCriteriaRepository,
            AiRubricAssessmentRepository rubricAssessmentRepository,
            AcousticFluencyMetricRepository acousticFluencyMetricRepository,
            UserRepository userRepository,
            TextToSpeechService ttsService,
            SpeechToTextService sttService,
            AnswerAnalysisService answerAnalysisService,
            InterviewConfig interviewConfig,
            InterviewAudioStorageService audioStorageService,
            SystemLanguageDefaultsProvider languageDefaults) {
        this.vivaAttemptRepository = vivaAttemptRepository;
        this.interactionTurnRepository = interactionTurnRepository;
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.rubricCriteriaRepository = rubricCriteriaRepository;
        this.rubricAssessmentRepository = rubricAssessmentRepository;
        this.acousticFluencyMetricRepository = acousticFluencyMetricRepository;
        this.userRepository = userRepository;
        this.ttsService = ttsService;
        this.sttService = sttService;
        this.answerAnalysisService = answerAnalysisService;
        this.interviewConfig = interviewConfig;
        this.audioStorageService = audioStorageService;
        this.languageDefaults = languageDefaults;
    }

    @Override
    @Transactional(noRollbackFor = AttemptExpiredException.class)
    public InterviewResponse startInterview(StartInterviewRequest request) {
        AppUser student = currentStudent();
        StudentSessionAssignment assignment = assignmentRepository
                .findBySessionAndStudentForUpdate(request.getSessionId(), student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("No scheduled assignment found for this session and student"));
        com.swd392.aiviva.exam.dto.response.ExamEntryEligibilityResponse eligibility =
                ExamEntryEligibility.evaluate(assignment, OffsetDateTime.now());
        if (!eligibility.isCanEnter()) {
            throw new BusinessException(eligibility.getReason());
        }
        if (assignment.getExamSession().getTopic() == null) {
            throw new BusinessException("An interview topic must be configured for this exam session.");
        }

        VivaAttempt attempt = vivaAttemptRepository.findByAssignment_AssignmentId(assignment.getAssignmentId())
                .orElse(null);

        if (attempt == null) {
            attempt = VivaAttempt.builder()
                    .assignment(assignment)
                    .status(InterviewStatus.IN_PROGRESS.name())
                    .startTime(OffsetDateTime.now())
                    .build();
            attempt = vivaAttemptRepository.save(attempt);
        } else if (InterviewStatus.COMPLETED.name().equalsIgnoreCase(attempt.getStatus())) {
            throw new BusinessException("This interview attempt has already been completed.");
        } else if (!InterviewStatus.IN_PROGRESS.name().equalsIgnoreCase(attempt.getStatus())) {
            attempt.setStatus(InterviewStatus.IN_PROGRESS.name());
            attempt.setStartTime(OffsetDateTime.now());
            attempt = vivaAttemptRepository.save(attempt);
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(assignment.getStatus())) {
            assignment.setStatus("IN_PROGRESS");
            assignmentRepository.save(assignment);
        }

        List<InteractionTurn> existingTurns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attempt.getAttemptId());
        if (expireAttemptIfNeeded(attempt, existingTurns.isEmpty() ? null : existingTurns.get(existingTurns.size() - 1))) {
            throw new AttemptExpiredException("This interview has expired.");
        }
        InteractionTurn currentTurn;

        if (existingTurns.isEmpty()) {
            Set<UUID> previousQuestionIds = previousStudentQuestionIds(assignment);
            Optional<Question> firstQuestion = questionRepository
                    .findByTopic_TopicIdAndApprovalStatusOrderByCreatedAtAsc(
                            assignment.getExamSession().getTopic().getTopicId(), "APPROVED")
                    .stream()
                    .filter(question -> !previousQuestionIds.contains(question.getQuestionId()))
                    .findFirst();
            if (firstQuestion.isEmpty()) {
                throw new BusinessException("No approved questions are available for this interview.");
            }

            currentTurn = InteractionTurn.builder()
                    .attempt(attempt)
                    .mainQuestion(firstQuestion.get())
                    .turnOrder(1)
                    .turnType("MAIN")
                    .aiQuestionText(firstQuestion.get().getQuestionText())
                    .createdAt(OffsetDateTime.now())
                    .build();
            currentTurn = interactionTurnRepository.save(currentTurn);
            existingTurns.add(currentTurn);
        } else {
            currentTurn = existingTurns.get(existingTurns.size() - 1);
        }

        return toInterviewResponse(attempt, currentTurn, existingTurns);
    }

    @Override
    @Transactional
    public InterviewResponse getInterviewStatus(UUID attemptId) {
        VivaAttempt attempt = vivaAttemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));
        assertOwner(attempt);

        List<InteractionTurn> turns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId);
        expireAttemptIfNeeded(attempt, turns.isEmpty() ? null : turns.get(turns.size() - 1));
        InteractionTurn currentTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);

        return toInterviewResponse(attempt, currentTurn, turns);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InteractionTurnResponse> getInterviewTurns(UUID attemptId) {
        VivaAttempt attempt = vivaAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));
        assertOwner(attempt);
        return interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId)
                .stream()
                .map(this::toTurnResponse)
                .toList();
    }

    @Override
    @Transactional(noRollbackFor = AttemptExpiredException.class)
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        VivaAttempt attempt = vivaAttemptRepository.findByIdForUpdate(request.getAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + request.getAttemptId()));
        assertOwner(attempt);

        List<InteractionTurn> allTurns = interactionTurnRepository
                .findByAttempt_AttemptIdOrderByTurnOrderAsc(attempt.getAttemptId());
        InteractionTurn turn = interactionTurnRepository.findById(request.getTurnId())
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found: " + request.getTurnId()));
        if (!turn.getAttempt().getAttemptId().equals(attempt.getAttemptId())) {
            throw new AccessDeniedException("The requested turn does not belong to this interview.");
        }

        if (turn.getTranscriptText() != null && turn.getAiReasoning() != null) {
            return toSubmitAnswerResponse(attempt, turn, allTurns);
        }
        if (!InterviewStatus.IN_PROGRESS.name().equalsIgnoreCase(attempt.getStatus())) {
            throw new BusinessException("This interview is not accepting answers.");
        }
        InteractionTurn latestTurn = allTurns.isEmpty() ? null : allTurns.get(allTurns.size() - 1);
        if (latestTurn == null || !latestTurn.getTurnId().equals(turn.getTurnId())) {
            throw new BusinessException("Answers can only be submitted for the current interview turn.");
        }
        if (expireAttemptIfNeeded(attempt, turn)) {
            throw new AttemptExpiredException("The answer deadline has passed and this interview is closed.");
        }

        boolean hasText = request.getTranscriptText() != null && !request.getTranscriptText().isBlank();
        boolean hasAudio = request.getAudioSegmentUrl() != null && !request.getAudioSegmentUrl().isBlank();
        if (hasText == hasAudio) {
            throw new BusinessException("Provide exactly one of transcriptText or audioSegmentUrl.");
        }

        String language = normalizeLanguage(request.getLanguage(), languageDefaults.currentDefaults().sttLanguage());
        String transcript = hasAudio
                ? sttService.transcribe(request.getAudioSegmentUrl(), language)
                : request.getTranscriptText().trim();
        if (transcript == null || transcript.isBlank()) {
            throw new BusinessException("No speech was recognized. Please retry with a clear recording.");
        }
        transcript = transcript.trim();
        if (transcript.length() > interviewConfig.getMaxTranscriptCharacters()) {
            throw new BusinessException("Transcript exceeds the maximum supported length.");
        }

        ExamSession session = attempt.getAssignment().getSession();
        int maxFollowups = session.getMaxFollowupPerQuestion();
        int maxMain = session.getMaxMainQuestions();
        long followupsUsed = allTurns.stream()
                .filter(t -> "FOLLOW_UP".equalsIgnoreCase(t.getTurnType()) && t.getMainQuestion() != null
                        && turn.getMainQuestion() != null
                        && t.getMainQuestion().getQuestionId().equals(turn.getMainQuestion().getQuestionId()))
                .count();

        List<RubricCriteria> criteria = turn.getMainQuestion() == null
                ? List.of()
                : rubricCriteriaRepository.findByQuestion_QuestionId(turn.getMainQuestion().getQuestionId());
        OffsetDateTime now = OffsetDateTime.now();
        long remainingSeconds = remainingSeconds(attempt, turn, now);
        List<AnswerAnalysisContext.PreviousTurn> previousTurns = allTurns.stream()
                .filter(previous -> previous.getTurnOrder() < turn.getTurnOrder()
                        && previous.getMainQuestion() != null
                        && turn.getMainQuestion() != null
                        && previous.getMainQuestion().getQuestionId().equals(turn.getMainQuestion().getQuestionId()))
                .map(previous -> new AnswerAnalysisContext.PreviousTurn(
                        previous.getTurnType(), previous.getAiQuestionText(), previous.getTranscriptText()))
                .toList();
        List<AnswerAnalysisContext.RubricItem> rubric = criteria.stream()
                .map(item -> new AnswerAnalysisContext.RubricItem(
                        criteria.indexOf(item), item.getCriteriaName(), item.getExpectedKnowledgePoints(),
                        item.getWeightRatio() == null ? 0 : item.getWeightRatio().doubleValue()))
                .toList();

        AnswerAnalysisResult analysis = answerAnalysisService.analyzeAnswer(new AnswerAnalysisContext(
                language,
                turn.getMainQuestion() == null ? turn.getAiQuestionText() : turn.getMainQuestion().getQuestionText(),
                turn.getAiQuestionText(),
                transcript,
                previousTurns,
                rubric,
                Math.toIntExact(followupsUsed),
                maxFollowups,
                remainingSeconds));

        OffsetDateTime analyzedAt = OffsetDateTime.now();
        long remainingAfterAnalysis = remainingSeconds(attempt, turn, analyzedAt);
        turn.setTranscriptText(transcript);
        if (hasAudio) {
            turn.setAudioSegmentUrl(audioStorageService.store(attempt.getAttemptId(), request.getAudioSegmentUrl()).url());
        }
        turn.setAiReasoning(analysis.classification().name() + ": " + analysis.summary());
        interactionTurnRepository.save(turn);
        saveAcousticMetrics(turn, request.getAudioDurationSeconds());

        List<AiRubricAssessment> assessments = persistAssessments(turn, criteria, analysis);

        String candidateFollowUp = analysis.followUpQuestion();
        boolean shouldGenerateFollowUp = candidateFollowUp != null
                && followupsUsed < maxFollowups
                && remainingAfterAnalysis > 0
                && analysis.classification() != AnswerAnalysisResult.Classification.SUFFICIENT
                && !sameQuestion(candidateFollowUp, turn.getMainQuestion() == null
                        ? turn.getAiQuestionText() : turn.getMainQuestion().getQuestionText());
        InteractionTurn nextTurn = null;

        if (remainingAfterAnalysis <= 0) {
            completeAttempt(attempt, analyzedAt);
        } else if (shouldGenerateFollowUp) {
            nextTurn = saveTurn(attempt, turn.getMainQuestion(), allTurns.size() + 1,
                    "FOLLOW_UP", candidateFollowUp);
        } else {
            long mainQuestionsAsked = allTurns.stream()
                    .map(InteractionTurn::getMainQuestion)
                    .filter(Objects::nonNull)
                    .map(Question::getQuestionId)
                    .distinct()
                    .count();
            List<UUID> askedIds = allTurns.stream()
                    .map(InteractionTurn::getMainQuestion)
                    .filter(Objects::nonNull)
                    .map(Question::getQuestionId)
                    .toList();
            Set<UUID> previousQuestionIds = previousStudentQuestionIds(attempt.getAssignment());
            Optional<Question> nextQuestion = mainQuestionsAsked < maxMain
                    ? questionRepository.findByTopic_TopicIdAndApprovalStatusOrderByCreatedAtAsc(
                                    session.getTopic().getTopicId(), "APPROVED").stream()
                            .filter(question -> !askedIds.contains(question.getQuestionId()))
                            .filter(question -> !previousQuestionIds.contains(question.getQuestionId()))
                            .findFirst()
                    : Optional.empty();
            if (nextQuestion.isPresent()) {
                Question question = nextQuestion.get();
                nextTurn = saveTurn(attempt, question, allTurns.size() + 1, "MAIN", question.getQuestionText());
            } else {
                completeAttempt(attempt, analyzedAt);
            }
        }

        return toSubmitAnswerResponse(attempt, turn, allTurns, nextTurn, analysis, assessments);
    }

    @Override
    @Transactional
    public InterviewResponse completeInterview(UUID attemptId) {
        return completeInterview(attemptId, null);
    }

    @Override
    @Transactional
    public InterviewResponse completeInterview(UUID attemptId, String fullAudioDataUrl) {
        VivaAttempt attempt = vivaAttemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));
        assertOwner(attempt);

        if (!InterviewStatus.COMPLETED.name().equalsIgnoreCase(attempt.getStatus())) {
            if (!InterviewStatus.IN_PROGRESS.name().equalsIgnoreCase(attempt.getStatus())) {
                throw new BusinessException("Only an in-progress interview can be completed.");
            }
            completeAttempt(attempt, OffsetDateTime.now());
        }
        if (fullAudioDataUrl != null && !fullAudioDataUrl.isBlank()) {
            attempt.setFullAudioUrl(audioStorageService.store(attemptId, fullAudioDataUrl).url());
            vivaAttemptRepository.save(attempt);
        }

        List<InteractionTurn> turns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId);
        InteractionTurn lastTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);

        return toInterviewResponse(attempt, lastTurn, turns);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getQuestionSpeechAudio(UUID turnId) {
        return getQuestionSpeechAudio(turnId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getQuestionSpeechAudio(UUID turnId, String language) {
        InteractionTurn turn = interactionTurnRepository.findById(turnId)
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found: " + turnId));
        assertOwner(turn.getAttempt());
        String effectiveLanguage = normalizeLanguage(language, languageDefaults.currentDefaults().ttsLanguage());
        return ttsService.synthesize(turn.getAiQuestionText(), effectiveLanguage);
    }

    @Override
    @Transactional(readOnly = true)
    public AudioFile getInterviewAudio(UUID attemptId, String filename) {
        VivaAttempt attempt = vivaAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));
        assertOwner(attempt);
        String expectedSuffix = "/" + attemptId + "/audio/" + filename;
        boolean belongsToAttempt = attempt.getFullAudioUrl() != null
                && attempt.getFullAudioUrl().endsWith(expectedSuffix);
        if (!belongsToAttempt) {
            belongsToAttempt = interactionTurnRepository
                    .findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId).stream()
                    .map(InteractionTurn::getAudioSegmentUrl)
                    .filter(Objects::nonNull)
                    .anyMatch(url -> url.endsWith(expectedSuffix));
        }
        if (!belongsToAttempt) {
            throw new ResourceNotFoundException("Interview audio not found.");
        }
        return new AudioFile(audioStorageService.read(filename),
                audioStorageService.mediaType(filename).toString());
    }

    private void saveAcousticMetrics(InteractionTurn turn, Integer audioDurationSeconds) {
        String[] words = turn.getTranscriptText().trim().split("\\s+");
        long hesitationCount = java.util.Arrays.stream(turn.getTranscriptText().toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(Set.of("uh", "um", "erm", "ờ", "ừm", "ừ", "à")::contains)
                .count();
        BigDecimal wordsPerMinute = audioDurationSeconds == null || audioDurationSeconds <= 0
                ? null
                : BigDecimal.valueOf(words.length * 60.0 / audioDurationSeconds).setScale(2, RoundingMode.HALF_UP);
        acousticFluencyMetricRepository.save(AcousticFluencyMetric.builder()
                .turn(turn)
                .wordsPerMinute(wordsPerMinute)
                .hesitationPausesCount(Math.toIntExact(hesitationCount))
                .build());
    }

    private String normalizeLanguage(String language, Language defaultLanguage) {
        String normalized = language == null || language.isBlank()
                ? defaultLanguage.name().toLowerCase(Locale.ROOT) : language.trim().toLowerCase(Locale.ROOT);
        if (!"vi".equals(normalized) && !"en".equals(normalized)) {
            throw new BusinessException("Interview language must be 'vi' or 'en'.");
        }
        return normalized;
    }

    private List<AiRubricAssessment> persistAssessments(
            InteractionTurn turn, List<RubricCriteria> criteria, AnswerAnalysisResult analysis) {
        Map<UUID, AiRubricAssessment> existingByCriteria = new HashMap<>();
        for (AiRubricAssessment existing : rubricAssessmentRepository.findByTurn_TurnId(turn.getTurnId())) {
            if (existing.getCriteria() != null) {
                existingByCriteria.putIfAbsent(existing.getCriteria().getCriteriaId(), existing);
            }
        }
        List<AiRubricAssessment> assessments = new ArrayList<>();
        for (int index = 0; index < criteria.size(); index++) {
            RubricCriteria item = criteria.get(index);
            int rubricIndex = index;
            AnswerAnalysisResult.CriterionResult result = analysis.criteria().stream()
                    .filter(candidate -> candidate.rubricIndex() == rubricIndex)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("AI assessment is missing a rubric criterion."));
            BigDecimal weight = item.getWeightRatio() == null ? BigDecimal.ONE : item.getWeightRatio();
            BigDecimal points = BigDecimal.TEN.multiply(weight)
                    .multiply(BigDecimal.valueOf(result.scoreFraction()))
                    .min(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);
            String missingConcepts = result.missingConcepts() == null || result.missingConcepts().isEmpty()
                    ? null : String.join("; ", result.missingConcepts());
            AiRubricAssessment assessment = existingByCriteria.get(item.getCriteriaId());
            if (assessment == null) {
                assessment = AiRubricAssessment.builder().turn(turn).criteria(item).build();
            }
            assessment.setSemanticSimilarity(null);
            assessment.setComplianceStatus(result.complianceStatus());
            assessment.setAiSuggestedPoints(points);
            assessment.setMissingConcepts(missingConcepts);
            assessment.setAiExplanationReasoning(result.reasoning());
            assessments.add(rubricAssessmentRepository.save(assessment));
        }
        return assessments;
    }

    private InteractionTurn saveTurn(
            VivaAttempt attempt, Question mainQuestion, int order, String type, String questionText) {
        if (questionText == null || questionText.isBlank() || questionText.length() > 4000) {
            throw new BusinessException("AI generated an invalid interview question.");
        }
        return interactionTurnRepository.save(InteractionTurn.builder()
                .attempt(attempt)
                .mainQuestion(mainQuestion)
                .turnOrder(order)
                .turnType(type)
                .aiQuestionText(questionText.trim())
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private SubmitAnswerResponse toSubmitAnswerResponse(
            VivaAttempt attempt, InteractionTurn turn, List<InteractionTurn> turns) {
        InteractionTurn nextTurn = turns.stream()
                .filter(candidate -> candidate.getTurnOrder() > turn.getTurnOrder())
                .findFirst()
                .orElse(null);
        return toSubmitAnswerResponse(attempt, turn, turns, nextTurn, null,
                rubricAssessmentRepository.findByTurn_TurnId(turn.getTurnId()));
    }

    private SubmitAnswerResponse toSubmitAnswerResponse(
            VivaAttempt attempt,
            InteractionTurn turn,
            List<InteractionTurn> turns,
            InteractionTurn nextTurn,
            AnswerAnalysisResult analysis,
            List<AiRubricAssessment> assessments) {
        String reasoning = turn.getAiReasoning() == null ? "" : turn.getAiReasoning();
        int separator = reasoning.indexOf(": ");
        String classification = analysis == null
                ? (separator < 0 ? null : reasoning.substring(0, separator))
                : analysis.classification().name();
        String summary = analysis == null
                ? (separator < 0 ? reasoning : reasoning.substring(separator + 2))
                : analysis.summary();

        List<AiAssessmentResponse> assessmentResponses = assessments.stream()
                .map(item -> AiAssessmentResponse.builder()
                        .assessmentId(item.getAssessmentId())
                        .criteriaId(item.getCriteria() == null ? null : item.getCriteria().getCriteriaId())
                        .criteriaName(item.getCriteria() == null ? null : item.getCriteria().getCriteriaName())
                        .semanticSimilarity(item.getSemanticSimilarity())
                        .complianceStatus(item.getComplianceStatus())
                        .aiSuggestedPoints(item.getAiSuggestedPoints())
                        .missingConcepts(item.getMissingConcepts())
                        .aiExplanationReasoning(item.getAiExplanationReasoning())
                        .build())
                .toList();
        return SubmitAnswerResponse.builder()
                .turnId(turn.getTurnId())
                .transcriptText(turn.getTranscriptText())
                .completed(InterviewStatus.COMPLETED.name().equalsIgnoreCase(attempt.getStatus()))
                .followUpGenerated(nextTurn != null && "FOLLOW_UP".equalsIgnoreCase(nextTurn.getTurnType()))
                .nextTurn(nextTurn == null ? null : toTurnResponse(nextTurn))
                .answerClassification(classification)
                .analysisSummary(summary)
                .assessments(assessmentResponses)
                .build();
    }

    private boolean expireAttemptIfNeeded(VivaAttempt attempt, InteractionTurn currentTurn) {
        if (!InterviewStatus.IN_PROGRESS.name().equalsIgnoreCase(attempt.getStatus())
                || remainingSeconds(attempt, currentTurn, OffsetDateTime.now()) > 0) {
            return false;
        }
        completeAttempt(attempt, OffsetDateTime.now());
        return true;
    }

    private long remainingSeconds(VivaAttempt attempt, InteractionTurn turn, OffsetDateTime now) {
        if (attempt.getStartTime() == null || attempt.getAssignment() == null
                || attempt.getAssignment().getSession() == null) {
            return 0;
        }
        ExamSession session = attempt.getAssignment().getSession();
        if (session.getTimeLimitMinutes() == null || session.getTimeLimitMinutes() <= 0) {
            return 0;
        }
        OffsetDateTime deadline = attempt.getStartTime().plusMinutes(session.getTimeLimitMinutes());
        if (turn != null) {
            if (turn.getCreatedAt() == null || interviewConfig.getAnswerTimeLimitSeconds() <= 0) {
                return 0;
            }
            OffsetDateTime answerDeadline = turn.getCreatedAt()
                    .plusSeconds(interviewConfig.getAnswerTimeLimitSeconds());
            if (answerDeadline.isBefore(deadline)) {
                deadline = answerDeadline;
            }
        }
        return Math.max(0, Duration.between(now, deadline).toSeconds());
    }

    private void completeAttempt(VivaAttempt attempt, OffsetDateTime completedAt) {
        attempt.setStatus(InterviewStatus.COMPLETED.name());
        if (attempt.getEndTime() == null) {
            attempt.setEndTime(completedAt);
        }
        vivaAttemptRepository.save(attempt);
        if (attempt.getAssignment() != null
                && !"COMPLETED".equalsIgnoreCase(attempt.getAssignment().getStatus())) {
            attempt.getAssignment().setStatus("COMPLETED");
            assignmentRepository.save(attempt.getAssignment());
        }
    }

    private Set<UUID> previousStudentQuestionIds(StudentSessionAssignment assignment) {
        return assignmentRepository.findFirstByExamSessionSessionIdAndScheduledTimeLessThanOrderByScheduledTimeDesc(
                        assignment.getExamSession().getSessionId(), assignment.getScheduledTime())
                .flatMap(previous -> vivaAttemptRepository.findByAssignment_AssignmentId(previous.getAssignmentId()))
                .map(previousAttempt -> interactionTurnRepository
                        .findByAttempt_AttemptIdOrderByTurnOrderAsc(previousAttempt.getAttemptId()).stream()
                        .map(InteractionTurn::getMainQuestion)
                        .filter(Objects::nonNull)
                        .map(Question::getQuestionId)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    private boolean sameQuestion(String left, String right) {
        return normalizeQuestion(left).equals(normalizeQuestion(right));
    }

    private String normalizeQuestion(String question) {
        return question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private AppUser currentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required for interview access.");
        }
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated student account was not found."));
        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getRole() == null
                || !"ST".equalsIgnoreCase(user.getRole().getRoleCode())) {
            throw new AccessDeniedException("Only an active student can access an interview.");
        }
        return user;
    }

    private void assertOwner(VivaAttempt attempt) {
        AppUser student = currentStudent();
        UUID assignedStudentId = attempt.getAssignment() == null || attempt.getAssignment().getStudent() == null
                ? null : attempt.getAssignment().getStudent().getUserId();
        if (!student.getUserId().equals(assignedStudentId)) {
            throw new AccessDeniedException("You do not have access to this interview.");
        }
    }

    private InterviewResponse toInterviewResponse(VivaAttempt attempt, InteractionTurn currentTurn, List<InteractionTurn> turns) {
        StudentSessionAssignment assignment = attempt.getAssignment();
        UUID sessionId = assignment != null && assignment.getSession() != null
                ? assignment.getSession().getSessionId() : null;
        UUID studentId = assignment != null && assignment.getStudent() != null
                ? assignment.getStudent().getUserId() : null;

        List<InteractionTurnResponse> turnResponses = turns != null
                ? turns.stream().map(this::toTurnResponse).toList()
                : List.of();

        return InterviewResponse.builder()
                .attemptId(attempt.getAttemptId())
                .assignmentId(assignment != null ? assignment.getAssignmentId() : null)
                .sessionId(sessionId)
                .studentId(studentId)
                .status(attempt.getStatus())
                .startTime(attempt.getStartTime())
                .endTime(attempt.getEndTime())
                .currentTurn(currentTurn != null ? toTurnResponse(currentTurn) : null)
                .turns(turnResponses)
                .build();
    }

    private InteractionTurnResponse toTurnResponse(InteractionTurn turn) {
        return InteractionTurnResponse.builder()
                .turnId(turn.getTurnId())
                .attemptId(turn.getAttempt() != null ? turn.getAttempt().getAttemptId() : null)
                .mainQuestionId(turn.getMainQuestion() != null ? turn.getMainQuestion().getQuestionId() : null)
                .turnOrder(turn.getTurnOrder())
                .turnType(turn.getTurnType())
                .aiQuestionText(turn.getAiQuestionText())
                .transcriptText(turn.getTranscriptText())
                .audioSegmentUrl(turn.getAudioSegmentUrl())
                .aiReasoning(turn.getAiReasoning())
                .createdAt(turn.getCreatedAt())
                .build();
    }
}
