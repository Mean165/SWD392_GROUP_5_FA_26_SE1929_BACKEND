package com.swd392.aiviva.user.repository;

import com.swd392.aiviva.user.entity.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmail(String email);

    @Query("SELECT u FROM AppUser u WHERE u.studentOrStaffCode = :studentOrStaffCode")
    Optional<AppUser> findByStudentOrStaffCode(@Param("studentOrStaffCode") String studentOrStaffCode);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM AppUser u WHERE u.studentOrStaffCode = :code")
    boolean existsByStudentOrStaffCode(@Param("code") String code);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM AppUser u WHERE u.email = :email OR u.studentOrStaffCode = :code")
    Optional<AppUser> findByEmailOrStudentOrStaffCode(@Param("email") String email, @Param("code") String code);
}
