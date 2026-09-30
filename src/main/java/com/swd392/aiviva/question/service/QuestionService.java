package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import java.util.List;

public interface QuestionService {

    List<QuestionResponse> getQuestions();

    QuestionResponse getQuestionById(Long id);

    QuestionResponse generateQuestion(QuestionGenerationRequest request);
}

