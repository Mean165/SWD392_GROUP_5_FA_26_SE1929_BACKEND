-- Group 4 grading workflow. Apply after V3. Existing assessment/evidence/final rows are retained.
BEGIN;

CREATE TABLE IF NOT EXISTS grading_submissions (
    submission_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attempt_id UUID NOT NULL UNIQUE REFERENCES viva_attempt(attempt_id) ON DELETE RESTRICT,
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    assigned_lecturer_id UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'AI_PENDING'
        CHECK (status IN ('WAITING_SESSION','AI_PENDING','AI_SCORING','AI_SCORED','AI_FAILED','IN_REVIEW','FINALIZED','RELEASED','REOPENED')),
    ai_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (ai_status IN ('NONE','PENDING','RUNNING','DONE','FAILED')),
    release_status VARCHAR(16) NOT NULL DEFAULT 'NOT_RELEASED'
        CHECK (release_status IN ('NOT_RELEASED','RELEASED')),
    lock_holder_id UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    lock_expires_at TIMESTAMPTZ,
    session_finished_at TIMESTAMPTZ NOT NULL,
    ai_scored_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    finalized_by UUID REFERENCES app_user(user_id) ON DELETE RESTRICT,
    finalized_actor_type VARCHAR(16) CHECK (finalized_actor_type IS NULL OR finalized_actor_type IN ('LECTURER','ADMIN')),
    released_at TIMESTAMPTZ,
    priority_score DECIMAL(8,3) NOT NULL DEFAULT 0,
    priority_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    rubric_snapshot_id UUID,
    current_revision_no INT NOT NULL DEFAULT 0 CHECK (current_revision_no >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_grading_final_actor CHECK (
        status NOT IN ('FINALIZED','RELEASED') OR
        (finalized_by IS NOT NULL AND finalized_actor_type IN ('LECTURER','ADMIN') AND finalized_at IS NOT NULL)
    ),
    CONSTRAINT ck_grading_release_state CHECK (
        status <> 'RELEASED' OR (release_status = 'RELEASED' AND released_at IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS grading_rubric_snapshots (
    snapshot_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    snapshot_no INT NOT NULL DEFAULT 1 CHECK (snapshot_no > 0),
    content JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    CONSTRAINT uq_grading_snapshot_submission_no UNIQUE (submission_id, snapshot_no)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_grading_submission_snapshot') THEN
        ALTER TABLE grading_submissions ADD CONSTRAINT fk_grading_submission_snapshot
            FOREIGN KEY (rubric_snapshot_id) REFERENCES grading_rubric_snapshots(snapshot_id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS ai_grading_runs (
    run_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    run_no INT NOT NULL CHECK (run_no > 0),
    trigger VARCHAR(16) NOT NULL CHECK (trigger IN ('AUTO','MANUAL')),
    requested_by UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(120),
    prompt_version VARCHAR(40) NOT NULL,
    temperature DECIMAL(4,3) NOT NULL DEFAULT 0,
    seed BIGINT,
    k_samples INT NOT NULL CHECK (k_samples BETWEEN 1 AND 10),
    input_hash CHAR(64) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    latency_ms BIGINT,
    token_usage JSONB,
    error_code VARCHAR(80),
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_grading_run_submission_no UNIQUE (submission_id, run_no),
    CONSTRAINT uq_ai_grading_run_submission_input UNIQUE (submission_id, input_hash)
);
ALTER TABLE ai_grading_runs ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE ai_grading_runs ADD COLUMN IF NOT EXISTS retry_at TIMESTAMPTZ;

ALTER TABLE ai_rubric_assessment ADD COLUMN IF NOT EXISTS run_id UUID;
ALTER TABLE ai_rubric_assessment ADD COLUMN IF NOT EXISTS sample_scores_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE ai_rubric_assessment ADD COLUMN IF NOT EXISTS variance DECIMAL(10,6);
ALTER TABLE ai_rubric_assessment ADD COLUMN IF NOT EXISTS confidence DECIMAL(5,4);
ALTER TABLE ai_rubric_assessment ADD COLUMN IF NOT EXISTS evidence_verified BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_ai_assessment_run') THEN
        ALTER TABLE ai_rubric_assessment ADD CONSTRAINT fk_ai_assessment_run
            FOREIGN KEY (run_id) REFERENCES ai_grading_runs(run_id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_ai_assessment_run ON ai_rubric_assessment(run_id);

CREATE TABLE IF NOT EXISTS ai_question_feedback (
    feedback_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES ai_grading_runs(run_id) ON DELETE RESTRICT,
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE RESTRICT,
    strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
    weaknesses JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    draft_student_comment TEXT,
    flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    needs_attention BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_question_feedback_run_question UNIQUE (run_id, question_id)
);

ALTER TABLE ai_claim_evidence ADD COLUMN IF NOT EXISTS evidence_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ai_claim_evidence ADD COLUMN IF NOT EXISTS verification_code VARCHAR(40);

ALTER TABLE final_grade_report ALTER COLUMN total_ai_score DROP NOT NULL;

CREATE TABLE IF NOT EXISTS ai_criterion_evidence (
    evidence_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES ai_grading_runs(run_id) ON DELETE RESTRICT,
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE RESTRICT,
    criteria_id UUID NOT NULL REFERENCES rubric_criteria(criteria_id) ON DELETE RESTRICT,
    turn_id UUID REFERENCES interaction_turn(turn_id) ON DELETE RESTRICT,
    quote TEXT NOT NULL,
    char_start INT,
    char_end INT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((char_start IS NULL AND char_end IS NULL) OR (char_start >= 0 AND char_end > char_start))
);

CREATE TABLE IF NOT EXISTS lecturer_criterion_scores (
    score_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE RESTRICT,
    criteria_id UUID NOT NULL REFERENCES rubric_criteria(criteria_id) ON DELETE RESTRICT,
    score DECIMAL(7,3) NOT NULL CHECK (score >= 0),
    max_score DECIMAL(7,3) NOT NULL CHECK (max_score > 0 AND score <= max_score),
    source VARCHAR(16) NOT NULL CHECK (source IN ('ACCEPTED_AI','OVERRIDDEN','MANUAL')),
    based_on_run_id UUID REFERENCES ai_grading_runs(run_id) ON DELETE RESTRICT,
    override_reason TEXT,
    comment TEXT,
    updated_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lecturer_score_override_reason CHECK (source <> 'OVERRIDDEN' OR length(trim(override_reason)) > 0),
    CONSTRAINT uq_lecturer_score_cell UNIQUE (submission_id, question_id, criteria_id)
);

CREATE TABLE IF NOT EXISTS lecturer_question_notes (
    note_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    question_id UUID NOT NULL REFERENCES question(question_id) ON DELETE RESTRICT,
    lecturer_comment TEXT,
    student_visible_comment TEXT,
    comment_status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (comment_status IN ('DRAFT','APPROVED')),
    updated_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lecturer_question_note UNIQUE (submission_id, question_id)
);

CREATE TABLE IF NOT EXISTS grade_revisions (
    revision_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    revision_no INT NOT NULL CHECK (revision_no > 0),
    reason VARCHAR(16) NOT NULL CHECK (reason IN ('INITIAL','REGRADE','CORRECTION')),
    snapshot JSONB NOT NULL,
    total_score DECIMAL(7,3) NOT NULL CHECK (total_score >= 0),
    max_score DECIMAL(7,3) NOT NULL CHECK (max_score > 0 AND total_score <= max_score),
    rubric_snapshot_id UUID NOT NULL REFERENCES grading_rubric_snapshots(snapshot_id) ON DELETE RESTRICT,
    ai_run_id UUID REFERENCES ai_grading_runs(run_id) ON DELETE RESTRICT,
    finalized_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    finalized_actor_type VARCHAR(16) NOT NULL CHECK (finalized_actor_type IN ('LECTURER','ADMIN')),
    finalized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    supersedes_revision_id UUID REFERENCES grade_revisions(revision_id) ON DELETE RESTRICT,
    note TEXT,
    CONSTRAINT uq_grade_revision_no UNIQUE (submission_id, revision_no)
);

CREATE TABLE IF NOT EXISTS grading_audit_events (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    submission_id UUID NOT NULL REFERENCES grading_submissions(submission_id) ON DELETE RESTRICT,
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    seq BIGINT NOT NULL CHECK (seq > 0),
    ts TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('LECTURER','ADMIN','SYSTEM','AI')),
    action VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    prev_hash CHAR(64),
    hash CHAR(64) NOT NULL,
    CONSTRAINT uq_grading_audit_submission_seq UNIQUE (submission_id, seq)
);

CREATE TABLE IF NOT EXISTS grading_exam_settings (
    exam_id UUID PRIMARY KEY REFERENCES exam_session(session_id) ON DELETE CASCADE,
    auto_ai_scoring BOOLEAN NOT NULL DEFAULT TRUE,
    ai_k_samples INT NOT NULL DEFAULT 3 CHECK (ai_k_samples BETWEEN 1 AND 10),
    delta_reason_threshold DECIMAL(6,3) NOT NULL DEFAULT 2.0 CHECK (delta_reason_threshold >= 0),
    include_signals_in_grading BOOLEAN NOT NULL DEFAULT FALSE,
    allow_bulk_accept BOOLEAN NOT NULL DEFAULT FALSE,
    release_policy VARCHAR(24) NOT NULL DEFAULT 'MANUAL' CHECK (release_policy IN ('MANUAL','AUTO_AFTER_FINALIZE')),
    rounding_step DECIMAL(5,3) NOT NULL DEFAULT 0.25 CHECK (rounding_step > 0),
    scale_max DECIMAL(7,3) NOT NULL DEFAULT 10.0 CHECK (scale_max > 0),
    unanswered_policy VARCHAR(24) NOT NULL DEFAULT 'ZERO_REQUIRES_REVIEW'
        CHECK (unanswered_policy IN ('ZERO_REQUIRES_REVIEW','MANUAL_ONLY')),
    updated_by UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_final_grade_requires_lecturer') THEN
        ALTER TABLE final_grade_report ADD CONSTRAINT ck_final_grade_requires_lecturer CHECK (
            (final_teacher_score IS NULL AND finalized_by_lecturer_id IS NULL AND finalized_at IS NULL)
            OR (final_teacher_score IS NOT NULL AND finalized_by_lecturer_id IS NOT NULL AND finalized_at IS NOT NULL)
        );
    END IF;
END $$;

CREATE OR REPLACE FUNCTION reject_grading_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'grading audit events are append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_grading_audit_immutable ON grading_audit_events;
CREATE TRIGGER trg_grading_audit_immutable BEFORE UPDATE OR DELETE ON grading_audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_grading_audit_mutation();

CREATE OR REPLACE FUNCTION reject_grade_revision_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'grade revisions are append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_grade_revision_immutable ON grade_revisions;
CREATE TRIGGER trg_grade_revision_immutable BEFORE UPDATE OR DELETE ON grade_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_grade_revision_mutation();

CREATE OR REPLACE FUNCTION guard_official_grade_actor() RETURNS trigger AS $$
DECLARE actor_role VARCHAR(255);
BEGIN
    IF TG_OP = 'INSERT' OR NEW.final_teacher_score IS DISTINCT FROM OLD.final_teacher_score
       OR NEW.finalized_by_lecturer_id IS DISTINCT FROM OLD.finalized_by_lecturer_id
       OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at THEN
        IF NEW.final_teacher_score IS NULL AND NEW.finalized_by_lecturer_id IS NULL AND NEW.finalized_at IS NULL
           AND EXISTS (SELECT 1 FROM grading_submissions s WHERE s.attempt_id=NEW.attempt_id AND s.status='REOPENED') THEN
            RETURN NEW;
        END IF;
        IF NEW.final_teacher_score IS NULL OR NEW.finalized_by_lecturer_id IS NULL OR NEW.finalized_at IS NULL THEN
            RAISE EXCEPTION 'official final score requires an explicit human actor and timestamp' USING ERRCODE = '23514';
        END IF;
        SELECT r.role_code INTO actor_role FROM app_user u JOIN role r ON r.role_id=u.role_id
          WHERE u.user_id=NEW.finalized_by_lecturer_id;
        IF actor_role NOT IN ('LE','AD') THEN
            RAISE EXCEPTION 'official final score actor must be lecturer or admin' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_final_grade_human_actor ON final_grade_report;
CREATE TRIGGER trg_final_grade_human_actor BEFORE INSERT OR UPDATE ON final_grade_report
    FOR EACH ROW EXECUTE FUNCTION guard_official_grade_actor();

CREATE OR REPLACE FUNCTION reject_grading_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'grading rubric snapshots are immutable' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_grading_snapshot_immutable ON grading_rubric_snapshots;
CREATE TRIGGER trg_grading_snapshot_immutable BEFORE UPDATE OR DELETE ON grading_rubric_snapshots
    FOR EACH ROW EXECUTE FUNCTION reject_grading_snapshot_mutation();

CREATE INDEX IF NOT EXISTS ix_grading_queue_exam_status_finished ON grading_submissions(exam_id,status,session_finished_at DESC);
CREATE INDEX IF NOT EXISTS ix_grading_queue_lecturer_status_finished ON grading_submissions(assigned_lecturer_id,status,session_finished_at DESC);
CREATE INDEX IF NOT EXISTS ix_grading_runs_submission_status_created ON ai_grading_runs(submission_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_grading_runs_pending_retry ON ai_grading_runs(status,retry_at,created_at) WHERE status IN ('PENDING','RUNNING');
CREATE INDEX IF NOT EXISTS ix_grading_audit_submission_seq ON grading_audit_events(submission_id,seq);

COMMIT;
