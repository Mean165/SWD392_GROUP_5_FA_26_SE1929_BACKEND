package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import java.util.List;
import java.util.UUID;

public interface ExamSessionService {

    ExamSessionResponse createExamSession(CreateExamSessionRequest request);

    ExamSessionResponse updateExamSession(UUID id, UpdateExamSessionRequest request);

    List<ExamSessionResponse> getAllExamSessions();

    ExamSessionResponse getExamSessionById(UUID id);
}
