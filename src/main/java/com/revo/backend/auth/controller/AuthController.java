package com.revo.backend.auth.controller;


import com.revo.backend.auth.dto.*;

import com.revo.backend.auth.service.AuthService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/register/verify")
    public VerifyRegistrationResponse verifyRegistration(
            @Valid @RequestBody VerifyRegistrationRequest request
    ) {
        return authService.verifyRegistration(request);
    }

    @PostMapping("/register/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResendRegistrationResponse resendRegistration(
            @Valid @RequestBody ResendRegistrationRequest request
    ) {
        return authService.resendRegistration(request);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthSessionResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }


    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.parseLong(
                jwt.getSubject()
        );

        Long sessionId = Long.parseLong(
                jwt.getClaimAsString("sid")
        );

        authService.logout(userId, sessionId);
    }


    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
    }



}
