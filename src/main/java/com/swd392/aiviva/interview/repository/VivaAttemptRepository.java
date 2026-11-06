package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.VivaAttempt;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaAttemptRepository extends JpaRepository<VivaAttempt, UUID> {
    Optional<VivaAttempt> findByAssignment_AssignmentId(UUID assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VivaAttempt a join fetch a.assignment ass "
            + "join fetch ass.examSession join fetch ass.student where a.attemptId = :attemptId")
    Optional<VivaAttempt> findByIdForUpdate(@Param("attemptId") UUID attemptId);
}
