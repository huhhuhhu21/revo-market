
package com.revo.backend.auth.dto;

public record ResendRegistrationResponse(
        RegisterResponse.Verification verification
) {}
