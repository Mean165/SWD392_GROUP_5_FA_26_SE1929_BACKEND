-- Apply once to existing PostgreSQL databases. Fresh databases receive these
-- idempotent definitions from schema.sql during Spring SQL initialization.
BEGIN;

ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS response_latency_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS speech_duration_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS longest_pause_ms BIGINT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS filler_count INT;
ALTER TABLE acoustic_fluency_metric ADD COLUMN IF NOT EXISTS confidence_source VARCHAR(20);

CREATE TABLE IF NOT EXISTS stt_result (
    stt_result_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    turn_id UUID NOT NULL UNIQUE REFERENCES interaction_turn(turn_id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(80) NOT NULL,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('streaming','final','dual')),
    streaming_result_json JSONB,
    final_result_json JSONB,
    official_text_raw TEXT,
    official_source VARCHAR(20) CHECK (official_source IS NULL OR official_source IN ('FINAL','STREAMING')),
    text_normalized TEXT,
    corrections_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    needs_human_review BOOLEAN NOT NULL DEFAULT FALSE,
    review_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    reconciliation_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS session_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL REFERENCES viva_attempt(attempt_id) ON DELETE CASCADE,
    seq BIGINT NOT NULL CHECK (seq > 0),
    event_type VARCHAR(80) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_event_attempt_seq UNIQUE (attempt_id, seq)
);

CREATE INDEX IF NOT EXISTS ix_session_event_attempt_created
    ON session_event(attempt_id, created_at);

COMMIT;
