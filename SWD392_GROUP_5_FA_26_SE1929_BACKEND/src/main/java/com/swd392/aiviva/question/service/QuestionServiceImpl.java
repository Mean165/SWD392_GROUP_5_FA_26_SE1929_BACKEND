package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.QuestionCreateRequest;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.request.QuestionUpdateRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;

    @Override
    public List<QuestionResponse> getQuestions(UUID topicId, String bloomLevel) {
        // TODO: Implement listing of question bank with filtering.
        return List.of();
    }

    @Override
    public QuestionResponse getQuestionById(UUID id) {
        // TODO: Implement find by id.
        return null;
    }

    @Override
    public QuestionResponse generateQuestion(QuestionGenerationRequest request) {
        // TODO: Integrate with AI generation service.
        return null;
    }

    @Override
    public QuestionResponse createQuestion(QuestionCreateRequest request) {
        Question question = Question.builder()
                .topicId(request.getTopicId())
                .createdBy(request.getCreatedBy())
                .questionText(request.getQuestionText())
                .bloomLevel(request.getBloomLevel())
                .sourceType(request.getSourceType())
                .approvalStatus(request.getApprovalStatus())
                .build();

        Question savedQuestion = questionRepository.save(question);

        return mapToResponse(savedQuestion);
    }

    @Override
    public QuestionResponse updateQuestion(UUID id, QuestionUpdateRequest request) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        question.setQuestionText(request.getQuestionText());
        question.setBloomLevel(request.getBloomLevel());

        Question updatedQuestion = questionRepository.save(question);
        return mapToResponse(updatedQuestion);
    }

    @Override
    public void deleteQuestion(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        questionRepository.delete(question);
    }

    private QuestionResponse mapToResponse(Question question) {
        return QuestionResponse.builder()
                .questionId(question.getQuestionId())
                .topicId(question.getTopicId())
                .createdBy(question.getCreatedBy())
                .questionText(question.getQuestionText())
                .bloomLevel(question.getBloomLevel())
                .sourceType(question.getSourceType())
                .approvalStatus(question.getApprovalStatus())
                .createdAt(question.getCreatedAt())
                .build();
    }
}
