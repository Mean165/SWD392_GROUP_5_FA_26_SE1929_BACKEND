package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.Rubric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {
    List<Rubric> findBySubject_SubjectIdOrderByUpdatedAtDesc(UUID subjectId);
    List<Rubric> findBySubject_SubjectIdAndStatusOrderByUpdatedAtDesc(UUID subjectId, String status);
    Optional<Rubric> findFirstBySubject_SubjectIdAndStatusOrderByUpdatedAtDesc(UUID subjectId, String status);
    boolean existsBySubject_SubjectIdAndStatus(UUID subjectId, String status);
}
