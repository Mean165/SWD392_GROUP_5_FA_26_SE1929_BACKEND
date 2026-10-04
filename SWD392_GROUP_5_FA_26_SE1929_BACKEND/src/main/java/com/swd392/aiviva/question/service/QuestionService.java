package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.QuestionCreateRequest;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.request.QuestionUpdateRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import java.util.List;
import java.util.UUID;

public interface QuestionService {

    List<QuestionResponse> getQuestions(UUID topicId, String bloomLevel);

    QuestionResponse getQuestionById(UUID id);

    QuestionResponse generateQuestion(QuestionGenerationRequest request);

    QuestionResponse createQuestion(QuestionCreateRequest request);

    QuestionResponse updateQuestion(UUID id, QuestionUpdateRequest request);

    void deleteQuestion(UUID id);
}
