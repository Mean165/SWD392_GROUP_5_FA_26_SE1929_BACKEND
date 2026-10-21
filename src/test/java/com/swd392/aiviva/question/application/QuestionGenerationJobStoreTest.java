package com.swd392.aiviva.question.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionGenerationJobStoreTest {
    @Test
    void tracksJobProgressInMemoryAndEnforcesHourlyLimit() {
        QuestionGenerationJobStore store = new QuestionGenerationJobStore();
        UUID lecturerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        GenerateRequest request = new GenerateRequest(subjectId, topicId, List.of(documentId), 1,
                Map.of("REMEMBER", 0, "UNDERSTAND", 1, "APPLY", 0, "ANALYZE", 0), "en", false, new BigDecimal("10"));

        QuestionGenerationJobStore.Snapshot job = store.enqueue(subjectId, topicId, lecturerId, List.of(documentId), request, 1);
        assertEquals("QUEUED", job.status());
        assertThrows(RateLimitExceededException.class,
                () -> store.enqueue(subjectId, topicId, lecturerId, List.of(documentId), request, 1));

        UUID questionId = UUID.randomUUID();
        store.start(job.jobId());
        store.addQuestion(job.jobId(), questionId);
        store.complete(job.jobId());
        QuestionGenerationJobStore.Snapshot finished = store.find(job.jobId());
        assertEquals("DONE", finished.status());
        assertEquals(100, finished.progress());
        assertEquals(List.of(questionId), finished.questionIds());
        assertNull(new QuestionGenerationJobStore().find(job.jobId()));
    }
}
