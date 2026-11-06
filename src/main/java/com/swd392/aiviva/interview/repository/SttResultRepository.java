package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.SttResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SttResultRepository extends JpaRepository<SttResultEntity, UUID> {
    Optional<SttResultEntity> findByTurn_TurnId(UUID turnId);
}
