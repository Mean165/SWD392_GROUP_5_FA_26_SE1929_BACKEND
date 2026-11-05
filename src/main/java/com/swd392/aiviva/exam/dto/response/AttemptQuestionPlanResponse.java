package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttemptQuestionPlanResponse {
    private UUID attemptId;
    private int totalQuestions;
    private List<Item> questions;

    @Data
    @Builder
    public static class Item {
        private UUID planId;
        private int position;
        private UUID questionId;
        private String questionText;
        private String bloomLevel;
        private OffsetDateTime createdAt;
        private java.util.List<String> selectionFlags;
    }
}
