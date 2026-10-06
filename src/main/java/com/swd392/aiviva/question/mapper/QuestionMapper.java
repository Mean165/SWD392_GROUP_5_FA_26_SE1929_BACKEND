package com.swd392.aiviva.question.mapper;

import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.entity.Question;
import org.springframework.stereotype.Component;

@Component
public class QuestionMapper {

    public QuestionResponse toResponse(Question question) {
        if (question == null) {
            return null;
        }
        return QuestionResponse.builder()
                .questionId(question.getQuestionId())
                .topicId(question.getTopic() != null ? question.getTopic().getTopicId() : null)
                .createdBy(question.getCreatedBy() != null ? question.getCreatedBy().getUserId() : null)
                .questionText(question.getQuestionText())
                .bloomLevel(question.getBloomLevel())
                .sourceType(question.getSourceType())
                .approvalStatus(question.getApprovalStatus())
                .createdAt(question.getCreatedAt())
                .build();
    }
}
