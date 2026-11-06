package com.swd392.aiviva.question.application;

import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class QuestionGenerationJobStore {
    public record Snapshot(UUID jobId, UUID subjectId, UUID topicId, UUID requestedBy, List<UUID> documentIds,
            GenerateRequest request, String status, int progress, int generatedCount, String error,
            OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt,
            List<UUID> questionIds) {}

    private static final class Entry {
        private final UUID jobId = UUID.randomUUID();
        private final UUID subjectId;
        private final UUID topicId;
        private final UUID owner;
        private final List<UUID> documentIds;
        private final GenerateRequest request;
        private final OffsetDateTime createdAt = OffsetDateTime.now();
        private final List<UUID> questionIds = new ArrayList<>();
        private String status = "QUEUED";
        private int progress;
        private String error;
        private OffsetDateTime startedAt;
        private OffsetDateTime completedAt;

        private Entry(UUID subjectId, UUID topicId, UUID owner, List<UUID> documentIds, GenerateRequest request) {
            this.subjectId = subjectId;
            this.topicId = topicId;
            this.owner = owner;
            this.documentIds = List.copyOf(documentIds);
            this.request = request;
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(jobId, subjectId, topicId, owner, documentIds, request, status, progress,
                    questionIds.size(), error, createdAt, startedAt, completedAt, List.copyOf(questionIds));
        }
    }

    private final Map<UUID, Entry> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<OffsetDateTime>> recentRequests = new ConcurrentHashMap<>();

    public synchronized Snapshot enqueue(UUID subjectId, UUID topicId, UUID owner, List<UUID> documentIds,
            GenerateRequest request, int hourlyLimit) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusHours(1);
        OffsetDateTime expiry = now.minusHours(24);
        jobs.entrySet().removeIf(item -> item.getValue().completedAt != null && item.getValue().createdAt.isBefore(expiry));
        Deque<OffsetDateTime> attempts = recentRequests.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
        if (attempts.size() >= hourlyLimit) throw new RateLimitExceededException(3600, "Generation request limit reached; retry after the current hourly window");
        attempts.addLast(OffsetDateTime.now());
        Entry entry = new Entry(subjectId, topicId, owner, documentIds, request);
        jobs.put(entry.jobId, entry);
        return entry.snapshot();
    }

    public Snapshot find(UUID jobId) {
        Entry entry = jobs.get(jobId);
        return entry == null ? null : entry.snapshot();
    }

    public void start(UUID jobId) {
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.status = "RUNNING";
            entry.startedAt = OffsetDateTime.now();
            entry.progress = 5;
        }
    }

    public void addQuestion(UUID jobId, UUID questionId) {
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.questionIds.add(questionId);
            entry.progress = Math.min(95, 10 + entry.questionIds.size() * 85 / entry.request.numberOfQuestions());
        }
    }

    public void complete(UUID jobId) {
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.status = "DONE";
            entry.progress = 100;
            entry.completedAt = OffsetDateTime.now();
        }
    }

    public void fail(UUID jobId, String error) {
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.status = "FAILED";
            entry.error = error;
            entry.completedAt = OffsetDateTime.now();
        }
    }

    private Entry required(UUID jobId) {
        Entry entry = jobs.get(jobId);
        if (entry == null) throw new IllegalStateException("In-memory generation job is unavailable");
        return entry;
    }
}
