package com.swd392.aiviva.question.dto.response;

import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.enums.QuestionSource;
import com.swd392.aiviva.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private Long id;
    private String content;
    private BloomLevel bloomLevel;
    private QuestionStatus status;
    private QuestionSource source;
    private String subjectCode;
    private String topicCode;
}

