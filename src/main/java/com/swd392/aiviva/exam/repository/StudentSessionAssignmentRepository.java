package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentSessionAssignmentRepository extends JpaRepository<StudentSessionAssignment, UUID> {

    boolean existsByExamSessionSessionIdAndStudentUserId(UUID sessionId, UUID studentId);

    Optional<StudentSessionAssignment> findByExamSessionSessionIdAndStudentUserId(UUID sessionId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudentSessionAssignment a join fetch a.examSession join fetch a.student "
            + "where a.examSession.sessionId = :sessionId and a.student.userId = :studentId")
    Optional<StudentSessionAssignment> findBySessionAndStudentForUpdate(
            @Param("sessionId") UUID sessionId, @Param("studentId") UUID studentId);

    List<StudentSessionAssignment> findByExamSessionSessionId(UUID sessionId);

    List<StudentSessionAssignment> findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(UUID sessionId);

    List<StudentSessionAssignment> findByExamSessionSessionIdAndStatusIn(UUID sessionId, List<String> statuses);

    List<StudentSessionAssignment> findByStudentUserId(UUID studentId);

    Optional<StudentSessionAssignment> findByAssignmentIdAndStudentUserId(UUID assignmentId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudentSessionAssignment a join fetch a.examSession join fetch a.student where a.assignmentId = :assignmentId and a.student.userId = :studentId")
    Optional<StudentSessionAssignment> findOwnedAssignmentForUpdate(@Param("assignmentId") UUID assignmentId, @Param("studentId") UUID studentId);

    Optional<StudentSessionAssignment> findFirstByExamSessionSessionIdAndScheduledTimeLessThanOrderByScheduledTimeDesc(
            UUID sessionId, OffsetDateTime scheduledTime);

    @Query("select a from StudentSessionAssignment a where a.examSession.sessionId = :sessionId and a.slotOrder < :slotOrder order by a.slotOrder desc, a.scheduledTime desc")
    List<StudentSessionAssignment> findPreviousAssignments(@Param("sessionId") UUID sessionId, @Param("slotOrder") int slotOrder, org.springframework.data.domain.Pageable pageable);


    @Query(value = """
            select new com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse(
                a.assignmentId, a.scheduledTime, a.status, s.sessionId, s.sessionName, s.status,
                s.timeLimitMinutes, s.maxMainQuestions, s.maxFollowupPerQuestion, v.attemptId, v.status, t.topicName, a.slotOrder)
            from StudentSessionAssignment a
            join a.examSession s
            left join s.topic t
            left join VivaAttempt v on v.assignment.assignmentId = a.assignmentId
            where a.student.userId = :studentId
              and ((a.scheduledTime >= :fromTime and a.scheduledTime <= :toTime)
                   or upper(a.status) = 'IN_PROGRESS')
              and (:filterByStatus = false or upper(a.status) = upper(:status))
            order by a.scheduledTime asc
            """,
            countQuery = """
            select count(a)
            from StudentSessionAssignment a
            where a.student.userId = :studentId
              and ((a.scheduledTime >= :fromTime and a.scheduledTime <= :toTime)
                   or upper(a.status) = 'IN_PROGRESS')
              and (:filterByStatus = false or upper(a.status) = upper(:status))
            """)
    Page<StudentExamAssignmentResponse> findStudentSchedule(
            @Param("studentId") UUID studentId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime,
            @Param("status") String status,
            @Param("filterByStatus") boolean filterByStatus,
            Pageable pageable);

    @Query(value = """
            select new com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse(
                a.assignmentId, a.scheduledTime, a.status, s.sessionId, s.sessionName, s.status,
                s.timeLimitMinutes, s.maxMainQuestions, s.maxFollowupPerQuestion, v.attemptId, v.status, t.topicName, a.slotOrder)
            from StudentSessionAssignment a
            join a.examSession s
            left join s.topic t
            left join VivaAttempt v on v.assignment.assignmentId = a.assignmentId
            where a.student.userId = :studentId
                and upper(a.status) in ('COMPLETED', 'ABSENT', 'CANCELLED')
            order by a.scheduledTime desc
            """,
            countQuery = """
            select count(a)
            from StudentSessionAssignment a
            where a.student.userId = :studentId
              and upper(a.status) in ('COMPLETED', 'ABSENT', 'CANCELLED')
            """)
    Page<StudentExamAssignmentResponse> findStudentHistory(@Param("studentId") UUID studentId, Pageable pageable);

    @Query("""
            select new com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse(
                a.assignmentId, a.scheduledTime, a.status, s.sessionId, s.sessionName, s.status,
                s.timeLimitMinutes, s.maxMainQuestions, s.maxFollowupPerQuestion, v.attemptId, v.status, t.topicName, a.slotOrder)
            from StudentSessionAssignment a
            join a.examSession s
            left join s.topic t
            left join VivaAttempt v on v.assignment.assignmentId = a.assignmentId
            where a.assignmentId = :assignmentId and a.student.userId = :studentId
            """)
    Optional<StudentExamAssignmentResponse> findStudentScheduleById(
            @Param("assignmentId") UUID assignmentId, @Param("studentId") UUID studentId);
}
