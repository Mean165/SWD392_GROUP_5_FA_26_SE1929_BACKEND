package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.InteractionTurn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InteractionTurnRepository extends JpaRepository<InteractionTurn, UUID> {
    List<InteractionTurn> findByAttempt_AttemptIdOrderByTurnOrderAsc(UUID attemptId);
}
