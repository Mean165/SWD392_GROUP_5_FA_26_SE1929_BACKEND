package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.dto.request.ExamRequest;
import com.swd392.aiviva.exam.dto.response.ExamResponse;
import java.util.List;

public interface ExamService {

    List<ExamResponse> getExams();

    ExamResponse createExam(ExamRequest request);
}

