package com.revo.backend.auth.controller;


import com.revo.backend.auth.dto.*;

import com.revo.backend.auth.service.AuthService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}
