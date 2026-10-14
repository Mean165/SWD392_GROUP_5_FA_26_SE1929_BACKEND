package com.swd392.aiviva.question.mapper;

import com.swd392.aiviva.question.dto.response.SubjectTopicResponse;
import com.swd392.aiviva.question.entity.SubjectTopic;
import org.springframework.stereotype.Component;

@Component
public class SubjectTopicMapper {

    public SubjectTopicResponse toResponse(SubjectTopic topic) {
        if (topic == null) {
            return null;
        }
        return SubjectTopicResponse.builder()
                .topicId(topic.getTopicId())
                .createdBy(topic.getCreatedBy() != null ? topic.getCreatedBy().getUserId() : null)
                .topicCode(topic.getTopicCode())
                .topicName(topic.getTopicName())
                .description(topic.getDescription())
                .createdAt(topic.getCreatedAt())
                .build();
    }
}
