package com.swd392.aiviva.auth.security;

import java.util.Map;

public interface JwtService {

    String generateToken(String username);

    String generateToken(String username, Map<String, Object> extraClaims);

    String extractUsername(String token);

    String extractEmail(String token);

    boolean isTokenValid(String token, String username);

    long getExpirationTime();
}
