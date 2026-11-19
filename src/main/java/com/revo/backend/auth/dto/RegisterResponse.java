
package com.revo.backend.auth.dto;

public record RegisterResponse(
        Long userId,
        String status,
        Verification verification
) {
    public record Verification(
            String channel,
            String maskedTarget,
            int expiresInSeconds,
            int resendAfterSeconds
    ) {}
}
