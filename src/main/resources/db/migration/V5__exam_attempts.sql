-- Add repeatable exam attempts while retaining enrollment, interview and grading history.
-- Apply with psql after V4; do not run this PL/pgSQL script through Spring SQL init.
BEGIN;

ALTER TABLE student_session_assignment ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ;
ALTER TABLE attempt_question_plan ADD COLUMN IF NOT EXISTS selection_flags_json JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE IF NOT EXISTS exam_attempt_settings (
    exam_id UUID PRIMARY KEY REFERENCES exam_session(session_id) ON DELETE CASCADE,
    max_attempts INT NOT NULL DEFAULT 1 CHECK (max_attempts >= 1),
    score_policy VARCHAR(24) NOT NULL DEFAULT 'LECTURER_CHOOSES'
        CHECK (score_policy IN ('LATEST','HIGHEST','FIRST','AVERAGE','LECTURER_CHOOSES')),
    avoid_repeat_questions BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_minutes INT NOT NULL DEFAULT 0 CHECK (cooldown_minutes >= 0),
    attempt_window_start TIMESTAMPTZ,
    attempt_window_end TIMESTAMPTZ,
    count_on_start_event VARCHAR(32) NOT NULL DEFAULT 'FIRST_QUESTION_ASKED'
        CHECK (count_on_start_event = 'FIRST_QUESTION_ASKED'),
    allow_lecturer_extra_attempts BOOLEAN NOT NULL DEFAULT TRUE,
    count_abandoned BOOLEAN NOT NULL DEFAULT TRUE,
    count_expired BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (attempt_window_start IS NULL OR attempt_window_end IS NULL OR attempt_window_end > attempt_window_start)
);

