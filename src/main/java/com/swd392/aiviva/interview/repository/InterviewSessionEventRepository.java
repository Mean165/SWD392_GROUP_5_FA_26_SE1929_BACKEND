package com.swd392.aiviva.interview.repository;

import com.swd392.aiviva.interview.entity.InterviewSessionEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionEventRepository extends JpaRepository<InterviewSessionEvent, UUID> {
    List<InterviewSessionEvent> findByAttempt_AttemptIdOrderBySeqAsc(UUID attemptId);
}
