package com.swd392.aiviva.auth.security;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;

public interface JwtService {

    String generateToken(String username);

    String generateToken(String username, Map<String, Object> extraClaims);

    String extractUsername(String token);

    String extractEmail(String token);

    UUID extractUserId(String token);

    Instant extractIssuedAt(String token);

    boolean isTokenValid(String token, String username);

    long getExpirationTime();
}
