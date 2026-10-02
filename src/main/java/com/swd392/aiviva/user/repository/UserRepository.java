package com.swd392.aiviva.user.repository;

import com.swd392.aiviva.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.studentOrStaffCode = :code")
    Optional<User> findByStudentOrStaffCode(@Param("code") String code);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.studentOrStaffCode = :code")
    boolean existsByStudentOrStaffCode(@Param("code") String code);

    @Query("SELECT u.studentOrStaffCode FROM User u WHERE u.studentOrStaffCode LIKE CONCAT(:prefix, '%')")
    List<String> findAllCodesByPrefix(@Param("prefix") String prefix);
}
