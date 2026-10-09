package com.swd392.aiviva.question.service;

import com.swd392.aiviva.question.dto.request.CreateSubjectTopicRequest;
import com.swd392.aiviva.question.dto.response.SubjectTopicResponse;
import java.util.UUID;

public interface SubjectTopicService {
    SubjectTopicResponse createTopic(CreateSubjectTopicRequest request, String userEmail);
    SubjectTopicResponse getTopicById(UUID topicId);
}
