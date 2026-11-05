package com.swd392.aiviva.interview.dto.response;

import java.util.UUID;

public record PlannedQuestionResponse(UUID questionId, int position, String bloomLevel, String questionText,
        java.util.List<String> selectionFlags) {
}
