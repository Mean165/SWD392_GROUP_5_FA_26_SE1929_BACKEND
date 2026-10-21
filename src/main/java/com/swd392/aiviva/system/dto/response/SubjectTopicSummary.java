package com.swd392.aiviva.system.dto.response;

import java.util.UUID;

public record SubjectTopicSummary(
        UUID topicId,
        String topicCode,
        String topicName,
        String description) {
}
