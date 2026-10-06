package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.VivaAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VivaAttemptRepository extends JpaRepository<VivaAttempt, UUID> {
    Optional<VivaAttempt> findByAssignment_AssignmentId(UUID assignmentId);
}
