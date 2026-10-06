package com.swd392.aiviva.exam.repository;

import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentSessionAssignmentRepository extends JpaRepository<StudentSessionAssignment, UUID> {
    List<StudentSessionAssignment> findBySession_SessionId(UUID sessionId);
    List<StudentSessionAssignment> findByStudent_UserId(UUID studentId);
}
