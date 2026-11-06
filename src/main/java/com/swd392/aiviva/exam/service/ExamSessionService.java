package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.dto.request.AssignStudentSessionRequest;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import java.util.List;
import java.util.UUID;
import com.swd392.aiviva.exam.dto.request.BulkAssignStudentsRequest;
import com.swd392.aiviva.exam.dto.request.CancelExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.ScheduleExamRequest;
import com.swd392.aiviva.exam.dto.response.ExamReadinessResponse;
import com.swd392.aiviva.exam.dto.request.UpdateAssignmentScheduleRequest;
import java.util.Map;
import com.swd392.aiviva.common.response.PageResponse;

public interface ExamSessionService {

    ExamSessionResponse createExamSession(CreateExamSessionRequest request);

    ExamSessionResponse updateExamSession(UUID id, UpdateExamSessionRequest request);

    List<ExamSessionResponse> getAllExamSessions();

    ExamSessionResponse getExamSessionById(UUID id);

    StudentSessionAssignmentResponse assignStudentToSession(AssignStudentSessionRequest request);

    StudentSessionAssignmentResponse assignStudentToSession(UUID sessionId, AssignStudentSessionRequest request);

    PageResponse<StudentSessionAssignmentResponse> getAssignmentsBySessionId(UUID sessionId, String status, int page, int size);
    ExamSessionResponse startSession(UUID id);
    ExamSessionResponse cancelSession(UUID id, CancelExamSessionRequest request);
    ExamReadinessResponse readiness(UUID id);
    Map<String, Object> bulkAssign(UUID id, BulkAssignStudentsRequest request);
    Map<String, Object> autoSchedule(UUID id, ScheduleExamRequest request);
    void deleteSession(UUID id);
    PageResponse<ExamSessionResponse> search(String status, UUID topicId, java.time.OffsetDateTime from,
                                             java.time.OffsetDateTime to, String keyword, int page, int size);
    StudentSessionAssignmentResponse updateAssignmentSchedule(UUID sessionId, UUID studentId, UpdateAssignmentScheduleRequest request);
    void removeStudent(UUID sessionId, UUID studentId);
    StudentImportResult importStudents(UUID sessionId, org.springframework.web.multipart.MultipartFile file);
}
