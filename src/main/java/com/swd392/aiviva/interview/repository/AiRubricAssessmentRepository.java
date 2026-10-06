package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.AiRubricAssessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRubricAssessmentRepository extends JpaRepository<AiRubricAssessment, UUID> {
    List<AiRubricAssessment> findByTurn_TurnId(UUID turnId);
}
