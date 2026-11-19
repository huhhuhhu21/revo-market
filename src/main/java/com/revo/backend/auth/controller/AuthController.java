package com.revo.backend.auth.controller;


import com.revo.backend.auth.dto.RegisterRequest;
import com.revo.backend.auth.dto.RegisterResponse;

import com.revo.backend.auth.service.AuthService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.revo.backend.auth.dto.VerifyRegistrationRequest;
import com.revo.backend.auth.dto.VerifyRegistrationResponse;

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
}
