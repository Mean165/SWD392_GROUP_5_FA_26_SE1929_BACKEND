package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.CreateQuestionRequest;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import java.util.List;
import java.util.UUID;

public interface QuestionService {

    List<QuestionResponse> getQuestions();

    List<QuestionResponse> getQuestionsByTopicId(UUID topicId);

    QuestionResponse getQuestionById(UUID id);

    QuestionResponse createQuestion(CreateQuestionRequest request);

    QuestionResponse generateQuestion(QuestionGenerationRequest request);

    void deleteQuestion(UUID id);

    List<QuestionResponse> importQuestions(UUID topicId, org.springframework.web.multipart.MultipartFile file);
}
