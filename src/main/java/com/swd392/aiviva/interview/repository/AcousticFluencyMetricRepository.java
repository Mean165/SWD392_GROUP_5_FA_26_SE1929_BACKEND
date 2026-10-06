package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.AcousticFluencyMetric;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcousticFluencyMetricRepository extends JpaRepository<AcousticFluencyMetric, UUID> {
    Optional<AcousticFluencyMetric> findByTurn_TurnId(UUID turnId);
}
