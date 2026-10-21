package com.swd392.aiviva.question.application;

import java.util.UUID;

public final class QuestionWorkflowEvents {
    private QuestionWorkflowEvents() {}
    public record ProcessDocument(UUID documentId) {}
    public record RunGeneration(UUID jobId) {}
}
