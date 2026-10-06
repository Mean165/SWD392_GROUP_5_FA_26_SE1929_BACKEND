package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.AiClaimEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiClaimEvidenceRepository extends JpaRepository<AiClaimEvidence, UUID> {
    List<AiClaimEvidence> findByTurn_TurnId(UUID turnId);
    List<AiClaimEvidence> findByAssessment_AssessmentId(UUID assessmentId);
}
