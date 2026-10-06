package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentSessionAssignmentRepository extends JpaRepository<StudentSessionAssignment, UUID> {

    boolean existsByExamSessionSessionIdAndStudentUserId(UUID sessionId, UUID studentId);

    Optional<StudentSessionAssignment> findByExamSessionSessionIdAndStudentUserId(UUID sessionId, UUID studentId);

    List<StudentSessionAssignment> findByExamSessionSessionId(UUID sessionId);

    List<StudentSessionAssignment> findByStudentUserId(UUID studentId);
}
