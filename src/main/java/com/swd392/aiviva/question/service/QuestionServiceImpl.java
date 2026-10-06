package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.mapper.QuestionMapper;
import com.swd392.aiviva.question.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public QuestionServiceImpl(QuestionRepository questionRepository, QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @Override
    public List<QuestionResponse> getQuestions() {
        return questionRepository.findAll().stream()
                .map(questionMapper::toResponse)
                .toList();
    }

    @Override
    public QuestionResponse getQuestionById(UUID id) {
        return questionRepository.findById(id)
                .map(questionMapper::toResponse)
                .orElse(null);
    }

    @Override
    public QuestionResponse generateQuestion(QuestionGenerationRequest request) {
        // TODO: Integrate with AI generation service.
        return null;
    }

    @Override
    public void deleteQuestion(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        questionRepository.delete(question);
    }
}
