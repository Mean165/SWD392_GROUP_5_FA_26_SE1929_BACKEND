package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.response.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO: Implement authentication logic and JWT generation.
        return new LoginResponse("placeholder-token", "Bearer", 3600L);
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        // TODO: Implement token refresh logic.
        return new LoginResponse("placeholder-refreshed-token", "Bearer", 3600L);
    }
}

