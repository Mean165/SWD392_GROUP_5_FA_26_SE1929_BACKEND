package com.swd392.aiviva.exam.attempt.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.evaluation.grading.domain.AuditHashChain;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptAuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AttemptAuditRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void append(UUID examId, UUID studentId, UUID actorId, String actorType,
            String eventType, String reason, Object payload) {
        String scope = studentId == null ? "exam" : "student:" + studentId;
        jdbc.execute("select pg_advisory_xact_lock(hashtextextended('attempt:' || '" + examId + ":" + scope + "',0))");
        String previous = jdbc.query("select hash from attempt_audit_events where exam_id=? and student_id is not distinct from ? order by seq desc limit 1",
                rs -> rs.next() ? rs.getString(1) : null, examId, studentId);
        long seq = jdbc.query("select coalesce(max(seq),0)+1 from attempt_audit_events where exam_id=? and student_id is not distinct from ?",
                rs -> rs.next() ? rs.getLong(1) : 1L, examId, studentId);
        String payloadJson = write(payload);
        Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("examId", examId); event.put("studentId", studentId); event.put("actorType", actorType);
        event.put("eventType", eventType); event.put("reason", reason == null ? "" : reason); event.put("payload", payload);
        String eventJson = write(event);
        jdbc.update("insert into attempt_audit_events(exam_id,student_id,seq,event_type,actor_id,actor_type,reason,payload,prev_hash,hash) values(?,?,?,?,?,?,?,?::jsonb,?,?)",
                examId, studentId, seq, eventType, actorId, actorType, reason, payloadJson,
                previous, AuditHashChain.hash(previous, eventJson));
    }

    public void appendExam(UUID examId, UUID actorId, String actorType, String eventType, String reason, Object payload) {
        append(examId, null, actorId, actorType, eventType, reason, payload);
    }

    public List<Map<String, Object>> findByExam(UUID examId) {
        return jdbc.queryForList("select * from attempt_audit_events where exam_id=? order by student_id,seq", examId);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize attempt audit payload", e); }
    }
}
