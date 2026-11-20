package com.revo.backend.media.dto;

import java.time.OffsetDateTime;

public record MediaUploadResponse(
        String uploadToken,
        String previewUrl,
        String mimeType,
        long sizeBytes,
        OffsetDateTime expiresAt
) {}