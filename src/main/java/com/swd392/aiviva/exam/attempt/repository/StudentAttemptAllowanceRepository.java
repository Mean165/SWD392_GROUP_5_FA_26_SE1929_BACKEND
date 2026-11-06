package com.swd392.aiviva.exam.attempt.repository;

import com.swd392.aiviva.exam.attempt.entity.StudentAttemptAllowance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

public interface StudentAttemptAllowanceRepository extends JpaRepository<StudentAttemptAllowance, UUID> {
    Optional<StudentAttemptAllowance> findByExamSession_SessionIdAndStudent_UserId(UUID examId, UUID studentId);
    @EntityGraph(attributePaths = {"student"})
    java.util.List<StudentAttemptAllowance> findByExamSession_SessionId(UUID examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentAttemptAllowance> findForUpdateByExamSession_SessionIdAndStudent_UserId(UUID examId, UUID studentId);
}
