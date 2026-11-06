package com.swd392.aiviva.exam.attempt.repository;

import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamAttemptSettingsRepository extends JpaRepository<ExamAttemptSettings, UUID> { }
