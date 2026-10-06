package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.RubricCriteria;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricCriteriaRepository extends JpaRepository<RubricCriteria, UUID> {
    List<RubricCriteria> findByQuestion_QuestionId(UUID questionId);
}
