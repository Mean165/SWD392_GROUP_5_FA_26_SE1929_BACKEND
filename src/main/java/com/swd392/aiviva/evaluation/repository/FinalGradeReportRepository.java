package com.swd392.aiviva.evaluation.repository;

import com.swd392.aiviva.evaluation.entity.FinalGradeReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinalGradeReportRepository extends JpaRepository<FinalGradeReport, UUID> {
    Optional<FinalGradeReport> findByAttempt_AttemptId(UUID attemptId);
}
