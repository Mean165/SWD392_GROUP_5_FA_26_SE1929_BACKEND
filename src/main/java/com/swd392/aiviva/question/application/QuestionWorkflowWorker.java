package com.swd392.aiviva.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.question.ai.port.EmbeddingPort;
import com.swd392.aiviva.question.ai.port.LlmPort;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import com.swd392.aiviva.question.application.QuestionWorkflowEvents.ProcessDocument;
import com.swd392.aiviva.question.application.QuestionWorkflowEvents.RunGeneration;
import com.swd392.aiviva.question.infrastructure.DocumentTextExtractor;
import com.swd392.aiviva.question.storage.port.StoragePort;
import com.swd392.aiviva.question.application.QuestionGenerationJobStore.Snapshot;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class QuestionWorkflowWorker {
    private record Chunk(UUID chunkId, UUID documentId, String filename, int index, String location, String content, List<Double> embedding) {}
    private final JdbcTemplate jdbc;
    private final StoragePort storage;
    private final EmbeddingPort embeddings;
    private final LlmPort llm;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final QuestionGenerationJobStore jobs;
    private final int chunkSize;
    private final int overlap;
    private final BigDecimal rubricTotal;

    public QuestionWorkflowWorker(JdbcTemplate jdbc, StoragePort storage, EmbeddingPort embeddings, LlmPort llm,
            ObjectMapper mapper, TransactionTemplate transactions, QuestionGenerationJobStore jobs,
            @Value("${question.files.chunk-size:1200}") int chunkSize,
            @Value("${question.files.chunk-overlap:150}") int overlap,
            @Value("${question.rubric.total-points:10}") BigDecimal rubricTotal) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.embeddings = embeddings;
        this.llm = llm;
        this.mapper = mapper;
        this.transactions = transactions;
        this.jobs = jobs;
        this.chunkSize = Math.max(300, chunkSize);
        this.overlap = Math.min(Math.max(0, overlap), this.chunkSize / 2);
        this.rubricTotal = rubricTotal;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processDocument(ProcessDocument event) {
        UUID id = event.documentId();
        try {
            jdbc.update("UPDATE question_document SET status='PROCESSING', error_reason=NULL WHERE document_id=?", id);
            Map<String, Object> document = jdbc.queryForMap("SELECT original_filename, storage_key FROM question_document WHERE document_id=?", id);
            byte[] file;
            try (InputStream stream = storage.open((String) document.get("storage_key"))) { file = stream.readAllBytes(); }
            List<DocumentTextExtractor.PageText> pages = new DocumentTextExtractor().extract((String) document.get("original_filename"), file);
            if (split(id, (String) document.get("original_filename"), pages).isEmpty()) throw new IllegalArgumentException("No extractable text was found. The PDF may be a scan; upload a searchable PDF or run OCR first.");
            jdbc.update("UPDATE question_document SET status='READY', processed_at=now(), error_reason=NULL WHERE document_id=?", id);
        } catch (Exception e) {
            String message = safeFailure(e);
            jdbc.update("UPDATE question_document SET status='FAILED', error_reason=?, processed_at=now() WHERE document_id=?", message, id);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void runGeneration(RunGeneration event) {
        UUID jobId = event.jobId();
        Snapshot job = jobs.find(jobId);
        if (job == null) return;
        try {
            jobs.start(jobId);
            GenerateRequest request = job.request();
            List<Chunk> chunks = loadChunks(job.documentIds());
            if (chunks.isEmpty()) throw new IllegalArgumentException("No indexed document chunks are available");
            String topicContext = jdbc.queryForObject("SELECT topic_name || ' ' || coalesce(description, '') FROM subject_topic WHERE topic_id=?", String.class, request.topicId());
            List<Double> queryVector = embeddings.embed(topicContext + ". Bloom distribution: " + request.bloomDistribution());
            List<Chunk> retrieved = chunks.stream().sorted(Comparator.comparingDouble((Chunk c) -> cosine(queryVector, c.embedding())).reversed()).limit(8).toList();
            String prompt = buildPrompt(request, retrieved);
            JsonNode questions = null;
            RuntimeException parseProblem = null;
            for (int attempt = 0; attempt < 2 && questions == null; attempt++) {
                String generatedJson = llm.generateJson(prompt);
                try { questions = validateResponse(mapper.readTree(generatedJson), request, retrieved.size()); }
                catch (Exception e) { parseProblem = new IllegalArgumentException("AI response did not match required question schema", e); }
                if (questions == null && attempt == 0) prompt += "\nThe previous response failed schema validation. Return the complete corrected JSON object only.";
            }
            if (questions == null) throw parseProblem == null ? new IllegalArgumentException("Invalid AI response") : parseProblem;
            for (JsonNode item : questions) {
                if (isDuplicate(request.topicId(), item.path("questionText").asText())) continue;
                UUID questionId = saveGeneratedQuestion(job, retrieved, item);
                jobs.addQuestion(jobId, questionId);
            }
            jobs.complete(jobId);
        } catch (Exception e) {
            jobs.fail(jobId, safeFailure(e));
        }
    }

    private List<Chunk> split(UUID documentId, String filename, List<DocumentTextExtractor.PageText> pages) {
        List<Chunk> result = new ArrayList<>();
        for (DocumentTextExtractor.PageText page : pages) {
            String text = page.text().replaceAll("\\s+", " ").trim();
            for (int start = 0; start < text.length();) {
                int end = Math.min(text.length(), start + chunkSize);
                if (end < text.length()) {
                    int boundary = text.lastIndexOf(' ', end);
                    if (boundary > start + chunkSize / 2) end = boundary;
                }
                String piece = text.substring(start, end).trim();
                if (!piece.isEmpty()) result.add(new Chunk(UUID.randomUUID(), documentId, filename, result.size(), page.location(), piece, List.of()));
                if (end == text.length()) break;
                start = Math.max(start + 1, end - overlap);
            }
        }
        return result;
    }

    private List<Chunk> loadChunks(List<UUID> documentIds) {
        List<Chunk> result = new ArrayList<>();
        for (UUID documentId : documentIds) {
            Map<String, Object> document = jdbc.query("SELECT original_filename, storage_key, status FROM question_document WHERE document_id=?", rs -> {
                if (!rs.next()) return null;
                return Map.of("filename", rs.getString("original_filename"), "storageKey", rs.getString("storage_key"), "status", rs.getString("status"));
            }, documentId);
            if (document == null || !"READY".equals(document.get("status"))) throw new IllegalArgumentException("A selected document is no longer READY");
            try (InputStream stream = storage.open((String) document.get("storageKey"))) {
                List<DocumentTextExtractor.PageText> pages = new DocumentTextExtractor().extract((String) document.get("filename"), stream.readAllBytes());
                for (Chunk chunk : split(documentId, (String) document.get("filename"), pages)) {
                    result.add(new Chunk(chunk.chunkId(), chunk.documentId(), chunk.filename(), chunk.index(), chunk.location(), chunk.content(), embeddings.embed(chunk.content())));
                }
            } catch (java.io.IOException e) { throw new IllegalStateException("Unable to read a selected document", e); }
        }
        return result;
    }

    private double cosine(List<Double> left, List<Double> right) {
        int length = Math.min(left.size(), right.size());
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < length; i++) { dot += left.get(i) * right.get(i); a += left.get(i) * left.get(i); b += right.get(i) * right.get(i); }
        return a == 0 || b == 0 ? 0 : dot / (Math.sqrt(a) * Math.sqrt(b));
    }

    private String buildPrompt(GenerateRequest request, List<Chunk> chunks) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) source.append("[SOURCE ").append(i).append(" | ").append(chunks.get(i).location()).append("]\n")
                .append(chunks.get(i).content()).append("\n\n");
        return "QUESTION_COUNT=" + request.numberOfQuestions() + "\n"
                + "Create exactly " + request.numberOfQuestions() + " distinct oral viva questions in " + request.language() + ". "
                + "Use only the source text below; do not add facts not stated there. No multiple-choice questions. "
                + "Assign exactly the requested Bloom counts: " + request.bloomDistribution() + ". "
                + "Return JSON object {questions:[{questionText,referenceAnswer,bloomLevel,estimatedDifficulty,suggestedAnswerMinutes,rubric:[{name,description,maxPoints,weight}],citationIndexes:[integer]}]}. "
                + "difficulty is 1-5, suggestedAnswerMinutes is 1-60. Bloom must be REMEMBER, UNDERSTAND, APPLY, or ANALYZE. "
                + (request.generateRubric() ? "Rubric maxPoints must total " + request.rubricTotalPoints() + " and weights total 1.0. " : "Set rubric to []. ")
                + "citationIndexes must refer to at least one source.\nSOURCE TEXT:\n" + source;
    }

    static JsonNode validateResponse(JsonNode response, GenerateRequest request, int sourceCount) {
        JsonNode questions = response.path("questions");
        if (!questions.isArray() || questions.size() != request.numberOfQuestions()) throw new IllegalArgumentException("Question count mismatch");
        Map<String, Integer> levels = new java.util.HashMap<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode question : questions) {
            String text = question.path("questionText").asText("").trim();
            String answer = question.path("referenceAnswer").asText("").trim();
            String bloom = question.path("bloomLevel").asText("");
            if (text.isEmpty() || answer.isEmpty() || !Set.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE").contains(bloom)) throw new IllegalArgumentException("Required question field or Bloom level is invalid");
            if (text.contains("?") && text.matches("(?is).*\\b(option [abcd]|select one|choose one)\\b.*")) throw new IllegalArgumentException("Multiple-choice questions are not accepted");
            if (!texts.add(normalize(text))) throw new IllegalArgumentException("Duplicate generated questions");
            levels.merge(bloom, 1, Integer::sum);
            if (!question.path("estimatedDifficulty").isNumber() || question.path("estimatedDifficulty").decimalValue().compareTo(BigDecimal.ONE) < 0
                    || question.path("estimatedDifficulty").decimalValue().compareTo(new BigDecimal("5")) > 0)
                throw new IllegalArgumentException("Estimated difficulty must be between 1 and 5");
            if (!question.path("suggestedAnswerMinutes").canConvertToInt() || question.path("suggestedAnswerMinutes").asInt() < 1 || question.path("suggestedAnswerMinutes").asInt() > 60) throw new IllegalArgumentException("Invalid suggested answer time");
            JsonNode citations = question.path("citationIndexes");
            if (!citations.isArray() || citations.isEmpty()) throw new IllegalArgumentException("Question citation is required");
            for (JsonNode index : citations) if (!index.canConvertToInt() || index.asInt() < 0 || index.asInt() >= sourceCount) throw new IllegalArgumentException("Invalid citation index");
            JsonNode rubric = question.path("rubric");
            if (request.generateRubric()) {
                if (!rubric.isArray() || rubric.isEmpty()) throw new IllegalArgumentException("Rubric criteria are required");
                BigDecimal points = BigDecimal.ZERO, weights = BigDecimal.ZERO;
                for (JsonNode criterion : rubric) {
                    if (criterion.path("name").asText().isBlank() || criterion.path("description").asText().isBlank()
                            || !criterion.path("maxPoints").isNumber() || !criterion.path("weight").isNumber()) throw new IllegalArgumentException("Invalid rubric criterion");
                    points = points.add(criterion.path("maxPoints").decimalValue());
                    weights = weights.add(criterion.path("weight").decimalValue());
                }
                if (points.compareTo(request.rubricTotalPoints()) != 0 || weights.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0)
                    throw new IllegalArgumentException("Rubric totals do not match configuration");
            }
        }
        Map<String, Integer> requestedLevels = new java.util.HashMap<>();
        request.bloomDistribution().forEach((level, count) -> { if (count != null && count > 0) requestedLevels.put(level, count); });
        if (!levels.equals(requestedLevels)) throw new IllegalArgumentException("Generated Bloom distribution mismatch");
        return questions;
    }

    private boolean isDuplicate(UUID topicId, String text) {
        String normalized = normalize(text);
        List<String> existing = jdbc.queryForList("SELECT question_text FROM question WHERE topic_id=?", String.class, topicId);
        for (String item : existing) {
            String other = normalize(item);
            if (normalized.equals(other) || similarity(normalized, other) >= 0.9) return true;
        }
        return false;
    }

    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] previous = new int[b.length() + 1], current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] swap = previous; previous = current; current = swap;
        }
        return 1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length());
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private UUID saveGeneratedQuestion(Snapshot job, List<Chunk> sources, JsonNode item) {
        UUID questionId = UUID.randomUUID();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (JsonNode index : item.path("citationIndexes")) {
            Chunk chunk = sources.get(index.asInt());
            citations.add(Map.of("documentId", chunk.documentId().toString(), "chunkId", chunk.chunkId().toString(),
                    "filename", chunk.filename(), "location", chunk.location(), "chunkIndex", chunk.index()));
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("referenceAnswer", item.path("referenceAnswer").asText().trim());
        metadata.put("estimatedDifficulty", item.path("estimatedDifficulty").decimalValue());
        metadata.put("suggestedAnswerMinutes", item.path("suggestedAnswerMinutes").asInt());
        metadata.put("jobId", job.jobId().toString());
        metadata.put("citations", citations);
        String metadataJson = json(metadata);
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO question(question_id, topic_id, created_by, question_text, bloom_level,
                        source_type, approval_status, created_at, generation_metadata_json)
                    VALUES (?, ?, ?, ?, ?, 'AI_GENERATED', 'DRAFT', now(), ?::jsonb)
                    """, questionId, job.topicId(), job.requestedBy(), item.path("questionText").asText().trim(),
                    item.path("bloomLevel").asText(), metadataJson);
            if (job.request().generateRubric()) {
                for (JsonNode criterion : item.path("rubric")) {
                    jdbc.update("""
                            INSERT INTO rubric_criteria(question_id, criteria_name, expected_knowledge_points, weight_ratio)
                            VALUES (?, ?, ?, ?)
                            """, questionId, criterion.path("name").asText().trim(), criterion.path("description").asText().trim(),
                            criterion.path("weight").decimalValue());
                }
            }
        });
        return questionId;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Unable to serialize question generation metadata", e); }
    }

    private String safeFailure(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("quota") || message.contains("resource_exhausted")) return "AI provider quota or rate limit reached";
        if (message.contains("timeout") || error instanceof java.net.SocketTimeoutException) return "AI provider request timed out";
        if (message.contains("gemini_api_key") || message.contains("not configured")) return "AI provider is not configured; set GEMINI_API_KEY";
        if (error instanceof IllegalArgumentException) return error.getMessage();
        if (error instanceof java.io.IOException) return "Could not read the document; check that it is a valid, unencrypted PDF, PPTX or DOCX file";
        return "AI or document processing failed; check provider configuration and retry";
    }
}
