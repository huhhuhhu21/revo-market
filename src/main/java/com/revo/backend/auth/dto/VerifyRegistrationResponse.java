package com.revo.backend.auth.dto;

public record VerifyRegistrationResponse(
        boolean verified,
        String status
) {}