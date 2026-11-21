
package com.revo.backend.auth.dto;

import java.time.Instant;

public record PrivateUserResponse(
        Long id,
        String role,
        String status,
        String fullName,
        String email,
        String phone,
        boolean emailVerified,
        boolean phoneVerified,
        String avatarUrl,
        String addressLine,
        String province,
        String ward,
        Instant createdAt,
        Instant updatedAt
) {}
