package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuestionServiceImpl implements QuestionService {

    @Override
    public List<QuestionResponse> getQuestions() {
        // TODO: Implement listing of question bank.
        return List.of();
    }

    @Override
    public QuestionResponse getQuestionById(Long id) {
        // TODO: Implement find by id.
        return null;
    }

    @Override
    public QuestionResponse generateQuestion(QuestionGenerationRequest request) {
        // TODO: Integrate with AI generation service.
        return null;
    }
}

