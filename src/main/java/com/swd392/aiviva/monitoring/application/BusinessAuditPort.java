package com.swd392.aiviva.monitoring.application;

import java.util.UUID;

/** Public business-audit contract; callers do not access monitoring persistence. */
public interface BusinessAuditPort {
    void record(UUID actorId, String action, String entityType, UUID entityId, String beforeJson, String afterJson);
}
