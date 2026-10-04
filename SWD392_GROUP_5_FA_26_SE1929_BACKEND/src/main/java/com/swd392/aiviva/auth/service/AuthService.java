package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(RefreshTokenRequest request);
}

