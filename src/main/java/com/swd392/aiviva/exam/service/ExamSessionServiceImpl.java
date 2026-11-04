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
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
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
    private final ExamSessionBloomDistributionRepository distributionRepository;
    private final VivaAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final RubricResolver rubricResolver;
    private final FinalGradeReportRepository gradeRepository;
    private final int defaultMaxOverlapPercent;

    public ExamSessionServiceImpl(ExamSessionRepository examSessionRepository,
                                  StudentSessionAssignmentRepository assignmentRepository,
                                  UserRepository userRepository,
                                  SubjectTopicRepository topicRepository,
                                  ExamSessionBloomDistributionRepository distributionRepository,
                                  VivaAttemptRepository attemptRepository,
                                  QuestionRepository questionRepository,
                                  RubricResolver rubricResolver,
                                  FinalGradeReportRepository gradeRepository,
                                  @Value("${exam.default-max-overlap-percent:40}") int defaultMaxOverlapPercent) {
        this.examSessionRepository = examSessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.distributionRepository = distributionRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.rubricResolver = rubricResolver;
        this.gradeRepository = gradeRepository;
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
        boolean hasStartedAttempt = assignmentRepository.findByExamSessionSessionId(id).stream().map(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).orElse(null))
                .filter(java.util.Objects::nonNull).anyMatch(a -> List.of("IN_PROGRESS", "COMPLETED").contains(a.getStatus().toUpperCase(Locale.ROOT)));
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
        return assignmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSessionAssignmentResponse> getAssignmentsBySessionId(UUID sessionId, String status, int page, int size) { ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); if(page<0||size<1||size>100) throw new BusinessException("page must be >= 0 and size must be between 1 and 100"); List<StudentSessionAssignment> all=assignmentRepository.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(sessionId); if(status!=null&&!status.isBlank()){String normalized=status.trim().toUpperCase(Locale.ROOT); if(!List.of("PENDING","SCHEDULED","IN_PROGRESS","COMPLETED","ABSENT","CANCELLED").contains(normalized)) throw new BusinessException("Unsupported assignment status"); all=all.stream().filter(a->normalized.equals(a.getStatus())).toList();} int start=Math.min(page*size,all.size()),end=Math.min(start+size,all.size()); List<StudentSessionAssignmentResponse> content=all.subList(start,end).stream().map(this::assignmentResponse).toList(); return PageResponse.<StudentSessionAssignmentResponse>builder().content(content).page(page).size(size).totalElements(all.size()).totalPages((int)Math.ceil((double)all.size()/size)).build(); }

    @Override
    @Transactional
    public ExamSessionResponse startSession(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); readinessOrThrow(s); ExamStateMachine.requireSessionTransition(s.getStatus(), "IN_PROGRESS"); s.setStatus("IN_PROGRESS"); s.setUpdatedAt(OffsetDateTime.now()); return response(examSessionRepository.save(s)); }

    @Override
    @Transactional
    public ExamSessionResponse cancelSession(UUID id, CancelExamSessionRequest request) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); boolean active = assignmentRepository.findByExamSessionSessionId(id).stream().map(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).orElse(null)).filter(java.util.Objects::nonNull).anyMatch(a -> "IN_PROGRESS".equalsIgnoreCase(a.getStatus())); if (active) throw new ConflictException("Cannot cancel a session with an IN_PROGRESS attempt"); ExamStateMachine.requireSessionTransition(s.getStatus(), "CANCELLED"); s.setStatus("CANCELLED"); s.setDescription((s.getDescription() == null ? "" : s.getDescription() + "\n") + "Cancelled: " + request.getReason()); assignmentRepository.findByExamSessionSessionId(id).forEach(a -> { if (List.of("PENDING","SCHEDULED").contains(a.getStatus())) { a.setStatus("CANCELLED"); a.setCancelledReason(request.getReason()); assignmentRepository.save(a); } }); return response(examSessionRepository.save(s)); }

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
        return Map.of("added", added, "skipped", skipped);
    }

    @Override
    @Transactional
    public Map<String, Object> autoSchedule(UUID id, ScheduleExamRequest request) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); OffsetDateTime start = request.getStartTime() != null ? request.getStartTime() : s.getStartTime(); if (start == null) start = OffsetDateTime.now(); int slot = request.getSlotMinutes() == null ? s.getTimeLimitMinutes() + s.getBufferMinutes() : request.getSlotMinutes(); List<StudentSessionAssignment> pending = assignmentRepository.findByExamSessionSessionIdAndStatusIn(id, List.of("PENDING","SCHEDULED")); if (Boolean.TRUE.equals(request.getShuffle())) java.util.Collections.shuffle(pending, new java.util.Random(request.getSeed() == null ? id.getMostSignificantBits() : request.getSeed())); List<Map<String,Object>> result = new ArrayList<>(); int order = 1; OffsetDateTime cursor = start; for (StudentSessionAssignment a : pending) { result.add(Map.of("assignmentId", a.getAssignmentId(), "studentId", a.getStudent().getUserId(), "slotOrder", order, "scheduledTime", cursor)); if (!Boolean.TRUE.equals(request.getDryRun())) { a.setSlotOrder(order); a.setScheduledTime(cursor); a.setStatus("SCHEDULED"); assignmentRepository.save(a); } order++; cursor = cursor.plusMinutes(slot); } return Map.of("dryRun", Boolean.TRUE.equals(request.getDryRun()), "items", result); }

    @Override
    @Transactional
    public void deleteSession(UUID id) { ExamSession s = examSessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s); if (assignmentRepository.findByExamSessionSessionId(id).stream().anyMatch(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).isPresent())) throw new ConflictException("Session has attempts; use cancel instead"); examSessionRepository.delete(s); }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse updateAssignmentSchedule(UUID sessionId, UUID studentId, UpdateAssignmentScheduleRequest request) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        StudentSessionAssignment a = assignmentRepository.findByExamSessionSessionIdAndStudentUserId(sessionId, studentId).orElseThrow(() -> new ResourceNotFoundException("Student assignment not found"));
        if (List.of("IN_PROGRESS", "COMPLETED", "ABSENT", "CANCELLED").contains(a.getStatus())) throw new ConflictException("Assignment can no longer be rescheduled");
        ensureNoScheduleConflict(studentId, request.getScheduledTime(), s.getTimeLimitMinutes());
        ensureNoSessionOverlap(sessionId, request.getScheduledTime(), s.getTimeLimitMinutes(), a.getAssignmentId());
        a.setScheduledTime(request.getScheduledTime()); a.setStatus("SCHEDULED");
        return assignmentResponse(assignmentRepository.save(a));
    }

    @Override
    @Transactional
    public void removeStudent(UUID sessionId, UUID studentId) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        StudentSessionAssignment a = assignmentRepository.findByExamSessionSessionIdAndStudentUserId(sessionId, studentId).orElseThrow(() -> new ResourceNotFoundException("Student assignment not found"));
        if (List.of("IN_PROGRESS", "COMPLETED").contains(a.getStatus())) throw new ConflictException("Student cannot be removed after the assignment has started");
        assignmentRepository.delete(a);
    }

    @Override
    @Transactional
    public StudentImportResult importStudents(UUID sessionId, org.springframework.web.multipart.MultipartFile file) {
        ExamSession s = examSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Exam session not found")); authorizeOwner(s);
        if (file == null || file.isEmpty()) throw new BusinessException("Import file is empty");
        List<Map<String,Object>> added = new ArrayList<>(), skipped = new ArrayList<>();
        try {
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            List<String> identifiers = new ArrayList<>();
            if (filename.endsWith(".csv")) {
                try (var reader = new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8); var csv = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                    for (var row : csv) { String value = row.isMapped("studentCode") ? row.get("studentCode") : row.isMapped("email") ? row.get("email") : row.get(0); if (value != null && !value.isBlank()) identifiers.add(value.trim()); }
                }
            } else if (filename.endsWith(".xlsx")) {
                try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) { var sheet = workbook.getSheetAt(0); for (int i=1;i<=sheet.getLastRowNum();i++) { var cell=sheet.getRow(i)==null?null:sheet.getRow(i).getCell(0); if(cell!=null&&!cell.toString().isBlank()) identifiers.add(cell.toString().trim()); } }
            } else throw new BusinessException("Only CSV and XLSX imports are supported");
            int row = 2; for (String identifier : identifiers) { AppUser st = userRepository.findByStudentOrStaffCode(identifier).orElseGet(() -> userRepository.findByEmail(identifier).orElse(null)); if(st==null){skipped.add(Map.of("identifier",identifier,"row",row,"reason","NOT_FOUND")); row++; continue;} if(st.getRole()==null||!"ST".equalsIgnoreCase(st.getRole().getRoleCode())||Boolean.FALSE.equals(st.getIsActive())){skipped.add(Map.of("identifier",identifier,"row",row,"reason","NOT_ACTIVE_STUDENT"));row++;continue;} if(assignmentRepository.existsByExamSessionSessionIdAndStudentUserId(sessionId,st.getUserId())){skipped.add(Map.of("identifier",identifier,"row",row,"reason","ALREADY_ASSIGNED"));row++;continue;} StudentSessionAssignment a=assignmentRepository.save(StudentSessionAssignment.builder().examSession(s).student(st).status("PENDING").build()); added.add(Map.of("identifier",identifier,"assignmentId",a.getAssignmentId())); row++; }
            return new StudentImportResult(added, skipped);
        } catch (java.io.IOException ex) { throw new BusinessException("Failed to parse student import: " + ex.getMessage(), ex); }
    }

    private void readinessOrThrow(ExamSession s) {
        ExamReadinessResponse response = readiness(s);
        if (!response.isReady()) {
            throw new ConflictException(String.join("; ", response.getBlockingErrors()), "SESSION_NOT_READY");
        }
    }

    private ExamReadinessResponse readiness(ExamSession session) {
        List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId())
                .stream().filter(a -> !"CANCELLED".equalsIgnoreCase(a.getStatus())).toList();
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

        long unscheduled = assignments.stream().filter(a -> a.getScheduledTime() == null).count();
        int scheduleOverlaps = countScheduleOverlaps(assignments, session.getTimeLimitMinutes());
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
    private void authorizeOwner(ExamSession s) { AppUser u=authenticatedUser(); authorizeLecturerOrAdmin(u); if(u.getRole()!=null&&"LE".equalsIgnoreCase(u.getRole().getRoleCode())&&s.getCreatedBy()!=null&&!u.getUserId().equals(s.getCreatedBy().getUserId())) throw new AccessDeniedException("Lecturers can only manage sessions they created"); }
    private void ensureNoScheduleConflict(UUID studentId, OffsetDateTime time, int duration) { for(StudentSessionAssignment a:assignmentRepository.findByStudentUserId(studentId)) if(a.getScheduledTime()!=null&&!List.of("CANCELLED","ABSENT").contains(a.getStatus())&&time.isBefore(a.getScheduledTime().plusMinutes(duration))&&a.getScheduledTime().isBefore(time.plusMinutes(duration))) throw new ConflictException("Sinh viên đã có lịch thi khác chồng giờ", "SCHEDULE_CONFLICT"); }
    private void ensureNoSessionOverlap(UUID sessionId, OffsetDateTime time, int duration, UUID excluded) { for (StudentSessionAssignment other : assignmentRepository.findByExamSessionSessionId(sessionId)) if ((excluded == null || !excluded.equals(other.getAssignmentId())) && other.getScheduledTime() != null && !List.of("CANCELLED","ABSENT").contains(other.getStatus()) && time.isBefore(other.getScheduledTime().plusMinutes(duration)) && other.getScheduledTime().isBefore(time.plusMinutes(duration))) throw new ConflictException("Khung giờ bị chồng với assignment khác trong phiên", "SCHEDULE_CONFLICT"); }
    private ExamSessionResponse response(ExamSession session) {
        Map<String, Integer> bloomCounts = distribution(session).entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId());
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
    private StudentSessionAssignmentResponse assignmentResponse(StudentSessionAssignment a) { VivaAttempt attempt=attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).orElse(null); var grade=attempt==null?null:gradeRepository.findByAttempt_AttemptId(attempt.getAttemptId()).orElse(null); return StudentSessionAssignmentResponse.builder().assignmentId(a.getAssignmentId()).sessionId(a.getExamSession().getSessionId()).sessionName(a.getExamSession().getSessionName()).studentId(a.getStudent().getUserId()).studentCode(a.getStudent().getStudentOrStaffCode()).studentName(a.getStudent().getFullName()).studentEmail(a.getStudent().getEmail()).scheduledTime(a.getScheduledTime()).status(a.getStatus()).slotOrder(a.getSlotOrder()).cancelledReason(a.getCancelledReason()).attemptId(attempt==null?null:attempt.getAttemptId()).attemptStatus(attempt==null?null:attempt.getStatus()).totalAiScore(grade==null?null:grade.getTotalAiScore()).finalTeacherScore(grade==null?null:grade.getFinalTeacherScore()).build(); }
}
