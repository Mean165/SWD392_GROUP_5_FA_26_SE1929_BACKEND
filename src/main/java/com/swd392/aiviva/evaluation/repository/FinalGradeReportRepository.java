package com.swd392.aiviva.evaluation.repository;

import com.swd392.aiviva.evaluation.entity.FinalGradeReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface FinalGradeReportRepository extends JpaRepository<FinalGradeReport, UUID> {
    Optional<FinalGradeReport> findByAttempt_AttemptId(UUID attemptId);
    @EntityGraph(attributePaths = {"attempt"})
    java.util.List<FinalGradeReport> findByAttempt_AttemptIdIn(java.util.Collection<UUID> attemptIds);
}
