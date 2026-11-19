
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(

        @NotBlank(message = "Refresh Token không được để trống.")
        @Size(
                max = 512,
                message = "Refresh Token không hợp lệ."
        )
        String refreshToken

) {}
