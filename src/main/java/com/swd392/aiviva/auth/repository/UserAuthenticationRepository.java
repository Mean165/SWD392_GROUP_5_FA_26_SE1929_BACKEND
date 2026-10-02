package com.swd392.aiviva.auth.repository;

import com.swd392.aiviva.auth.entity.UserAuthentication;
import com.swd392.aiviva.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuthenticationRepository extends JpaRepository<UserAuthentication, Long> {
    Optional<UserAuthentication> findByUser(User user);
    Optional<UserAuthentication> findByUsername(String username);
}
