-- Reconcile demo/legacy rows inserted by data.sql after schema.sql has created the attempts model.
-- Spring runs data.sql on each start, so all statements must be idempotent.
INSERT INTO exam_attempts(attempt_id,exam_id,student_id,assignment_id,attempt_no,status,slot_order,scheduled_time,
        interview_session_id,counted,started_at,completed_at,created_by_type)
SELECT COALESCE(v.attempt_id,uuid_generate_v4()),a.session_id,a.student_id,a.assignment_id,1,
       CASE WHEN upper(a.status)='CANCELLED' THEN 'CANCELLED'
            WHEN upper(a.status)='ABSENT' THEN 'EXPIRED'
            WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED' THEN 'COMPLETED'
            WHEN upper(v.status)='IN_PROGRESS' AND EXISTS (
              SELECT 1 FROM interaction_turn t WHERE t.attempt_id=v.attempt_id AND upper(t.turn_type)='MAIN') THEN 'IN_PROGRESS'
            ELSE 'SCHEDULED' END,
       a.slot_order,a.scheduled_time,v.attempt_id,
       CASE WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED' THEN TRUE
            WHEN upper(v.status)='IN_PROGRESS' AND EXISTS (
              SELECT 1 FROM interaction_turn t WHERE t.attempt_id=v.attempt_id AND upper(t.turn_type)='MAIN') THEN TRUE
            ELSE FALSE END,
       v.start_time,
       CASE WHEN upper(a.status)='COMPLETED' OR upper(v.status)='COMPLETED'
            THEN COALESCE(v.end_time,v.start_time,a.scheduled_time,CURRENT_TIMESTAMP) ELSE v.end_time END,
       'SYSTEM'
FROM student_session_assignment a
LEFT JOIN viva_attempt v ON v.assignment_id=a.assignment_id
ORDER BY a.session_id,a.student_id
ON CONFLICT(exam_id,student_id,attempt_no) DO NOTHING;

UPDATE viva_attempt v SET exam_attempt_id=ea.attempt_id
FROM exam_attempts ea
WHERE ea.interview_session_id=v.attempt_id AND v.exam_attempt_id IS NULL;

UPDATE grading_submissions gs SET exam_attempt_id=ea.attempt_id,attempt_no=ea.attempt_no
FROM viva_attempt va JOIN exam_attempts ea ON ea.attempt_id=va.exam_attempt_id
WHERE gs.attempt_id=va.attempt_id AND gs.exam_attempt_id IS NULL;

INSERT INTO exam_student_results(exam_id,student_id,policy_applied)
SELECT session_id,student_id,'LECTURER_CHOOSES' FROM student_session_assignment
ON CONFLICT(exam_id,student_id) DO NOTHING;

UPDATE exam_student_results r SET official_attempt_id=ea.attempt_id,policy_applied='LECTURER_CHOOSES',
    decided_by=fg.finalized_by_lecturer_id,decided_at=fg.finalized_at,released=TRUE,released_at=fg.finalized_at
FROM exam_attempts ea JOIN viva_attempt va ON va.attempt_id=ea.interview_session_id
JOIN final_grade_report fg ON fg.attempt_id=va.attempt_id
WHERE r.exam_id=ea.exam_id AND r.student_id=ea.student_id AND r.official_attempt_id IS NULL AND r.decided_by IS NULL
  AND fg.final_teacher_score IS NOT NULL AND fg.finalized_by_lecturer_id IS NOT NULL AND fg.finalized_at IS NOT NULL;

INSERT INTO attempt_audit_events(exam_id,student_id,seq,event_type,actor_type,reason,payload,hash)
SELECT DISTINCT ON(ea.exam_id,ea.student_id) ea.exam_id,ea.student_id,1,'LEGACY_ATTEMPT_IMPORTED','SYSTEM',
       'Legacy enrollment migrated to attempt 1',jsonb_build_object('attemptId',ea.attempt_id,'attemptNo',1),repeat('0',64)
FROM exam_attempts ea
ON CONFLICT(exam_id,student_id,seq) DO NOTHING;
