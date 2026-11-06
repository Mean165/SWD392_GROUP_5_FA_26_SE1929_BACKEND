package com.swd392.aiviva.exam.attempt.repository;

import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {
    List<ExamAttempt> findByAssignment_AssignmentIdOrderByAttemptNoAsc(UUID assignmentId);
    java.util.Optional<ExamAttempt> findByAssignment_AssignmentIdAndAttemptNo(UUID assignmentId, int attemptNo);
    List<ExamAttempt> findByAssignment_ExamSession_SessionIdAndStudent_UserIdOrderByAttemptNoAsc(UUID examId, UUID studentId);
    @EntityGraph(attributePaths = {"assignment", "assignment.student", "assignment.examSession", "student"})
    List<ExamAttempt> findByExamSession_SessionIdOrderByAssignment_SlotOrderAscAttemptNoAsc(UUID examId);
    List<ExamAttempt> findByExamSession_SessionIdOrderByAttemptNoAsc(UUID examId);
    List<ExamAttempt> findByExamSession_SessionIdAndAttemptNoOrderByScheduledTimeAsc(UUID examId, int attemptNo);
    List<ExamAttempt> findByStudent_UserId(UUID studentId);
    List<ExamAttempt> findByAssignment_ExamSession_SessionIdAndAttemptNo(UUID examId, int attemptNo);
    Optional<ExamAttempt> findByInterviewSessionId(UUID interviewSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a join fetch a.assignment join fetch a.examSession join fetch a.student where a.attemptId=:attemptId")
    Optional<ExamAttempt> findForUpdateByAttemptId(@Param("attemptId") UUID attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a join fetch a.assignment e join fetch a.examSession join fetch a.student "
            + "where a.attemptId=:attemptId and a.student.userId=:studentId")
    Optional<ExamAttempt> findOwnedForUpdate(@Param("attemptId") UUID attemptId, @Param("studentId") UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a join fetch a.assignment e join fetch a.examSession join fetch a.student "
            + "where a.assignment.assignmentId=:assignmentId and a.attemptNo=:attemptNo")
    Optional<ExamAttempt> findForUpdate(@Param("assignmentId") UUID assignmentId, @Param("attemptNo") int attemptNo);
}
