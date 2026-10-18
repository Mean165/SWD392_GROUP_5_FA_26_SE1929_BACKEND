package com.swd392.aiviva.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class JwtAuthenticationDetails extends WebAuthenticationDetails {

    private final UUID userId;

    public JwtAuthenticationDetails(HttpServletRequest request, UUID userId) {
        super(request);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
