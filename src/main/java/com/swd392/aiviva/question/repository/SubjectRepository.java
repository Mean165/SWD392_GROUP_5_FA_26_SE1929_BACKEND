package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.Subject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    @Query(value = "select exists (select 1 from subject where subject_id = :subjectId "
            + "and assigned_lecturer_ids_json @> jsonb_build_array(cast(:lecturerId as text)))", nativeQuery = true)
    boolean isLecturerAssigned(@Param("subjectId") UUID subjectId, @Param("lecturerId") UUID lecturerId);
}
