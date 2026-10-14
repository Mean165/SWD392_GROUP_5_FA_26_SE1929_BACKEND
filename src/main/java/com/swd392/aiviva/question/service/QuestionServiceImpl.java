package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.question.ai.QuestionGenerationService;
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
    private final QuestionGenerationService questionGenerationService;

    public QuestionServiceImpl(QuestionRepository questionRepository,
                               SubjectTopicRepository subjectTopicRepository,
                               UserRepository userRepository,
                               QuestionMapper questionMapper,
                               QuestionGenerationService questionGenerationService) {
        this.questionRepository = questionRepository;
        this.subjectTopicRepository = subjectTopicRepository;
        this.userRepository = userRepository;
        this.questionMapper = questionMapper;
        this.questionGenerationService = questionGenerationService;
    }

    @Override
    public List<QuestionResponse> getQuestions() {
        return questionRepository.findAll().stream()
                .map(questionMapper::toResponse)
                .toList();
    }

    @Override
    public List<QuestionResponse> getQuestionsByTopicId(UUID topicId) {
        if (!subjectTopicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Subject topic not found with id: " + topicId);
        }
        return questionRepository.findByTopic_TopicId(topicId).stream()
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
        String generatedQuestion = questionGenerationService.generateQuestionText(request.getPrompt());

        SubjectTopic topic = null;
        if (request.getTopicCode() != null && !request.getTopicCode().isBlank()) {
            topic = subjectTopicRepository.findByTopicCode(request.getTopicCode()).orElse(null);
        }

        AppUser creator = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            creator = userRepository.findByEmail(authentication.getName()).orElse(null);
        }

        if (topic != null && creator != null) {
            Question question = Question.builder()
                    .topic(topic)
                    .createdBy(creator)
                    .questionText(generatedQuestion)
                    .bloomLevel(BloomLevel.ANALYZE.name())
                    .sourceType(QuestionSource.AI_GENERATED.name())
                    .approvalStatus(QuestionStatus.PENDING_REVIEW.name())
                    .createdAt(OffsetDateTime.now())
                    .build();

            Question saved = questionRepository.save(question);
            return questionMapper.toResponse(saved);
        }

        return QuestionResponse.builder()
                .questionText(generatedQuestion)
                .bloomLevel(BloomLevel.ANALYZE.name())
                .sourceType(QuestionSource.AI_GENERATED.name())
                .approvalStatus(QuestionStatus.DRAFT.name())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public void deleteQuestion(UUID id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));
        questionRepository.delete(question);
    }
    @Override
    @Transactional
    public List<QuestionResponse> importQuestions(UUID topicId, org.springframework.web.multipart.MultipartFile file) {
        SubjectTopic topic = subjectTopicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject topic not found with id: " + topicId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AppUser creator = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String email = authentication.getName();
            creator = userRepository.findByEmail(email).orElse(null);
        }

        if (creator == null) {
            throw new BusinessException("Created by user is required (authenticate first)");
        }

        List<FileImportHelper.QuestionImportDto> importedData = FileImportHelper.parseFile(file);
        
        List<Question> questionsToSave = new java.util.ArrayList<>();
        for (FileImportHelper.QuestionImportDto dto : importedData) {
            String bloomLevel = BloomLevel.REMEMBER.name();
            if (dto.bloomLevel != null && !dto.bloomLevel.isBlank()) {
                try {
                    bloomLevel = BloomLevel.valueOf(dto.bloomLevel.trim().toUpperCase()).name();
                } catch (IllegalArgumentException e) {
                    // Ignore and use default
                }
            }

            Question question = Question.builder()
                    .topic(topic)
                    .createdBy(creator)
                    .questionText(dto.questionText.trim())
                    .bloomLevel(bloomLevel)
                    .sourceType(QuestionSource.IMPORTED.name())
                    .approvalStatus(QuestionStatus.APPROVED.name())
                    .createdAt(OffsetDateTime.now())
                    .build();
                    
            questionsToSave.add(question);
        }

        List<Question> savedQuestions = questionRepository.saveAll(questionsToSave);
        
        return savedQuestions.stream()
                .map(questionMapper::toResponse)
                .toList();
    }
}
