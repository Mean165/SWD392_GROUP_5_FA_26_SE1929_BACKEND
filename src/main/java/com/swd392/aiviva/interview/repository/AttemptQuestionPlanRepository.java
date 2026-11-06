package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.AttemptQuestionPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptQuestionPlanRepository extends JpaRepository<AttemptQuestionPlan, UUID> {
    List<AttemptQuestionPlan> findByAttempt_AttemptIdOrderByPositionAsc(UUID attemptId);
    boolean existsByAttempt_AttemptId(UUID attemptId);
    long countByAttempt_AttemptId(UUID attemptId);
    @Query("select p from AttemptQuestionPlan p join fetch p.question where p.attempt.attemptId = :attemptId and p.position = :position")
    java.util.Optional<AttemptQuestionPlan> findByAttemptAndPosition(@Param("attemptId") UUID attemptId, @Param("position") int position);
}
