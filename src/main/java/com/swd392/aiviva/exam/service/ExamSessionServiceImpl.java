package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.exam.dto.request.AssignStudentSessionRequest;
import com.swd392.aiviva.exam.dto.request.BulkAssignStudentsRequest;
import com.swd392.aiviva.exam.dto.request.CancelExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.ScheduleExamRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateAssignmentScheduleRequest;
import com.swd392.aiviva.exam.dto.response.ExamReadinessResponse;
import com.swd392.aiviva.exam.dto.response.ExamReadinessIssue;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.ExamSessionBloomDistribution;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionBloomDistributionRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.attempt.service.AttemptRosterService;
import com.swd392.aiviva.exam.attempt.service.ExamAttemptService;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.question.repository.SubjectRepository;
import com.swd392.aiviva.question.service.RubricResolver;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamSessionServiceImpl implements ExamSessionService {
    private final ExamSessionRepository examSessionRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final SubjectTopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final ExamSessionBloomDistributionRepository distributionRepository;
    private final VivaAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final RubricResolver rubricResolver;
    private final FinalGradeReportRepository gradeRepository;
    private final AttemptRosterService attemptRosterService;
    private final ExamAttemptService examAttemptService;
    private final int defaultMaxOverlapPercent;

    public ExamSessionServiceImpl(ExamSessionRepository examSessionRepository,
                                  StudentSessionAssignmentRepository assignmentRepository,
                                  UserRepository userRepository,
                                  SubjectTopicRepository topicRepository,
                                  SubjectRepository subjectRepository,
                                  ExamSessionBloomDistributionRepository distributionRepository,
                                  VivaAttemptRepository attemptRepository,
                                  QuestionRepository questionRepository,
                                  RubricResolver rubricResolver,
                                  FinalGradeReportRepository gradeRepository,
                                  AttemptRosterService attemptRosterService,
                                  ExamAttemptService examAttemptService,
                                  @Value("${exam.default-max-overlap-percent:40}") int defaultMaxOverlapPercent) {
        this.examSessionRepository = examSessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.distributionRepository = distributionRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.rubricResolver = rubricResolver;
        this.gradeRepository = gradeRepository;
        this.attemptRosterService = attemptRosterService;
        this.examAttemptService = examAttemptService;
        this.defaultMaxOverlapPercent = defaultMaxOverlapPercent;
    }

    @Override
    @Transactional
    public ExamSessionResponse createExamSession(CreateExamSessionRequest request) {
        AppUser current = authenticatedUser();
        authorizeLecturerOrAdmin(current);
        SubjectTopic topic = topicRepository.findById(request.getTopicId()).orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + request.getTopicId()));
        validateStartTime(request.getStartTime());
        int buffer = request.getBufferMinutes() == null ? 5 : request.getBufferMinutes();
        int avoid = request.getAvoidRepeatWindow() == null ? 2 : request.getAvoidRepeatWindow();
        int maxOverlapPercent = request.getMaxOverlapPercent() == null ? defaultMaxOverlapPercent : request.getMaxOverlapPercent();
        validateOverlapPercent(maxOverlapPercent);
        if (request.getTimeLimitMinutes() > 180) throw new BusinessException("time_limit_minutes must be between 1 and 180");
        Map<BloomLevel, Integer> distribution = normalizeDistribution(request.getBloomDistribution(), request.getMaxMainQuestions());
        validateBank(topic, distribution);
        ExamSession session = ExamSession.builder().createdBy(resolveCreator(current, request.getCreatedBy())).topic(topic)
                .sessionName(request.getSessionName().trim()).maxMainQuestions(request.getMaxMainQuestions())
                .maxFollowupPerQuestion(request.getMaxFollowupPerQuestion()).timeLimitMinutes(request.getTimeLimitMinutes())
                .status("SCHEDULED").startTime(request.getStartTime()).createdAt(OffsetDateTime.now())
                .bufferMinutes(buffer).avoidRepeatWindow(avoid).maxOverlapPercent(maxOverlapPercent)
                .description(request.getDescription()).updatedAt(OffsetDateTime.now()).build();
        ExamSession saved = examSessionRepository.save(session);
        saveDistribution(saved, distribution);
        return response(saved);
    }

    @Override
    @Transactional
    public ExamSessionResponse updateExamSession(UUID id, UpdateExamSessionRequest request) {
        ExamSession session = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + id));
        authorizeOwner(session);
        if (!"SCHEDULED".equalsIgnoreCase(session.getStatus())) throw new ConflictException("Only SCHEDULED sessions can be edited", "SESSION_EDIT_LOCKED");
        boolean hasStartedAttempt = assignmentRepository.findByExamSessionSessionId(id).stream()
                .flatMap(a -> attemptRepository.findAllByAssignment_AssignmentIdOrderByStartTimeAsc(a.getAssignmentId()).stream())
                .anyMatch(a -> List.of("IN_PROGRESS", "COMPLETED").contains(a.getStatus().toUpperCase(Locale.ROOT)));
        if (hasStartedAttempt && (request.getMaxMainQuestions() != null || request.getBloomDistribution() != null || request.getTopicId() != null)) throw new ConflictException("Session question configuration is locked after an attempt has started", "SESSION_EDIT_LOCKED");
        if (request.getTopicId() != null && !request.getTopicId().equals(session.getTopic().getTopicId())) throw new ConflictException("topic_id cannot be changed after session creation", "SESSION_TOPIC_IMMUTABLE");
        if (request.getSessionName() != null && !request.getSessionName().isBlank()) session.setSessionName(request.getSessionName().trim());
        if (request.getMaxMainQuestions() != null) session.setMaxMainQuestions(request.getMaxMainQuestions());
        if (request.getMaxFollowupPerQuestion() != null) session.setMaxFollowupPerQuestion(request.getMaxFollowupPerQuestion());
        if (request.getTimeLimitMinutes() != null) { if (request.getTimeLimitMinutes() > 180) throw new BusinessException("time_limit_minutes must be between 1 and 180"); session.setTimeLimitMinutes(request.getTimeLimitMinutes()); }
        if (request.getStartTime() != null) { validateStartTime(request.getStartTime()); session.setStartTime(request.getStartTime()); }
        if (request.getBufferMinutes() != null) session.setBufferMinutes(request.getBufferMinutes());
        if (request.getAvoidRepeatWindow() != null) session.setAvoidRepeatWindow(request.getAvoidRepeatWindow());
        if (request.getMaxOverlapPercent() != null) {
            validateOverlapPercent(request.getMaxOverlapPercent());
            session.setMaxOverlapPercent(request.getMaxOverlapPercent());
        }
        if (request.getDescription() != null) session.setDescription(request.getDescription());
        if (request.getBloomDistribution() != null) { Map<BloomLevel,Integer> d = normalizeDistribution(request.getBloomDistribution(), session.getMaxMainQuestions()); validateBank(session.getTopic(), d); saveDistribution(session, d); }
        session.setUpdatedAt(OffsetDateTime.now());
        return response(examSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamSessionResponse> getAllExamSessions() { return search(null, null, null, null, null, 0, 100).getContent(); }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamSessionResponse> search(String status, UUID topicId, OffsetDateTime from, OffsetDateTime to, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException("page must be >= 0 and size must be between 1 and 100");
        AppUser viewer = authenticatedUser();
        List<ExamSession> all = examSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(s -> viewer.getRole() != null && "AD".equalsIgnoreCase(viewer.getRole().getRoleCode())
                        || s.getCreatedBy() != null && viewer.getUserId().equals(s.getCreatedBy().getUserId())).toList();
        List<ExamSession> filtered = all.stream().filter(s -> status == null || status.isBlank() || status.equalsIgnoreCase(s.getStatus()))
                .filter(s -> topicId == null || (s.getTopic() != null && topicId.equals(s.getTopic().getTopicId())))
                .filter(s -> from == null || (s.getStartTime() != null && !s.getStartTime().isBefore(from)))
                .filter(s -> to == null || (s.getStartTime() != null && !s.getStartTime().isAfter(to)))
                .filter(s -> keyword == null || keyword.isBlank() || s.getSessionName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))).toList();
        int start = Math.min(page * size, filtered.size()); int end = Math.min(start + size, filtered.size());
        List<ExamSessionResponse> content = filtered.subList(start, end).stream().map(this::response).toList();
        return PageResponse.<ExamSessionResponse>builder().content(content).page(page).size(size).totalElements(filtered.size()).totalPages((int) Math.ceil((double) filtered.size() / size)).build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSessionResponse getExamSessionById(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + id)); authorizeOwner(s); return response(s); }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse assignStudentToSession(AssignStudentSessionRequest request) { if (request.getSessionId() == null) throw new BusinessException("Session ID is required"); return assignStudentToSession(request.getSessionId(), request); }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse assignStudentToSession(UUID sessionId, AssignStudentSessionRequest request) {
        ExamSession session = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + sessionId)); authorizeOwner(session);
        AppUser student = userRepository.findByStudentOrStaffCode(request.getStudentCode().trim()).orElseThrow(() -> new ResourceNotFoundException("Student not found with code: " + request.getStudentCode()));
        if (student.getRole() == null || !"ST".equalsIgnoreCase(student.getRole().getRoleCode()) || Boolean.FALSE.equals(student.getIsActive())) throw new ConflictException("Student is inactive or does not have role ST");
        if (assignmentRepository.existsByExamSessionSessionIdAndStudentUserId(sessionId, student.getUserId())) throw new ConflictException("Student is already assigned to this session");
        OffsetDateTime time = request.getScheduledTime(); String status = time == null ? "PENDING" : "SCHEDULED";
        if (time != null) { ensureNoScheduleConflict(student.getUserId(), time, session.getTimeLimitMinutes()); ensureNoSessionOverlap(session.getSessionId(), time, session.getTimeLimitMinutes(), null); }
        StudentSessionAssignment saved = assignmentRepository.save(StudentSessionAssignment.builder().examSession(session).student(student).scheduledTime(time).status(status).build());
        return attemptRosterService.map(sessionId, List.of(saved)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSessionAssignmentResponse> getAssignmentsBySessionId(UUID sessionId, String status, int page, int size) { ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); if(page<0||size<1||size>100) throw new BusinessException("page must be >= 0 and size must be between 1 and 100"); List<StudentSessionAssignment> all=assignmentRepository.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(sessionId).stream().filter(a->a.getRemovedAt()==null).toList(); if(status!=null&&!status.isBlank()){String normalized=status.trim().toUpperCase(Locale.ROOT); if(!List.of("PENDING","SCHEDULED","IN_PROGRESS","COMPLETED","ABSENT","CANCELLED").contains(normalized)) throw new BusinessException("Unsupported assignment status"); all=all.stream().filter(a->normalized.equals(a.getStatus())).toList();} int start=Math.min(page*size,all.size()),end=Math.min(start+size,all.size()); List<StudentSessionAssignmentResponse> content=attemptRosterService.map(sessionId,all.subList(start,end)); return PageResponse.<StudentSessionAssignmentResponse>builder().content(content).page(page).size(size).totalElements(all.size()).totalPages((int)Math.ceil((double)all.size()/size)).build(); }

    @Override
    @Transactional
    public ExamSessionResponse startSession(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); readinessOrThrow(s); ExamStateMachine.requireSessionTransition(s.getStatus(), "IN_PROGRESS"); s.setStatus("IN_PROGRESS"); s.setUpdatedAt(OffsetDateTime.now()); return response(examSessionRepository.save(s)); }

    @Override
    @Transactional
    public ExamSessionResponse cancelSession(UUID id, CancelExamSessionRequest request) {
        ExamSession session = examSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        authorizeOwner(session);
        List<StudentSessionAssignment> sessionAssignments = assignmentRepository.findByExamSessionSessionId(id);
        boolean active = sessionAssignments.stream()
                .flatMap(row -> attemptRepository.findAllByAssignment_AssignmentIdOrderByStartTimeAsc(row.getAssignmentId()).stream())
                .anyMatch(row -> "IN_PROGRESS".equalsIgnoreCase(row.getStatus()));
        if (active) throw new ConflictException("Cannot cancel a session with an IN_PROGRESS attempt");
        ExamStateMachine.requireSessionTransition(session.getStatus(), "CANCELLED");
        for (StudentSessionAssignment assignment : sessionAssignments) {
            for (var attempt : examAttemptService.rowsForEnrollment(id, assignment.getStudent().getUserId())) {
                if ("SCHEDULED".equals(attempt.getStatus()) && !attempt.isCounted()) {
                    examAttemptService.cancel(attempt.getAttemptId(), request.getReason());
                }
            }
            if (List.of("PENDING", "SCHEDULED").contains(assignment.getStatus())) {
                assignment.setStatus("CANCELLED");
                assignment.setCancelledReason(request.getReason());
                assignmentRepository.save(assignment);
            }
        }
        session.setStatus("CANCELLED");
        session.setDescription((session.getDescription() == null ? "" : session.getDescription() + "\n")
                + "Cancelled: " + request.getReason());
        return response(examSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public ExamReadinessResponse readiness(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); return readiness(s); }

    @Override
    @Transactional
    public Map<String, Object> bulkAssign(UUID id, BulkAssignStudentsRequest request) {
        ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        List<Map<String,Object>> added = new ArrayList<>(), skipped = new ArrayList<>();
        List<UUID> ids = request.getStudentIds() == null ? List.of() : request.getStudentIds();
        List<String> codes = request.getStudentCodes() == null ? List.of() : request.getStudentCodes();
        if (ids.isEmpty() && codes.isEmpty()) throw new BusinessException("Provide studentIds or studentCodes");
        for (UUID studentId : ids) addStudent(s, studentId.toString(), userRepository.findById(studentId).orElse(null), added, skipped);
        for (String code : codes) { String identifier = code == null ? "" : code.trim(); AppUser student = identifier.contains("@") ? userRepository.findByEmail(identifier).orElse(null) : userRepository.findByStudentOrStaffCode(identifier).orElse(null); addStudent(s, identifier, student, added, skipped); }
        if (request.getExtraAttemptsByIdentifier() != null) {
            for (int index = 0; index < added.size(); index++) {
                Map<String, Object> row = added.get(index);
                String identifier = String.valueOf(row.get("identifier"));
                Integer extra = request.getExtraAttemptsByIdentifier().getOrDefault(identifier,
                        row.get("studentId") == null ? 0 : request.getExtraAttemptsByIdentifier().getOrDefault(String.valueOf(row.get("studentId")), 0));
                if (extra == null || extra == 0) continue;
                if (extra < 0 || !examAttemptService.extraAttemptsAllowed(id)) {
                    skipped.add(Map.of("identifier", identifier, "reason", extra < 0 ? "INVALID_EXTRA_ATTEMPTS" : "EXTRA_ATTEMPTS_DISABLED"));
                    continue;
                }
                examAttemptService.grant(id, UUID.fromString(String.valueOf(row.get("studentId"))), extra, "import");
                Map<String, Object> updated = new java.util.LinkedHashMap<>(row);
                updated.put("extraAttempts", extra);
                added.set(index, updated);
            }
        }
        return Map.of("added", added, "skipped", skipped);
    }

    @Override
    @Transactional
    public Map<String, Object> autoSchedule(UUID id, ScheduleExamRequest request) {
        ExamSession session = examSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        authorizeOwner(session);
        return examAttemptService.autoSchedule(id, request);
    }

    @Override
    @Transactional
    public void deleteSession(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); if (assignmentRepository.findByExamSessionSessionId(id).stream().anyMatch(a -> !attemptRepository.findAllByAssignment_AssignmentIdOrderByStartTimeAsc(a.getAssignmentId()).isEmpty())) throw new ConflictException("Session has attempts; use cancel instead"); examSessionRepository.delete(s); }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse updateAssignmentSchedule(UUID sessionId, UUID studentId, UpdateAssignmentScheduleRequest request) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        StudentSessionAssignment a = assignmentRepository.findByExamSessionSessionIdAndStudentUserId(sessionId, studentId).orElseThrow(() -> new ResourceNotFoundException("Student assignment not found"));
        int attemptNo = request.getAttemptNo() == null ? 1 : request.getAttemptNo();
        examAttemptService.scheduleEnrollmentAttempt(sessionId, studentId, attemptNo, request.getScheduledTime());
        if (attemptNo == 1) { a.setScheduledTime(request.getScheduledTime()); a.setStatus("SCHEDULED"); assignmentRepository.save(a); }
        return attemptRosterService.map(sessionId, List.of(a)).getFirst();
    }

    @Override
    @Transactional
    public void removeStudent(UUID sessionId, UUID studentId) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        StudentSessionAssignment a = assignmentRepository.findByExamSessionSessionIdAndStudentUserId(sessionId, studentId).orElseThrow(() -> new ResourceNotFoundException("Student assignment not found"));
        List<com.swd392.aiviva.exam.attempt.entity.ExamAttempt> rows = examAttemptService.rowsForEnrollment(sessionId, studentId);
        if (rows.stream().anyMatch(row -> "IN_PROGRESS".equals(row.getStatus()))) {
            throw new ConflictException("Student cannot be removed while an attempt is in progress", "ATTEMPT_IN_PROGRESS");
        }
        boolean hasCompleted = rows.stream().anyMatch(row -> "COMPLETED".equals(row.getStatus()));
        rows.stream().filter(row -> "SCHEDULED".equals(row.getStatus()))
                .forEach(row -> examAttemptService.cancel(row.getAttemptId(), "Student removed from exam"));
        a.setRemovedAt(OffsetDateTime.now());
        a.setStatus(hasCompleted ? "COMPLETED" : "CANCELLED");
        assignmentRepository.save(a);
        examAttemptService.auditEnrollmentRemoved(sessionId, studentId, "Student removed from exam roster");
    }

    @Override
    @Transactional
    public StudentImportResult importStudents(UUID sessionId, org.springframework.web.multipart.MultipartFile file) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        if (file == null || file.isEmpty()) throw new BusinessException("Import file is empty");
        List<Map<String,Object>> added = new ArrayList<>(), skipped = new ArrayList<>();
        try {
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            List<ImportStudentRow> rows = new ArrayList<>();
            if (filename.endsWith(".csv")) {
                try (var reader = new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
                     var csv = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                    int rowNumber = 2;
                    for (var csvRow : csv) {
                        String identifier = csvRow.isMapped("studentCode") ? csvRow.get("studentCode")
                                : csvRow.isMapped("email") ? csvRow.get("email") : csvRow.get(0);
                        if (identifier == null || identifier.isBlank()) { rowNumber++; continue; }
                        String extraValue = csvRow.isMapped("extra_attempts") ? csvRow.get("extra_attempts")
                                : csvRow.isMapped("extraAttempts") ? csvRow.get("extraAttempts") : "";
                        try { rows.add(new ImportStudentRow(rowNumber, identifier.trim(), parseExtraAttempts(extraValue), null)); }
                        catch (RuntimeException exception) { rows.add(new ImportStudentRow(rowNumber, identifier.trim(), 0, "INVALID_EXTRA_ATTEMPTS")); }
                        rowNumber++;
                    }
                }
            } else if (filename.endsWith(".xlsx")) {
                try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) {
                    var sheet = workbook.getSheetAt(0);
                    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                        var dataRow = sheet.getRow(i);
                        var identifierCell = dataRow == null ? null : dataRow.getCell(0);
                        if (identifierCell == null || identifierCell.toString().isBlank()) continue;
                        var extraCell = dataRow.getCell(1);
                        String extraValue = extraCell == null ? "" : extraCell.toString().trim();
                        try { rows.add(new ImportStudentRow(i + 1, identifierCell.toString().trim(), parseExtraAttempts(extraValue), null)); }
                        catch (RuntimeException exception) { rows.add(new ImportStudentRow(i + 1, identifierCell.toString().trim(), 0, "INVALID_EXTRA_ATTEMPTS")); }
                    }
                }
            } else throw new BusinessException("Only CSV and XLSX imports are supported");
            for (ImportStudentRow row : rows) {
                if (row.error() != null) { skipped.add(Map.of("identifier", row.identifier(), "row", row.row(), "reason", row.error())); continue; }
                AppUser student = userRepository.findByStudentOrStaffCode(row.identifier())
                        .orElseGet(() -> userRepository.findByEmail(row.identifier()).orElse(null));
                if (student == null) { skipped.add(Map.of("identifier", row.identifier(), "row", row.row(), "reason", "NOT_FOUND")); continue; }
                if (student.getRole() == null || !"ST".equalsIgnoreCase(student.getRole().getRoleCode()) || Boolean.FALSE.equals(student.getIsActive())) {
                    skipped.add(Map.of("identifier", row.identifier(), "row", row.row(), "reason", "NOT_ACTIVE_STUDENT")); continue;
                }
                if (assignmentRepository.existsByExamSessionSessionIdAndStudentUserId(sessionId, student.getUserId())) {
                    skipped.add(Map.of("identifier", row.identifier(), "row", row.row(), "reason", "ALREADY_ASSIGNED")); continue;
                }
                StudentSessionAssignment enrollment = assignmentRepository.save(StudentSessionAssignment.builder()
                        .examSession(s).student(student).status("PENDING").build());
                if (row.extraAttempts() > 0) examAttemptService.grant(sessionId, student.getUserId(), row.extraAttempts(), "import");
                added.add(Map.of("identifier", row.identifier(), "row", row.row(), "assignmentId", enrollment.getAssignmentId(),
                        "extraAttempts", row.extraAttempts()));
            }
            return new StudentImportResult(added, skipped);
        } catch (java.io.IOException ex) { throw new BusinessException("Failed to parse student import: " + ex.getMessage(), ex); }
    }

    private int parseExtraAttempts(String value) {
        if (value == null || value.isBlank()) return 0;
        int parsed = new java.math.BigDecimal(value.trim()).intValueExact();
        if (parsed < 0) throw new NumberFormatException("extra_attempts must be nonnegative");
        return parsed;
    }

    private record ImportStudentRow(int row, String identifier, int extraAttempts, String error) { }

    private void readinessOrThrow(ExamSession s) {
        ExamReadinessResponse response = readiness(s);
        if (!response.isReady()) {
            throw new ConflictException(String.join("; ", response.getBlockingErrors()), "SESSION_NOT_READY");
        }
    }

    private ExamReadinessResponse readiness(ExamSession session) {
        List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId())
                .stream().filter(a -> a.getRemovedAt() == null && !"CANCELLED".equalsIgnoreCase(a.getStatus())).toList();
        Map<BloomLevel, Integer> required = distribution(session);
        Map<String, Long> availableByBloom = new java.util.LinkedHashMap<>();
        Map<String, Long> missingByBloom = new java.util.LinkedHashMap<>();
        Map<BloomLevel, List<Question>> pools = new EnumMap<>(BloomLevel.class);
        List<ExamReadinessIssue> issues = new ArrayList<>();
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        UUID subjectId = session.getTopic() == null || session.getTopic().getSubject() == null
                ? null : session.getTopic().getSubject().getSubjectId();
        if (subjectId == null || rubricResolver.activeRubric(subjectId).isEmpty()) {
            issues.add(ExamReadinessIssue.builder().code("SUBJECT_RUBRIC_MISSING").build());
            blocking.add("Subject is missing an active rubric.");
        } else if (!rubricResolver.hasValidRubric(subjectId)) {
            issues.add(ExamReadinessIssue.builder().code("SUBJECT_RUBRIC_INVALID").build());
            blocking.add("Subject rubric criteria or weights are invalid.");
        }

        List<Question> approved = subjectId == null ? List.of()
                : questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(subjectId, "APPROVED");
        for (BloomLevel level : BloomLevel.values()) {
            List<Question> levelPool = approved.stream().filter(q -> level.name().equalsIgnoreCase(q.getBloomLevel())).toList();
            pools.put(level, levelPool);
            long have = levelPool.size();
            long need = required.getOrDefault(level, 0);
            availableByBloom.put(level.name(), have);
            missingByBloom.put(level.name(), Math.max(0, need - have));
            if (have < need) {
                issues.add(ExamReadinessIssue.builder().code("INSUFFICIENT_QUESTIONS").bloomLevel(level.name())
                        .have(have).need(need).build());
                blocking.add("Thiếu " + (need - have) + " câu đã duyệt ở mức " + level.name()
                        + " (hiện có " + have + ", cần " + need + ").");
            }
        }

        List<com.swd392.aiviva.exam.attempt.entity.ExamAttempt> scheduledAttempts = examAttemptService.scheduledAttempts(session.getSessionId());
        long unscheduled = scheduledAttempts.stream().filter(a -> a.getScheduledTime() == null).count();
        int scheduleOverlaps = countAttemptScheduleOverlaps(scheduledAttempts, session.getTimeLimitMinutes());
        if (scheduleOverlaps > 0) {
            issues.add(ExamReadinessIssue.builder().code("SCHEDULE_OVERLAP").have((long) scheduleOverlaps).build());
            blocking.add("Có " + scheduleOverlaps + " cặp lịch bị trùng.");
        }
        if (unscheduled > 0) warnings.add("Có " + unscheduled + " sinh viên chưa có giờ thi.");

        boolean poolHasShortage = issues.stream().anyMatch(i -> "INSUFFICIENT_QUESTIONS".equals(i.getCode()));
        if (!assignments.isEmpty() && !poolHasShortage && subjectId != null) {
            int mainQuestionCount = required.values().stream().mapToInt(Integer::intValue).sum();
            int overlapLimit = (int) ((long) mainQuestionCount * configuredOverlapPercent(session) / 100);
            QuestionAllocationChecker.Result allocation = new QuestionAllocationChecker().check(
                    assignments.size(), required, pools, overlapLimit);
            if (!allocation.possible()) {
                Map<String, Integer> additions = new java.util.LinkedHashMap<>();
                allocation.additionalQuestionsByBloom().forEach((level, count) -> additions.put(level.name(), count));
                issues.add(ExamReadinessIssue.builder().code("OVERLAP_EXCEEDS_LIMIT")
                        .maxPercent(configuredOverlapPercent(session))
                        .minimumAdditionalQuestions(allocation.minimumAdditionalQuestions())
                        .additionalQuestionsByBloom(additions.isEmpty() ? null : additions)
                        .minimumIsUpperBound(allocation.estimateIsUpperBound()).build());
                blocking.add("Không thể cấp phát đề trong giới hạn trùng " + configuredOverlapPercent(session) + "%.");
            }
        }

        boolean ready = issues.isEmpty();
        return ExamReadinessResponse.builder().ready(ready).canStart(ready).studentCount(assignments.size())
                .unscheduledCount((int) unscheduled).overlappingCount(scheduleOverlaps)
                .approvedQuestionsByBloom(availableByBloom).missingQuestionsByBloom(missingByBloom)
                .warnings(warnings).blockingErrors(blocking).issues(issues).build();
    }

    private int countScheduleOverlaps(List<StudentSessionAssignment> assignments, Integer duration) {
        int overlaps = 0;
        for (int i = 0; i < assignments.size(); i++) {
            OffsetDateTime first = assignments.get(i).getScheduledTime();
            if (first == null) continue;
            for (int j = i + 1; j < assignments.size(); j++) {
                OffsetDateTime second = assignments.get(j).getScheduledTime();
                if (second != null && first.isBefore(second.plusMinutes(duration))
                        && second.isBefore(first.plusMinutes(duration))) overlaps++;
            }
        }
        return overlaps;
    }

    private int countAttemptScheduleOverlaps(List<com.swd392.aiviva.exam.attempt.entity.ExamAttempt> attempts,
            Integer duration) {
        int overlaps = 0;
        for (int i = 0; i < attempts.size(); i++) {
            var first = attempts.get(i).getScheduledTime();
            if (first == null) continue;
            for (int j = i + 1; j < attempts.size(); j++) {
                var second = attempts.get(j).getScheduledTime();
                if (second != null && !attempts.get(i).getStudent().getUserId().equals(attempts.get(j).getStudent().getUserId())
                        && first.isBefore(second.plusMinutes(duration)) && second.isBefore(first.plusMinutes(duration))) overlaps++;
            }
        }
        return overlaps;
    }

    private int configuredOverlapPercent(ExamSession session) {
        return session.getMaxOverlapPercent() == null ? defaultMaxOverlapPercent : session.getMaxOverlapPercent();
    }
    private Map<BloomLevel,Integer> distribution(ExamSession s) { List<ExamSessionBloomDistribution> rows = distributionRepository.findBySession_SessionId(s.getSessionId()); Map<BloomLevel,Integer> d = new EnumMap<>(BloomLevel.class); if (!rows.isEmpty()) rows.forEach(r -> d.put(r.getBloomLevel(), r.getQuestionCount())); else { BloomLevel[] levels = s.getMaxMainQuestions() == 4 ? new BloomLevel[]{BloomLevel.UNDERSTAND,BloomLevel.APPLY,BloomLevel.ANALYZE,BloomLevel.EVALUATE} : Arrays.copyOf(BloomLevel.values(), Math.min(s.getMaxMainQuestions(), 6)); for (int i=0;i<s.getMaxMainQuestions();i++) d.merge(levels[i%levels.length],1,Integer::sum); } return d; }
    private Map<BloomLevel,Integer> normalizeDistribution(Map<String,Integer> raw, int total) { Map<BloomLevel,Integer> d = new EnumMap<>(BloomLevel.class); if (raw == null) { BloomLevel[] levels = total == 4 ? new BloomLevel[]{BloomLevel.UNDERSTAND,BloomLevel.APPLY,BloomLevel.ANALYZE,BloomLevel.EVALUATE} : Arrays.copyOf(BloomLevel.values(), Math.min(total, 6)); for (int i=0;i<total;i++) d.merge(levels[i%levels.length],1,Integer::sum); return d; } for (var e: raw.entrySet()) { BloomLevel l; try { l = BloomLevel.parse(e.getKey()); } catch (IllegalArgumentException ex) { throw new BusinessException(ex.getMessage()); } if (e.getValue()==null || e.getValue()<1) throw new BusinessException("Bloom question_count must be > 0"); d.merge(l,e.getValue(),Integer::sum); } if (d.values().stream().mapToInt(Integer::intValue).sum()!=total) throw new BusinessException("Bloom distribution must total max_main_questions"); return d; }
    private void validateBank(SubjectTopic topic, Map<BloomLevel, Integer> distribution) {
        if (topic.getSubject() == null) throw new ConflictException("The session topic is not assigned to a subject.", "SESSION_SUBJECT_MISSING");
        List<Question> approved = questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(
                topic.getSubject().getSubjectId(), "APPROVED");
        for (var entry : distribution.entrySet()) {
            long count = approved.stream().filter(q -> entry.getKey().name().equalsIgnoreCase(q.getBloomLevel())).count();
            if (count < entry.getValue()) {
                throw new ConflictException(entry.getKey().name() + " needs " + entry.getValue()
                        + " approved questions but has " + count + ".", "SESSION_BANK_INSUFFICIENT");
            }
        }
    }

    private void validateOverlapPercent(int percent) {
        if (percent < 0 || percent > 100) throw new BusinessException("max_overlap_percent must be between 0 and 100.");
    }
    private void saveDistribution(ExamSession s, Map<BloomLevel,Integer> d) { distributionRepository.deleteAll(distributionRepository.findBySession_SessionId(s.getSessionId())); d.forEach((level,count)->distributionRepository.save(ExamSessionBloomDistribution.builder().session(s).bloomLevel(level).questionCount(count).build())); }
    private void validateStartTime(OffsetDateTime start) { if (start != null && start.isBefore(OffsetDateTime.now().minusMinutes(5))) throw new BusinessException("start_time cannot be more than 5 minutes in the past"); }
    private AppUser resolveCreator(AppUser current, UUID requested) { if (requested == null || current.getRole()==null || !"AD".equalsIgnoreCase(current.getRole().getRoleCode())) return current; return userRepository.findById(requested).orElseThrow(() -> new ResourceNotFoundException("Creator not found")); }
    private AppUser authenticatedUser() { Authentication a=SecurityContextHolder.getContext().getAuthentication(); if(a==null||!a.isAuthenticated()||"anonymousUser".equals(a.getPrincipal())) throw new AccessDeniedException("Authentication is required"); return userRepository.findByEmail(a.getName()).orElseThrow(()->new AccessDeniedException("Authenticated user not found")); }
    private void authorizeLecturerOrAdmin(AppUser u) { String r=u.getRole()==null?"":u.getRole().getRoleCode(); if(!List.of("AD","LE").contains(r.toUpperCase(Locale.ROOT))) throw new AccessDeniedException("Only AD or LE can manage exam sessions"); }
    private void authorizeOwner(ExamSession session) {
        AppUser user = authenticatedUser();
        authorizeLecturerOrAdmin(user);
        if (user.getRole() == null || !"LE".equalsIgnoreCase(user.getRole().getRoleCode())) return;
        if (session.getCreatedBy() != null && user.getUserId().equals(session.getCreatedBy().getUserId())) return;
        UUID subjectId = session.getTopic() == null || session.getTopic().getSubject() == null
                ? null : session.getTopic().getSubject().getSubjectId();
        if (subjectId == null || !subjectRepository.isLecturerAssigned(subjectId, user.getUserId())) {
            throw new AccessDeniedException("Lecturer is not assigned to this exam's subject");
        }
    }
    private void ensureNoScheduleConflict(UUID studentId, OffsetDateTime time, int duration) { for(StudentSessionAssignment a:assignmentRepository.findByStudentUserId(studentId)) if(a.getScheduledTime()!=null&&!List.of("CANCELLED","ABSENT").contains(a.getStatus())&&time.isBefore(a.getScheduledTime().plusMinutes(duration))&&a.getScheduledTime().isBefore(time.plusMinutes(duration))) throw new ConflictException("Sinh viên đã có lịch thi khác chồng giờ", "SCHEDULE_CONFLICT"); }
    private void ensureNoSessionOverlap(UUID sessionId, OffsetDateTime time, int duration, UUID excluded) { for (StudentSessionAssignment other : assignmentRepository.findByExamSessionSessionId(sessionId)) if ((excluded == null || !excluded.equals(other.getAssignmentId())) && other.getScheduledTime() != null && !List.of("CANCELLED","ABSENT").contains(other.getStatus()) && time.isBefore(other.getScheduledTime().plusMinutes(duration)) && other.getScheduledTime().isBefore(time.plusMinutes(duration))) throw new ConflictException("Khung giờ bị chồng với assignment khác trong phiên", "SCHEDULE_CONFLICT"); }
    private ExamSessionResponse response(ExamSession session) {
        Map<String, Integer> bloomCounts = distribution(session).entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId()).stream()
                .filter(assignment -> assignment.getRemovedAt() == null).toList();
        Map<String, Object> statistics = Map.of("studentCount", assignments.size(),
                "completedCount", assignments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count(),
                "absentCount", assignments.stream().filter(a -> "ABSENT".equals(a.getStatus())).count());
        ExamReadinessResponse readiness = readiness(session);
        Map<String, Object> readinessSummary = Map.of("ready", readiness.isReady(), "canStart", readiness.isReady(),
                "issues", readiness.getIssues(), "blockingErrors", readiness.getBlockingErrors());
        return ExamSessionResponse.builder().sessionId(session.getSessionId())
                .createdById(session.getCreatedBy() == null ? null : session.getCreatedBy().getUserId())
                .createdByName(session.getCreatedBy() == null ? null : session.getCreatedBy().getFullName())
                .topicId(session.getTopic() == null ? null : session.getTopic().getTopicId())
                .topicName(session.getTopic() == null ? null : session.getTopic().getTopicName())
                .sessionName(session.getSessionName()).maxMainQuestions(session.getMaxMainQuestions())
                .maxFollowupPerQuestion(session.getMaxFollowupPerQuestion()).timeLimitMinutes(session.getTimeLimitMinutes())
                .status(session.getStatus()).startTime(session.getStartTime()).createdAt(session.getCreatedAt())
                .bufferMinutes(session.getBufferMinutes()).avoidRepeatWindow(session.getAvoidRepeatWindow())
                .maxOverlapPercent(configuredOverlapPercent(session)).description(session.getDescription())
                .bloomDistribution(bloomCounts).statistics(statistics).readiness(readinessSummary).build();
    }
    private void addStudent(ExamSession session, String identifier, AppUser student, List<Map<String,Object>> added, List<Map<String,Object>> skipped) { String reason=null; if(student==null) reason="NOT_FOUND"; else if(student.getRole()==null||!"ST".equalsIgnoreCase(student.getRole().getRoleCode())) reason="NOT_STUDENT"; else if(Boolean.FALSE.equals(student.getIsActive())) reason="INACTIVE"; else if(assignmentRepository.existsByExamSessionSessionIdAndStudentUserId(session.getSessionId(),student.getUserId())) reason="ALREADY_ASSIGNED"; if(reason!=null){skipped.add(Map.of("identifier",identifier,"reason",reason));return;} StudentSessionAssignment a=assignmentRepository.save(StudentSessionAssignment.builder().examSession(session).student(student).status("PENDING").build()); added.add(Map.of("identifier",identifier,"studentId",student.getUserId(),"assignmentId",a.getAssignmentId(),"status",a.getStatus())); }
    private StudentSessionAssignmentResponse assignmentResponse(StudentSessionAssignment a) { VivaAttempt attempt=attemptRepository.findAllByAssignment_AssignmentIdOrderByStartTimeAsc(a.getAssignmentId()).stream().findFirst().orElse(null); var grade=attempt==null?null:gradeRepository.findByAttempt_AttemptId(attempt.getAttemptId()).orElse(null); return StudentSessionAssignmentResponse.builder().assignmentId(a.getAssignmentId()).sessionId(a.getExamSession().getSessionId()).sessionName(a.getExamSession().getSessionName()).studentId(a.getStudent().getUserId()).studentCode(a.getStudent().getStudentOrStaffCode()).studentName(a.getStudent().getFullName()).studentEmail(a.getStudent().getEmail()).scheduledTime(a.getScheduledTime()).status(a.getStatus()).slotOrder(a.getSlotOrder()).cancelledReason(a.getCancelledReason()).attemptId(attempt==null?null:attempt.getAttemptId()).attemptStatus(attempt==null?null:attempt.getStatus()).totalAiScore(grade==null?null:grade.getTotalAiScore()).finalTeacherScore(grade==null?null:grade.getFinalTeacherScore()).build(); }
}
