package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.Rubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RubricRepository extends JpaRepository<Rubric, Long> {
}

