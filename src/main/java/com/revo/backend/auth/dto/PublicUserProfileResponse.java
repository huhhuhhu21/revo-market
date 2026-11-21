
package com.revo.backend.auth.dto;

import java.time.Instant;

public record PublicUserProfileResponse(
        Long id,
        String fullName,
        String avatarUrl,
        Instant joinedAt
) {}
