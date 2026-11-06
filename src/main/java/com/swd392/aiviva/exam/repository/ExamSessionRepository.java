package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.ExamSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
}
