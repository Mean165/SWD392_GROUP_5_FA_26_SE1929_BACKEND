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
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getBloomLevel(),
                question.getStatus(),
                question.getSource(),
                question.getSubjectCode(),
                question.getTopicCode()
        );
    }
}

