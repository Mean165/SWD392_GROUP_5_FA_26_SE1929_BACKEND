package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.request.RegisterRequest;
import com.swd392.aiviva.auth.dto.response.LoginResponse;
import com.swd392.aiviva.user.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(RefreshTokenRequest request);

    UserResponse getCurrentUser();

    void logout();
}
