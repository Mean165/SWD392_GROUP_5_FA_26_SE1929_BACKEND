package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.dto.request.ExamRequest;
import com.swd392.aiviva.exam.dto.response.ExamResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExamServiceImpl implements ExamService {

    @Override
    public List<ExamResponse> getExams() {
        return List.of();
    }

    @Override
    public ExamResponse createExam(ExamRequest request) {
        // TODO: Implement exam creation workflow.
        return null;
    }
}

