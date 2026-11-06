-- Run once against databases created before subject-level rubrics.
-- Legacy rubric rows and criteria are retained for audit and existing assessments.
BEGIN;

ALTER TABLE rubric ADD COLUMN IF NOT EXISTS subject_id UUID;
ALTER TABLE rubric ALTER COLUMN topic_id DROP NOT NULL;
ALTER TABLE rubric ALTER COLUMN bloom_level DROP NOT NULL;

ALTER TABLE rubric DROP CONSTRAINT IF EXISTS fk_rubric_subject;
ALTER TABLE rubric ADD CONSTRAINT fk_rubric_subject
    FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE;

UPDATE rubric r
SET subject_id = t.subject_id
FROM subject_topic t
WHERE r.topic_id = t.topic_id
  AND r.subject_id IS NULL
  AND t.subject_id IS NOT NULL;

DROP INDEX IF EXISTS ux_rubric_active_topic_bloom;
UPDATE rubric
SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND (topic_id IS NOT NULL OR bloom_level IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS ux_rubric_active_subject
    ON rubric(subject_id) WHERE status = 'ACTIVE' AND subject_id IS NOT NULL;

ALTER TABLE exam_session ADD COLUMN IF NOT EXISTS max_overlap_percent INT NOT NULL DEFAULT 40;
ALTER TABLE exam_session DROP CONSTRAINT IF EXISTS ck_exam_session_max_overlap_percent;
ALTER TABLE exam_session ADD CONSTRAINT ck_exam_session_max_overlap_percent
    CHECK (max_overlap_percent BETWEEN 0 AND 100);

COMMIT;
