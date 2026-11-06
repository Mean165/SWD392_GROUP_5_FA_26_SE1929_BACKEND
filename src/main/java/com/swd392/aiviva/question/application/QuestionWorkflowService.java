package com.swd392.aiviva.question.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.auth.service.CurrentUserProvider;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.DocumentTooLargeException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.common.exception.UnsupportedDocumentException;
import com.swd392.aiviva.monitoring.application.BusinessAuditPort;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.BulkApproveRequest;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.CriterionInput;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.EditQuestionRequest;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import com.swd392.aiviva.question.application.QuestionWorkflowEvents.ProcessDocument;
import com.swd392.aiviva.question.application.QuestionWorkflowEvents.RunGeneration;
import com.swd392.aiviva.question.storage.port.StoragePort;
import com.swd392.aiviva.question.domain.QuestionOwnershipPolicy;
import com.swd392.aiviva.question.domain.QuestionStateTransition;
import com.swd392.aiviva.question.enums.BloomLevel;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionWorkflowService {
    private static final Set<String> BLOOM_LEVELS = Set.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE");
    private static final Map<String, String> FILE_TYPES = Map.of(
            ".pdf", "application/pdf",
            ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider currentUser;
    private final BusinessAuditPort audit;
    private final StoragePort storage;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final QuestionGenerationJobStore jobs;
    private final long maxFileBytes;
    private final int requestsPerHour;
    private final BigDecimal rubricTotalPoints;
    private final Set<String> allowedMimeTypes;

    public QuestionWorkflowService(JdbcTemplate jdbc, CurrentUserProvider currentUser, BusinessAuditPort audit,
            StoragePort storage, ObjectMapper mapper, ApplicationEventPublisher events, QuestionGenerationJobStore jobs,
            @Value("${question.files.max-bytes:20971520}") long maxFileBytes,
            @Value("${question.generation.requests-per-hour:5}") int requestsPerHour,
            @Value("${question.rubric.total-points:10}") BigDecimal rubricTotalPoints,
            @Value("${question.files.allowed-mime-types:application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation,application/vnd.openxmlformats-officedocument.wordprocessingml.document}") String allowedMimeTypes) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.audit = audit;
        this.storage = storage;
        this.mapper = mapper;
        this.events = events;
        this.jobs = jobs;
        this.maxFileBytes = maxFileBytes;
        this.requestsPerHour = requestsPerHour;
        this.rubricTotalPoints = rubricTotalPoints;
        this.allowedMimeTypes = java.util.Arrays.stream(allowedMimeTypes.split(",")).map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<Map<String, Object>> subjects() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT subject_id AS \"subjectId\", subject_code AS \"subjectCode\", subject_name AS \"subjectName\", assigned_lecturer_ids_json::text AS assigned FROM subject ORDER BY subject_name");
        UUID actor = currentUser.currentUserId();
        return rows.stream().filter(row -> currentUser.isAdmin() || assigned((String) row.get("assigned"), actor))
                .map(row -> Map.<String, Object>of("subjectId", row.get("subjectId"), "subjectCode", row.get("subjectCode"), "subjectName", row.get("subjectName")))
                .toList();
    }

    public List<Map<String, Object>> topics(UUID subjectId) {
        requireSubjectAccess(subjectId);
        return jdbc.queryForList("SELECT topic_id AS \"topicId\", topic_code AS \"topicCode\", topic_name AS \"topicName\", description FROM subject_topic WHERE subject_id=? ORDER BY topic_name", subjectId);
    }

    @Transactional
    public Map<String, Object> upload(UUID subjectId, UUID topicId, MultipartFile file) {
        requireSubjectAccess(subjectId);
        requireTopic(subjectId, topicId);
        validateFile(file);
        UUID id = UUID.randomUUID();
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        try (java.io.InputStream content = file.getInputStream()) {
            String key = storage.store(id, filename, content);
            jdbc.update("""
                    INSERT INTO question_document(document_id, subject_id, topic_id, uploaded_by, original_filename,
                        content_type, file_size, storage_key, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED')
                    """, id, subjectId, topicId, currentUser.currentUserId(), filename, file.getContentType(), file.getSize(), key);
            audit.record(currentUser.currentUserId(), "DOCUMENT_UPLOADED", "QUESTION_DOCUMENT", id, null,
                    mapper.writeValueAsString(Map.of("subjectId", subjectId, "topicId", topicId, "filename", filename, "size", file.getSize())));
            events.publishEvent(new ProcessDocument(id));
            return document(id);
        } catch (IOException e) {
            throw new BusinessException("Unable to store or queue document processing");
        }
    }

    public Map<String, Object> document(UUID documentId) {
        Map<String, Object> row = jdbc.query("SELECT * FROM question_document WHERE document_id=?", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> data = new HashMap<>();
            data.put("documentId", rs.getObject("document_id"));
            data.put("subjectId", rs.getObject("subject_id"));
            data.put("topicId", rs.getObject("topic_id"));
            data.put("uploadedBy", rs.getObject("uploaded_by"));
            data.put("filename", rs.getString("original_filename"));
            data.put("contentType", rs.getString("content_type"));
            data.put("size", rs.getLong("file_size"));
            data.put("status", rs.getString("status"));
            data.put("errorReason", rs.getString("error_reason"));
            data.put("uploadedAt", rs.getObject("uploaded_at"));
            data.put("processedAt", rs.getObject("processed_at"));
            return data;
        }, documentId);
        if (row == null) throw new ResourceNotFoundException("Document not found");
        authorizeDocument(documentId);
        requireSubjectAccess(UUID.fromString(row.get("subjectId").toString()));
        return row;
    }

    public List<Map<String, Object>> documents(UUID subjectId, UUID topicId) {
        requireSubjectAccess(subjectId);
        requireTopic(subjectId, topicId);
        String sql = "SELECT document_id AS \"documentId\", subject_id AS \"subjectId\", topic_id AS \"topicId\", uploaded_by AS \"uploadedBy\", original_filename AS filename, content_type AS \"contentType\", file_size AS size, status, error_reason AS \"errorReason\", uploaded_at AS \"uploadedAt\", processed_at AS \"processedAt\" FROM question_document WHERE subject_id=? AND topic_id=?";
        if (currentUser.isAdmin()) return jdbc.queryForList(sql + " ORDER BY uploaded_at DESC", subjectId, topicId);
        return jdbc.queryForList(sql + " AND uploaded_by=? ORDER BY uploaded_at DESC", subjectId, topicId, currentUser.currentUserId());
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        authorizeDocument(documentId);
        String citationFilter = json(List.of(Map.of("documentId", documentId.toString())));
        Integer citations = jdbc.queryForObject("SELECT count(*) FROM question WHERE generation_metadata_json->'citations' @> ?::jsonb", Integer.class, citationFilter);
        if (citations != null && citations > 0) throw new BusinessException("Document is cited by generated questions and cannot be deleted");
        String key = jdbc.queryForObject("SELECT storage_key FROM question_document WHERE document_id=?", String.class, documentId);
        jdbc.update("DELETE FROM question_document WHERE document_id=?", documentId);
        try { storage.delete(key); } catch (IOException ignored) { }
        audit.record(currentUser.currentUserId(), "DOCUMENT_DELETED", "QUESTION_DOCUMENT", documentId, null, null);
    }

    @Transactional
    public Map<String, Object> createJob(GenerateRequest request) {
        UUID actor = currentUser.currentUserId();
        requireSubjectAccess(request.subjectId());
        requireTopic(request.subjectId(), request.topicId());
        if (request.documentIds().size() > 10 || new java.util.HashSet<>(request.documentIds()).size() != request.documentIds().size())
            throw new BusinessException("documentIds must contain 1 to 10 unique documents");
        if (request.rubricTotalPoints().compareTo(rubricTotalPoints) != 0) throw new BusinessException("rubricTotalPoints must match configured total " + rubricTotalPoints);
        if (!Set.of("vi", "en").contains(request.language())) throw new BusinessException("language must be vi or en");
        int bloomCount = 0;
        for (var entry : request.bloomDistribution().entrySet()) {
            if (!BLOOM_LEVELS.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0)
                throw new BusinessException("bloomDistribution accepts REMEMBER, UNDERSTAND, APPLY, ANALYZE with non-negative counts");
            bloomCount += entry.getValue();
        }
        if (bloomCount != request.numberOfQuestions()) throw new BusinessException("Bloom distribution must add up to numberOfQuestions");
        for (UUID documentId : request.documentIds()) {
            authorizeDocument(documentId);
            String status = jdbc.query("SELECT status FROM question_document WHERE document_id=? AND subject_id=? AND topic_id=?", rs -> rs.next() ? rs.getString(1) : null,
                    documentId, request.subjectId(), request.topicId());
            if (!"READY".equals(status)) throw new BusinessException("Every selected document must be READY and belong to the selected topic");
        }
        QuestionGenerationJobStore.Snapshot job = jobs.enqueue(request.subjectId(), request.topicId(), actor, request.documentIds(), request, requestsPerHour);
        try {
            audit.record(actor, "QUESTION_GENERATION_CREATED", "QUESTION_GENERATION_JOB", job.jobId(), null, mapper.writeValueAsString(request));
            events.publishEvent(new RunGeneration(job.jobId()));
        } catch (Exception e) {
            if (e instanceof BusinessException business) throw business;
            jobs.fail(job.jobId(), "Unable to queue generation job");
            throw new BusinessException("Unable to queue question generation job");
        }
        return jobResponse(job);
    }

    public Map<String, Object> job(UUID jobId) {
        QuestionGenerationJobStore.Snapshot job = jobs.find(jobId);
        if (job == null) throw new ResourceNotFoundException("Generation job is unavailable; in-memory jobs are lost when the backend restarts");
        if (!currentUser.isAdmin() && !job.requestedBy().equals(currentUser.currentUserId()))
            throw new org.springframework.security.access.AccessDeniedException("Job is not available to this Lecturer");
        requireSubjectAccess(job.subjectId());
        return jobResponse(job);
    }

    private Map<String, Object> jobResponse(QuestionGenerationJobStore.Snapshot job) {
        Map<String, Object> result = new HashMap<>();
        result.put("jobId", job.jobId()); result.put("subjectId", job.subjectId()); result.put("topicId", job.topicId());
        result.put("status", job.status()); result.put("progress", job.progress()); result.put("generatedCount", job.generatedCount());
        result.put("error", job.error()); result.put("createdAt", job.createdAt()); result.put("startedAt", job.startedAt());
        result.put("completedAt", job.completedAt()); result.put("questionIds", job.questionIds());
        return result;
    }

    public Map<String, Object> listQuestions(UUID jobId, UUID documentId, UUID subjectId, UUID topicId,
            String status, String bloomLevel, String sourceType, int page, int size, boolean bankOnly) {
        if (size < 1 || size > 100 || page < 0) throw new BusinessException("page must be >= 0 and size must be between 1 and 100");
        StringBuilder where = new StringBuilder(" WHERE true ");
        List<Object> args = new ArrayList<>();
        if (!currentUser.isAdmin()) {
            where.append(" AND EXISTS (SELECT 1 FROM subject_topic t JOIN subject s ON s.subject_id=t.subject_id WHERE t.topic_id=q.topic_id AND s.assigned_lecturer_ids_json @> jsonb_build_array(CAST(? AS text)))");
            args.add(currentUser.currentUserId());
            where.append(" AND (q.approval_status='APPROVED' OR q.created_by=?)"); args.add(currentUser.currentUserId());
        }
        if (jobId != null) { where.append(" AND q.generation_metadata_json->>'jobId'=?"); args.add(jobId.toString()); }
        if (documentId != null) {
            where.append(" AND q.generation_metadata_json->'citations' @> ?::jsonb");
            args.add(json(List.of(Map.of("documentId", documentId.toString()))));
        }
        if (subjectId != null) { requireSubjectAccess(subjectId); where.append(" AND t.subject_id=?"); args.add(subjectId); }
        if (topicId != null) { where.append(" AND q.topic_id=?"); args.add(topicId); }
        if (bankOnly) where.append(" AND q.approval_status='APPROVED'");
        else if (status != null && !status.isBlank()) { where.append(" AND q.approval_status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (bloomLevel != null && !bloomLevel.isBlank()) {
            where.append(" AND CASE WHEN q.bloom_level='EVALUATE' THEN 'ANALYZE' ELSE q.bloom_level END=?");
            args.add(BloomLevel.normalizeLegacyValue(bloomLevel));
        }
        if (sourceType != null && !sourceType.isBlank()) { where.append(" AND q.source_type=?"); args.add(sourceType.toUpperCase(Locale.ROOT)); }
        String from = " FROM question q JOIN subject_topic t ON t.topic_id=q.topic_id" + where;
        Long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(size); pageArgs.add(page * size);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT q.question_id AS \"questionId\", q.topic_id AS \"topicId\", t.subject_id AS \"subjectId\", q.question_text AS \"questionText\", q.bloom_level AS \"bloomLevel\", q.source_type AS \"sourceType\", q.approval_status AS status, q.created_at AS \"createdAt\", q.generation_metadata_json::text AS \"generationMetadata\"" + from + " ORDER BY q.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        items.forEach(item -> item.put("bloomLevel", BloomLevel.normalizeLegacyValue((String) item.get("bloomLevel"))));
        items.forEach(this::attachGenerationMetadata);
        return Map.of("items", items, "page", page, "size", size, "totalItems", total == null ? 0 : total,
                "totalPages", total == null ? 0 : (int) Math.ceil((double) total / size));
    }

    public Map<String, Object> question(UUID questionId) {
        Map<String, Object> data = jdbc.query("SELECT q.*, t.subject_id FROM question q JOIN subject_topic t ON t.topic_id=q.topic_id WHERE q.question_id=?", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", rs.getObject("question_id")); item.put("subjectId", rs.getObject("subject_id"));
            item.put("topicId", rs.getObject("topic_id")); item.put("createdBy", rs.getObject("created_by"));
            item.put("questionText", rs.getString("question_text"));
            item.put("bloomLevel", BloomLevel.normalizeLegacyValue(rs.getString("bloom_level"))); item.put("sourceType", rs.getString("source_type"));
            item.put("status", rs.getString("approval_status")); item.put("createdAt", rs.getObject("created_at"));
            item.put("generationMetadata", rs.getString("generation_metadata_json")); return item;
        }, questionId);
        if (data == null) throw new ResourceNotFoundException("Question not found");
        authorizeQuestion(questionId, UUID.fromString(data.get("subjectId").toString()), String.valueOf(data.get("status")), UUID.fromString(data.get("createdBy").toString()));
        attachGenerationMetadata(data);
        BigDecimal totalPoints = rubricTotalPoints;
        List<Map<String, Object>> rubric = jdbc.queryForList("SELECT criteria_id AS \"criteriaId\", criteria_name AS name, expected_knowledge_points AS description, weight_ratio AS weight FROM rubric_criteria WHERE question_id=? ORDER BY criteria_id", questionId);
        rubric.forEach(item -> item.put("maxPoints", ((BigDecimal) item.get("weight")).multiply(totalPoints)));
        data.put("rubric", rubric);
        return data;
    }

    private void attachGenerationMetadata(Map<String, Object> question) {
        Object raw = question.remove("generationMetadata");
        try {
            com.fasterxml.jackson.databind.JsonNode metadata = raw == null ? mapper.createObjectNode() : mapper.readTree(raw.toString());
            question.put("referenceAnswer", metadata.path("referenceAnswer").isTextual() ? metadata.path("referenceAnswer").asText() : null);
            question.put("estimatedDifficulty", metadata.path("estimatedDifficulty").isNumber() ? metadata.path("estimatedDifficulty").decimalValue() : null);
            question.put("suggestedAnswerMinutes", metadata.path("suggestedAnswerMinutes").canConvertToInt() ? metadata.path("suggestedAnswerMinutes").asInt() : null);
            question.put("generationJobId", metadata.path("jobId").isTextual() ? metadata.path("jobId").asText() : null);
            question.put("citations", metadata.path("citations").isArray() ? mapper.convertValue(metadata.path("citations"), new TypeReference<List<Map<String, Object>>>() {}) : List.of());
        } catch (Exception e) { throw new IllegalStateException("Question generation metadata is invalid", e); }
    }

    @Transactional
    public Map<String, Object> editQuestion(UUID questionId, EditQuestionRequest request) {
        Map<String, Object> before = question(questionId);
        UUID subjectId = UUID.fromString(before.get("subjectId").toString());
        authorizeQuestion(questionId, subjectId, String.valueOf(before.get("status")), UUID.fromString(before.get("createdBy").toString()));
        QuestionStateTransition.requireEditable(String.valueOf(before.get("status")));
        String bloomLevel = BloomLevel.normalizeLegacyValue(request.bloomLevel());
        if (!BLOOM_LEVELS.contains(bloomLevel)) throw new BusinessException("Invalid Bloom level");
        validateRubric(request.rubric());
        String beforeJson = json(before);
        var metadata = generationMetadata(questionId);
        metadata.put("referenceAnswer", request.referenceAnswer());
        metadata.put("estimatedDifficulty", request.estimatedDifficulty());
        metadata.put("suggestedAnswerMinutes", request.suggestedAnswerMinutes());
        jdbc.update("UPDATE question SET question_text=?, bloom_level=?, generation_metadata_json=?::jsonb WHERE question_id=?",
                request.questionText().trim(), bloomLevel, metadata.toString(), questionId);
        jdbc.update("DELETE FROM rubric_criteria WHERE question_id=?", questionId);
        saveRubric(questionId, request.rubric());
        Map<String, Object> after = question(questionId);
        audit.record(currentUser.currentUserId(), "QUESTION_EDITED", "QUESTION", questionId, beforeJson, json(after));
        return after;
    }

    @Transactional
    public Map<String, Object> approve(UUID questionId) {
        Map<String, Object> before = question(questionId);
        authorizeQuestion(questionId, UUID.fromString(before.get("subjectId").toString()), String.valueOf(before.get("status")), UUID.fromString(before.get("createdBy").toString()));
        QuestionStateTransition.requireApprovable(String.valueOf(before.get("status")));
        jdbc.update("UPDATE question SET approval_status='APPROVED' WHERE question_id=?", questionId);
        Map<String, Object> after = question(questionId);
        audit.record(currentUser.currentUserId(), "QUESTION_APPROVED", "QUESTION", questionId, json(before), json(after));
        return after;
    }

    @Transactional
    public List<Map<String, Object>> approveMany(BulkApproveRequest request) {
        return request.questionIds().stream().map(this::approve).toList();
    }

    @Transactional
    public Map<String, Object> reject(UUID questionId) {
        Map<String, Object> before = question(questionId);
        authorizeQuestion(questionId, UUID.fromString(before.get("subjectId").toString()), String.valueOf(before.get("status")), UUID.fromString(before.get("createdBy").toString()));
        QuestionStateTransition.requireRejectable(String.valueOf(before.get("status")));
        jdbc.update("UPDATE question SET approval_status='REJECTED' WHERE question_id=?", questionId);
        audit.record(currentUser.currentUserId(), "QUESTION_REJECTED", "QUESTION", questionId, json(before), "{\"status\":\"REJECTED\"}");
        return Map.of("questionId", questionId, "status", "REJECTED");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Document file is empty");
        if (file.getSize() > maxFileBytes) throw new DocumentTooLargeException("Document exceeds configured maximum size of " + maxFileBytes + " bytes");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension = FILE_TYPES.keySet().stream().filter(filename::endsWith).findFirst().orElse(null);
        if (extension == null) throw new UnsupportedDocumentException("Unsupported file extension; accepted formats are PDF, PPTX and DOCX");
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!allowedMimeTypes.contains(mime) || !FILE_TYPES.get(extension).equals(mime)) throw new UnsupportedDocumentException("File MIME type does not match an allowed extension and MIME type");
    }

    private void requireSubjectAccess(UUID subjectId) {
        String assigned = jdbc.query("SELECT assigned_lecturer_ids_json::text FROM subject WHERE subject_id=?", rs -> rs.next() ? rs.getString(1) : null, subjectId);
        if (assigned == null) throw new ResourceNotFoundException("Subject not found");
        if (!currentUser.isAdmin() && !assigned(assigned, currentUser.currentUserId()))
            throw new org.springframework.security.access.AccessDeniedException("Lecturer is not assigned to this subject");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode generationMetadata(UUID questionId) {
        try {
            String value = jdbc.queryForObject("SELECT generation_metadata_json::text FROM question WHERE question_id=?", String.class, questionId);
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(value == null ? "{}" : value);
            return node.isObject() ? ((com.fasterxml.jackson.databind.node.ObjectNode) node).deepCopy() : mapper.createObjectNode();
        } catch (Exception e) { throw new IllegalStateException("Question generation metadata is invalid", e); }
    }

    private boolean assigned(String json, UUID lecturerId) {
        try {
            for (var id : mapper.readTree(json)) if (lecturerId.toString().equals(id.asText())) return true;
        } catch (Exception e) { throw new IllegalStateException("Subject Lecturer assignment metadata is invalid", e); }
        return false;
    }

    private void requireTopic(UUID subjectId, UUID topicId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM subject_topic WHERE subject_id=? AND topic_id=?", Integer.class, subjectId, topicId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Topic does not belong to subject");
    }

    private void authorizeDocument(UUID documentId) {
        Map<String, Object> row = jdbc.query("SELECT uploaded_by, subject_id FROM question_document WHERE document_id=?", rs -> {
            if (!rs.next()) return null;
            return Map.of("uploadedBy", rs.getObject("uploaded_by"), "subjectId", rs.getObject("subject_id"));
        }, documentId);
        if (row == null) throw new ResourceNotFoundException("Document not found");
        requireSubjectAccess(UUID.fromString(row.get("subjectId").toString()));
        if (!QuestionOwnershipPolicy.canManageDocument(currentUser.isAdmin(), currentUser.currentUserId(), UUID.fromString(row.get("uploadedBy").toString())))
            throw new org.springframework.security.access.AccessDeniedException("Document is owned by another Lecturer");
    }

    private void authorizeQuestion(UUID id, UUID subjectId, String status, UUID creatorId) {
        requireSubjectAccess(subjectId);
        if (!QuestionOwnershipPolicy.canViewNonApprovedQuestion(currentUser.isAdmin(), currentUser.currentUserId(), creatorId, status))
            throw new org.springframework.security.access.AccessDeniedException("Draft question is owned by another Lecturer");
    }

    private void validateRubric(List<CriterionInput> criteria) {
        if (criteria == null || criteria.isEmpty()) throw new BusinessException("Rubric must contain at least one criterion");
        BigDecimal points = BigDecimal.ZERO, weights = BigDecimal.ZERO;
        for (CriterionInput item : criteria) {
            if (item.name() == null || item.name().isBlank() || item.description() == null || item.description().isBlank())
                throw new BusinessException("Rubric criterion name and description cannot be empty");
            if (item.maxPoints() == null || item.maxPoints().signum() <= 0 || item.weight() == null || item.weight().signum() <= 0)
                throw new BusinessException("Rubric points and weight must be greater than zero");
            BigDecimal derived = item.weight().multiply(rubricTotalPoints).setScale(2, RoundingMode.HALF_UP);
            if (derived.compareTo(item.maxPoints().setScale(2, RoundingMode.HALF_UP)) != 0)
                throw new BusinessException("Each rubric maxPoints must equal weight times configured total points");
            points = points.add(item.maxPoints()); weights = weights.add(item.weight());
        }
        if (points.compareTo(rubricTotalPoints) != 0) throw new BusinessException("Rubric criterion points must total " + rubricTotalPoints);
        if (weights.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0)
            throw new BusinessException("Rubric weights must total 1.0");
    }

    private void saveRubric(UUID questionId, List<CriterionInput> criteria) {
        for (CriterionInput item : criteria) jdbc.update("""
                INSERT INTO rubric_criteria(question_id, criteria_name, expected_knowledge_points, weight_ratio)
                VALUES (?, ?, ?, ?)
                """, questionId, item.name().trim(), item.description().trim(), item.weight());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }
}
