
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record VerifyRegistrationRequest(

        @NotNull(message = "User ID không được để trống.")
        @Positive(message = "User ID phải là số dương.")
        Long userId,

        @NotBlank(message = "OTP không được để trống.")
        @Pattern(
                regexp = "\\d{6}",
                message = "OTP phải gồm đúng 6 chữ số."
        )
        String otp

) {}
