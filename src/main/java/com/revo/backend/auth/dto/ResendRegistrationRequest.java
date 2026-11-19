
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResendRegistrationRequest(

        @NotNull(message = "User ID không được để trống.")
        @Positive(message = "User ID phải là số dương.")
        Long userId

) {}
