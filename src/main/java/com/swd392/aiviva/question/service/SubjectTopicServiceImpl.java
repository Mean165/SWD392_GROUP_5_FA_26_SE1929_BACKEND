package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.question.dto.request.CreateSubjectTopicRequest;
import com.swd392.aiviva.question.dto.response.SubjectTopicResponse;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.mapper.SubjectTopicMapper;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectTopicServiceImpl implements SubjectTopicService {

    private final SubjectTopicRepository topicRepository;
    private final UserRepository userRepository;
    private final SubjectTopicMapper topicMapper;

    public SubjectTopicServiceImpl(SubjectTopicRepository topicRepository,
                                   UserRepository userRepository,
                                   SubjectTopicMapper topicMapper) {
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.topicMapper = topicMapper;
    }

    @Override
    @Transactional
    public SubjectTopicResponse createTopic(CreateSubjectTopicRequest request, String userEmail) {
        AppUser user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("User not found: " + userEmail));

        if (topicRepository.findByTopicCode(request.getTopicCode()).isPresent()) {
            throw new BusinessException("Topic code already exists: " + request.getTopicCode());
        }

        SubjectTopic topic = SubjectTopic.builder()
                .createdBy(user)
                .topicCode(request.getTopicCode())
                .topicName(request.getTopicName())
                .description(request.getDescription())
                .createdAt(OffsetDateTime.now())
                .build();

        SubjectTopic savedTopic = topicRepository.save(topic);
        return topicMapper.toResponse(savedTopic);
    }

    @Override
    @Transactional(readOnly = true)
    public SubjectTopicResponse getTopicById(UUID topicId) {
        SubjectTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException("Topic not found"));
        return topicMapper.toResponse(topic);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<SubjectTopicResponse> getAllTopics() {
        return topicRepository.findAll().stream()
                .map(topicMapper::toResponse)
                .toList();
    }
}
