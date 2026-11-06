package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.ExamSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSession s where s.sessionId = :id")
    java.util.Optional<ExamSession> findByIdForUpdate(@Param("id") UUID id);
}