INSERT INTO exam_attempt_settings(exam_id)
SELECT session_id FROM exam_session ON CONFLICT (exam_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS student_attempt_allowance (
    allowance_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    extra_attempts INT NOT NULL CHECK (extra_attempts >= 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    granted_by UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_student_attempt_allowance UNIQUE(exam_id, student_id)
);

CREATE TABLE IF NOT EXISTS exam_attempts (
    attempt_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    assignment_id UUID NOT NULL REFERENCES student_session_assignment(assignment_id) ON DELETE RESTRICT,
    attempt_no INT NOT NULL CHECK (attempt_no >= 1),
    status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','ABANDONED','EXPIRED','VOIDED','CANCELLED')),
    slot_order INT,
    scheduled_time TIMESTAMPTZ,
    interview_session_id UUID UNIQUE,
    counted BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    void_reason TEXT,
    voided_by UUID REFERENCES app_user(user_id) ON DELETE RESTRICT,
    voided_at TIMESTAMPTZ,
    created_by_type VARCHAR(24) NOT NULL DEFAULT 'SYSTEM'
        CHECK (created_by_type IN ('SYSTEM','LECTURER','STUDENT_SCHEDULE')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_student_attempt_no UNIQUE(exam_id, student_id, attempt_no),
    CHECK (status <> 'VOIDED' OR (void_reason IS NOT NULL AND voided_by IS NOT NULL AND voided_at IS NOT NULL)),
    CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

ALTER TABLE viva_attempt DROP CONSTRAINT IF EXISTS ck_viva_attempt_status;
ALTER TABLE viva_attempt ADD CONSTRAINT ck_viva_attempt_status
    CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','ABANDONED','CANCELLED'));

-- Each old enrollment becomes attempt 1. Reuse the interview UUID where present so links are stable.
INSERT INTO exam_attempts(attempt_id,exam_id,student_id,assignment_id,attempt_no,status,slot_order,scheduled_time,
        interview_session_id,counted,started_at,completed_at,created_by_type)
SELECT COALESCE(v.attempt_id,uuid_generate_v4()), a.session_id, a.student_id, a.assignment_id, 1,
       CASE
         WHEN upper(a.status)='CANCELLED' THEN 'CANCELLED'
         WHEN upper(a.status)='ABSENT' THEN 'EXPIRED'
         WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED' THEN 'COMPLETED'
         WHEN upper(v.status)='IN_PROGRESS' AND EXISTS (
             SELECT 1 FROM interaction_turn t WHERE t.attempt_id=v.attempt_id AND upper(t.turn_type)='MAIN') THEN 'IN_PROGRESS'
         WHEN upper(v.status)='IN_PROGRESS' THEN 'SCHEDULED'
         ELSE 'SCHEDULED'
       END,
       a.slot_order,a.scheduled_time,v.attempt_id,
       CASE
         WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED' THEN TRUE
         WHEN upper(v.status)='IN_PROGRESS' AND EXISTS (
             SELECT 1 FROM interaction_turn t WHERE t.attempt_id=v.attempt_id AND upper(t.turn_type)='MAIN') THEN TRUE
         ELSE FALSE
       END,
       v.start_time,CASE WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED'
                         THEN COALESCE(v.end_time,v.start_time,a.scheduled_time,CURRENT_TIMESTAMP)
                         ELSE v.end_time END,'SYSTEM'
FROM student_session_assignment a
LEFT JOIN viva_attempt v ON v.assignment_id=a.assignment_id
ORDER BY a.session_id,a.student_id
ON CONFLICT (exam_id,student_id,attempt_no) DO NOTHING;

ALTER TABLE viva_attempt ADD COLUMN IF NOT EXISTS exam_attempt_id UUID;
UPDATE viva_attempt v SET exam_attempt_id=ea.attempt_id
FROM exam_attempts ea WHERE ea.interview_session_id=v.attempt_id AND v.exam_attempt_id IS NULL;

DO $$
DECLARE unique_name TEXT;
BEGIN
    SELECT c.conname INTO unique_name
    FROM pg_constraint c
    JOIN LATERAL unnest(c.conkey) AS key(attnum) ON TRUE
    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=key.attnum
    WHERE c.conrelid='viva_attempt'::regclass AND c.contype='u'
      AND array_length(c.conkey,1)=1 AND a.attname='assignment_id'
    LIMIT 1;
    IF unique_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE viva_attempt DROP CONSTRAINT %I', unique_name);
    END IF;
END $$;

ALTER TABLE viva_attempt ALTER COLUMN exam_attempt_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_viva_attempt_exam_attempt') THEN
        ALTER TABLE viva_attempt ADD CONSTRAINT fk_viva_attempt_exam_attempt
            FOREIGN KEY (exam_attempt_id) REFERENCES exam_attempts(attempt_id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_viva_attempt_exam_attempt ON viva_attempt(exam_attempt_id);

ALTER TABLE exam_attempts DROP CONSTRAINT IF EXISTS fk_exam_attempt_interview;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_exam_attempt_interview') THEN
        ALTER TABLE exam_attempts ADD CONSTRAINT fk_exam_attempt_interview
            FOREIGN KEY (interview_session_id) REFERENCES viva_attempt(attempt_id) ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE grading_submissions ADD COLUMN IF NOT EXISTS exam_attempt_id UUID;
ALTER TABLE grading_submissions ADD COLUMN IF NOT EXISTS attempt_no INT;
UPDATE grading_submissions gs SET exam_attempt_id=va.exam_attempt_id,attempt_no=ea.attempt_no
FROM viva_attempt va JOIN exam_attempts ea ON ea.attempt_id=va.exam_attempt_id
WHERE gs.attempt_id=va.attempt_id AND gs.exam_attempt_id IS NULL;
ALTER TABLE grading_submissions ALTER COLUMN attempt_no SET NOT NULL;
ALTER TABLE grading_submissions ALTER COLUMN exam_attempt_id SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_grading_submission_exam_attempt') THEN
        ALTER TABLE grading_submissions ADD CONSTRAINT fk_grading_submission_exam_attempt
            FOREIGN KEY (exam_attempt_id) REFERENCES exam_attempts(attempt_id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS ux_grading_submission_exam_attempt ON grading_submissions(exam_attempt_id);
ALTER TABLE grading_submissions DROP CONSTRAINT IF EXISTS grading_submissions_status_check;
ALTER TABLE grading_submissions DROP CONSTRAINT IF EXISTS ck_grading_submission_status;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_grading_submission_status') THEN
        ALTER TABLE grading_submissions ADD CONSTRAINT ck_grading_submission_status CHECK (
            status IN ('WAITING_SESSION','AI_PENDING','AI_SCORING','AI_SCORED','AI_FAILED','IN_REVIEW','FINALIZED','RELEASED','REOPENED','VOIDED'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS exam_student_results (
    result_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    official_attempt_id UUID REFERENCES exam_attempts(attempt_id) ON DELETE RESTRICT,
    policy_applied VARCHAR(24) NOT NULL CHECK (policy_applied IN ('LATEST','HIGHEST','FIRST','AVERAGE','LECTURER_CHOOSES')),
    computed_total DECIMAL(7,2),
    decided_by UUID REFERENCES app_user(user_id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ,
    released BOOLEAN NOT NULL DEFAULT FALSE,
    released_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exam_student_result UNIQUE(exam_id,student_id),
    CHECK ((official_attempt_id IS NULL) OR computed_total IS NULL),
    CHECK ((decided_by IS NULL) = (decided_at IS NULL))
);

INSERT INTO exam_student_results(exam_id,student_id,policy_applied)
SELECT session_id,student_id,'LECTURER_CHOOSES' FROM student_session_assignment
ON CONFLICT (exam_id,student_id) DO NOTHING;

-- Preserve legacy lecturer-confirmed grades as the existing official selection when actor/time are present.
UPDATE exam_student_results result
SET official_attempt_id=ea.attempt_id, policy_applied='LECTURER_CHOOSES',
    decided_by=grade.finalized_by_lecturer_id, decided_at=grade.finalized_at,
    released=TRUE, released_at=grade.finalized_at
FROM exam_attempts ea
JOIN viva_attempt va ON va.attempt_id=ea.interview_session_id
JOIN final_grade_report grade ON grade.attempt_id=va.attempt_id
WHERE result.exam_id=ea.exam_id AND result.student_id=ea.student_id
  AND result.official_attempt_id IS NULL AND result.decided_by IS NULL
  AND grade.final_teacher_score IS NOT NULL
  AND grade.finalized_by_lecturer_id IS NOT NULL
  AND grade.finalized_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS attempt_audit_events (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exam_id UUID NOT NULL REFERENCES exam_session(session_id) ON DELETE RESTRICT,
    student_id UUID REFERENCES app_user(user_id) ON DELETE RESTRICT,
    seq BIGINT NOT NULL CHECK (seq > 0),
    event_type VARCHAR(64) NOT NULL,
    actor_id UUID REFERENCES app_user(user_id) ON DELETE SET NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('LECTURER','ADMIN','SYSTEM','STUDENT')),
    reason TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    prev_hash CHAR(64),
    hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attempt_audit_seq UNIQUE(exam_id,student_id,seq)
);

-- A zero genesis hash anchors each migrated chain; new appends use SHA-256.
INSERT INTO attempt_audit_events(exam_id,student_id,seq,event_type,actor_type,reason,payload,hash)
SELECT DISTINCT ON (ea.exam_id,ea.student_id) ea.exam_id,ea.student_id,1,'LEGACY_ATTEMPT_IMPORTED','SYSTEM',
       'Legacy enrollment migrated to attempt 1',jsonb_build_object('attemptId',ea.attempt_id,'attemptNo',1),repeat('0',64)
FROM exam_attempts ea
ON CONFLICT (exam_id,student_id,seq) DO NOTHING;

CREATE INDEX IF NOT EXISTS ix_exam_attempt_student_status ON exam_attempts(exam_id,student_id,status);
CREATE INDEX IF NOT EXISTS ix_exam_attempt_exam_status ON exam_attempts(exam_id,status);
CREATE INDEX IF NOT EXISTS ix_exam_attempt_interview ON exam_attempts(interview_session_id);
CREATE INDEX IF NOT EXISTS ix_exam_attempt_assignment_no ON exam_attempts(assignment_id,attempt_no);
CREATE INDEX IF NOT EXISTS ix_grading_submission_exam_attempt_no ON grading_submissions(exam_id,attempt_no);
CREATE INDEX IF NOT EXISTS ix_attempt_allowance_exam_student ON student_attempt_allowance(exam_id,student_id);

CREATE OR REPLACE FUNCTION reject_attempt_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'attempt audit events are append-only' USING ERRCODE='55000';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_attempt_audit_immutable ON attempt_audit_events;
CREATE TRIGGER trg_attempt_audit_immutable BEFORE UPDATE OR DELETE ON attempt_audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_attempt_audit_mutation();

CREATE OR REPLACE FUNCTION create_first_exam_attempt() RETURNS trigger AS $$
DECLARE created_attempt UUID;
BEGIN
    INSERT INTO exam_attempts(exam_id,student_id,assignment_id,attempt_no,status,slot_order,scheduled_time,counted,created_by_type)
    VALUES(NEW.session_id,NEW.student_id,NEW.assignment_id,1,'SCHEDULED',NEW.slot_order,NEW.scheduled_time,FALSE,'SYSTEM')
    ON CONFLICT(exam_id,student_id,attempt_no) DO NOTHING
    RETURNING attempt_id INTO created_attempt;
    IF created_attempt IS NOT NULL THEN
        INSERT INTO exam_student_results(exam_id,student_id,policy_applied)
        VALUES(NEW.session_id,NEW.student_id,'LECTURER_CHOOSES') ON CONFLICT(exam_id,student_id) DO NOTHING;
        INSERT INTO attempt_audit_events(exam_id,student_id,seq,event_type,actor_type,reason,payload,prev_hash,hash)
        VALUES(NEW.session_id,NEW.student_id,coalesce((select max(seq)+1 from attempt_audit_events where exam_id=NEW.session_id and student_id=NEW.student_id),1),
          'ATTEMPT_1_CREATED','SYSTEM',NULL,jsonb_build_object('attemptId',created_attempt,'assignmentId',NEW.assignment_id),NULL,repeat('0',64));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_assignment_first_attempt ON student_session_assignment;
CREATE TRIGGER trg_assignment_first_attempt AFTER INSERT ON student_session_assignment
    FOR EACH ROW EXECUTE FUNCTION create_first_exam_attempt();

COMMIT;
