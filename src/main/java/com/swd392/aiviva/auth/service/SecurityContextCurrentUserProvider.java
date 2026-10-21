package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.security.JwtAuthenticationDetails;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return authentication;
    }

    @Override
    public UUID currentUserId() {
        Object details = authentication().getDetails();
        if (details instanceof JwtAuthenticationDetails jwtDetails && jwtDetails.getUserId() != null) {
            return jwtDetails.getUserId();
        }
        throw new AccessDeniedException("Authenticated user identity is unavailable");
    }

    @Override
    public boolean isAdmin() {
        return authentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AD"));
    }
}
