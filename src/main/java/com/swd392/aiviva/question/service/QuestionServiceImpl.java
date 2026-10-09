package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.question.dto.request.CreateQuestionRequest;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.enums.QuestionSource;
import com.swd392.aiviva.question.enums.QuestionStatus;
import com.swd392.aiviva.question.mapper.QuestionMapper;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final SubjectTopicRepository subjectTopicRepository;
    private final UserRepository userRepository;
    private final QuestionMapper questionMapper;

    public QuestionServiceImpl(QuestionRepository questionRepository,
                               SubjectTopicRepository subjectTopicRepository,
                               UserRepository userRepository,
                               QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.subjectTopicRepository = subjectTopicRepository;
        this.userRepository = userRepository;
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
    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest request) {
        SubjectTopic topic = subjectTopicRepository.findById(request.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject topic not found with id: " + request.getTopicId()));

        AppUser creator = null;
        if (request.getCreatedBy() != null) {
            creator = userRepository.findById(request.getCreatedBy())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getCreatedBy()));
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                String email = authentication.getName();
                creator = userRepository.findByEmail(email).orElse(null);
            }
        }

        if (creator == null) {
            throw new BusinessException("Created by user is required (provide createdBy or authenticate)");
        }

        String bloomLevel = BloomLevel.REMEMBER.name();
        if (request.getBloomLevel() != null && !request.getBloomLevel().isBlank()) {
            try {
                bloomLevel = BloomLevel.valueOf(request.getBloomLevel().trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid bloom level: " + request.getBloomLevel() + ". Valid values: REMEMBER, UNDERSTAND, APPLY, ANALYZE");
            }
        }

        String sourceType = QuestionSource.MANUAL.name();
        if (request.getSourceType() != null && !request.getSourceType().isBlank()) {
            try {
                sourceType = QuestionSource.valueOf(request.getSourceType().trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid source type: " + request.getSourceType() + ". Valid values: MANUAL, IMPORTED, AI_GENERATED");
            }
        }

        String approvalStatus = QuestionStatus.APPROVED.name();
        if (request.getApprovalStatus() != null && !request.getApprovalStatus().isBlank()) {
            try {
                approvalStatus = QuestionStatus.valueOf(request.getApprovalStatus().trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid approval status: " + request.getApprovalStatus() + ". Valid values: DRAFT, PENDING_REVIEW, APPROVED, REJECTED, ARCHIVED");
            }
        }

        Question question = Question.builder()
                .topic(topic)
                .createdBy(creator)
                .questionText(request.getQuestionText().trim())
                .bloomLevel(bloomLevel)
                .sourceType(sourceType)
                .approvalStatus(approvalStatus)
                .createdAt(OffsetDateTime.now())
                .build();

        Question saved = questionRepository.save(question);
        return questionMapper.toResponse(saved);
    }

    @Override
    public QuestionResponse generateQuestion(QuestionGenerationRequest request) {
        // TODO: Integrate with AI generation service.
        return null;
    }

    @Override
    public void deleteQuestion(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));
        questionRepository.delete(question);
    }
}
