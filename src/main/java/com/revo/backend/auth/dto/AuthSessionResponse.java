
package com.revo.backend.auth.dto;

public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        UserInfo user
) {
    public record UserInfo(
            Long id,
            String role,
            String status,
            String fullName,
            boolean emailVerified,
            boolean phoneVerified
    ) {}
}
