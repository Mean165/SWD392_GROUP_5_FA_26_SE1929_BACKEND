package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.ExamSessionBloomDistribution;
import com.swd392.aiviva.question.enums.BloomLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSessionBloomDistributionRepository extends JpaRepository<ExamSessionBloomDistribution, ExamSessionBloomDistribution.Key> {
    List<ExamSessionBloomDistribution> findBySession_SessionId(UUID sessionId);
}
