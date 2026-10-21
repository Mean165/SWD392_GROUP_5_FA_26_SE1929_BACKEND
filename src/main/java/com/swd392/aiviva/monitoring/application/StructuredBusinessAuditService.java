package com.swd392.aiviva.monitoring.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StructuredBusinessAuditService implements BusinessAuditPort {
    private static final Logger log = LoggerFactory.getLogger(StructuredBusinessAuditService.class);
    private final ObjectMapper mapper;

    public StructuredBusinessAuditService(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void record(UUID actorId, String action, String entityType, UUID entityId, String beforeJson, String afterJson) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actorId", actorId);
        event.put("action", action);
        event.put("entityType", entityType);
        event.put("entityId", entityId);
        event.put("before", beforeJson);
        event.put("after", afterJson);
        event.put("occurredAt", java.time.OffsetDateTime.now());
        try { log.info("business-audit {}", mapper.writeValueAsString(event)); }
        catch (Exception e) { log.info("business-audit actorId={} action={} entityType={} entityId={}", actorId, action, entityType, entityId); }
    }
}
