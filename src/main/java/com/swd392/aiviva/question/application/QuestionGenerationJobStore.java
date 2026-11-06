package com.swd392.aiviva.question.application;

import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
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
        private volatile String status = "QUEUED";
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
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QuestionGenerationJobStore() {
        this.jdbc = null;
        this.mapper = null;
    }

    @Autowired
    public QuestionGenerationJobStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    void failJobsInterruptedByRestart() {
        if (jdbc != null) {
            jdbc.update("UPDATE question_generation_job SET status='FAILED', error='Backend restarted while this job was running; create a new job.', completed_at=now() WHERE status IN ('QUEUED','RUNNING')");
        }
    }

    public synchronized Snapshot enqueue(UUID subjectId, UUID topicId, UUID owner, List<UUID> documentIds,
            GenerateRequest request, int hourlyLimit) {
        if (jdbc != null) return enqueueDatabase(subjectId, topicId, owner, documentIds, request, hourlyLimit);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusHours(1);
        OffsetDateTime expiry = now.minusHours(24);
        jobs.entrySet().removeIf(item -> item.getValue().completedAt != null && item.getValue().createdAt.isBefore(expiry));
        Entry activeMatch = jobs.values().stream()
                .filter(entry -> entry.owner.equals(owner) && entry.subjectId.equals(subjectId) && entry.topicId.equals(topicId))
                .filter(entry -> entry.status.equals("QUEUED") || entry.status.equals("RUNNING"))
                .filter(entry -> new java.util.HashSet<>(entry.documentIds).equals(new java.util.HashSet<>(documentIds)))
                .filter(entry -> sameGenerationRequest(entry.request, request))
                .findFirst().orElse(null);
        if (activeMatch != null) return activeMatch.snapshot();
        Deque<OffsetDateTime> attempts = recentRequests.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
        if (attempts.size() >= hourlyLimit) throw new RateLimitExceededException(3600, "Generation request limit reached; retry after the current hourly window");
        attempts.addLast(OffsetDateTime.now());
        Entry entry = new Entry(subjectId, topicId, owner, documentIds, request);
        jobs.put(entry.jobId, entry);
        return entry.snapshot();
    }

    private boolean sameGenerationRequest(GenerateRequest left, GenerateRequest right) {
        return left.numberOfQuestions() == right.numberOfQuestions()
                && left.bloomDistribution().equals(right.bloomDistribution())
                && left.language().equals(right.language())
                && left.generateRubric() == right.generateRubric()
                && left.rubricTotalPoints().compareTo(right.rubricTotalPoints()) == 0;
    }

    public Snapshot find(UUID jobId) {
        if (jdbc != null) {
            List<Snapshot> found = jdbc.query("SELECT * FROM question_generation_job WHERE job_id=?", (rs, row) -> snapshot(rs), jobId);
            return found.isEmpty() ? null : found.get(0);
        }
        Entry entry = jobs.get(jobId);
        return entry == null ? null : entry.snapshot();
    }

    public boolean start(UUID jobId) {
        if (jdbc != null) {
            return jdbc.update("UPDATE question_generation_job SET status='RUNNING', started_at=now(), progress=5 WHERE job_id=? AND status='QUEUED'", jobId) == 1;
        }
        Entry entry = required(jobId);
        synchronized (entry) {
            if (!entry.status.equals("QUEUED")) return false;
            entry.status = "RUNNING";
            entry.startedAt = OffsetDateTime.now();
            entry.progress = 5;
            return true;
        }
    }

    public void addQuestion(UUID jobId, UUID questionId) {
        if (jdbc != null) {
            Snapshot job = find(jobId);
            if (job == null) throw new IllegalStateException("Generation job is unavailable");
            List<UUID> questionIds = new ArrayList<>(job.questionIds());
            questionIds.add(questionId);
            int progress = Math.min(95, 10 + questionIds.size() * 85 / job.request().numberOfQuestions());
            jdbc.update("UPDATE question_generation_job SET question_ids_json=?::jsonb, generated_count=?, progress=? WHERE job_id=? AND status='RUNNING'",
                    json(questionIds), questionIds.size(), progress, jobId);
            return;
        }
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.questionIds.add(questionId);
            entry.progress = Math.min(95, 10 + entry.questionIds.size() * 85 / entry.request.numberOfQuestions());
        }
    }

    public void complete(UUID jobId) {
        if (jdbc != null) {
            jdbc.update("UPDATE question_generation_job SET status='DONE', progress=100, completed_at=now() WHERE job_id=? AND status='RUNNING'", jobId);
            return;
        }
        Entry entry = required(jobId);
        synchronized (entry) {
            entry.status = "DONE";
            entry.progress = 100;
            entry.completedAt = OffsetDateTime.now();
        }
    }

    public void fail(UUID jobId, String error) {
        if (jdbc != null) {
            jdbc.update("UPDATE question_generation_job SET status='FAILED', error=?, completed_at=now() WHERE job_id=? AND status IN ('QUEUED','RUNNING')", error, jobId);
            return;
        }
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

    private Snapshot enqueueDatabase(UUID subjectId, UUID topicId, UUID owner, List<UUID> documentIds,
            GenerateRequest request, int hourlyLimit) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, owner.toString());
                statement.execute();
            }
            return null;
        });
        jdbc.update("DELETE FROM question_generation_job WHERE completed_at < now() - interval '24 hours'");
        List<Snapshot> active = jdbc.query("SELECT * FROM question_generation_job WHERE requested_by=? AND subject_id=? AND topic_id=? AND status IN ('QUEUED','RUNNING')",
                (rs, row) -> snapshot(rs), owner, subjectId, topicId);
        for (Snapshot existing : active) {
            if (new java.util.HashSet<>(existing.documentIds()).equals(new java.util.HashSet<>(documentIds))
                    && sameGenerationRequest(existing.request(), request)) return existing;
        }

        List<Map<String, Object>> usage = jdbc.queryForList("SELECT count(*) AS requests, ceil(extract(epoch from min(created_at) + interval '1 hour' - now())) AS retry_after FROM question_generation_job WHERE requested_by=? AND created_at >= now() - interval '1 hour'", owner);
        Number count = (Number) usage.get(0).get("requests");
        if (count.intValue() >= hourlyLimit) {
            Number retryAfter = (Number) usage.get(0).get("retry_after");
            long retry = retryAfter == null ? 3600 : Math.max(1, retryAfter.longValue());
            throw new RateLimitExceededException(retry, "Backend generation request limit reached; retry after the current hourly window");
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_generation_job(job_id, subject_id, topic_id, requested_by, document_ids_json,
                    request_json, question_ids_json, status, progress, generated_count, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, '[]'::jsonb, 'QUEUED', 0, 0, now())
                """, id, subjectId, topicId, owner, json(documentIds), json(request));
        return find(id);
    }

    private Snapshot snapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new Snapshot(rs.getObject("job_id", UUID.class), rs.getObject("subject_id", UUID.class),
                    rs.getObject("topic_id", UUID.class), rs.getObject("requested_by", UUID.class),
                    mapper.readValue(rs.getString("document_ids_json"), new TypeReference<List<UUID>>() {}),
                    mapper.readValue(rs.getString("request_json"), GenerateRequest.class), rs.getString("status"),
                    rs.getInt("progress"), rs.getInt("generated_count"), rs.getString("error"),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                    rs.getObject("completed_at", OffsetDateTime.class),
                    mapper.readValue(rs.getString("question_ids_json"), new TypeReference<List<UUID>>() {}));
        } catch (java.io.IOException e) {
            throw new java.sql.SQLException("Unable to read persisted generation job", e);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("Unable to serialize generation job", e); }
    }

}
