package com.swd392.aiviva.exam.attempt.repository;

import com.swd392.aiviva.exam.attempt.entity.ExamStudentResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

public interface ExamStudentResultRepository extends JpaRepository<ExamStudentResult, UUID> {
    Optional<ExamStudentResult> findByExamSession_SessionIdAndStudent_UserId(UUID examId, UUID studentId);
    @EntityGraph(attributePaths = {"student"})
    List<ExamStudentResult> findByExamSession_SessionId(UUID examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExamStudentResult> findForUpdateByExamSession_SessionIdAndStudent_UserId(UUID examId, UUID studentId);
}
