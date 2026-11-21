
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Email hoặc số điện thoại không được để trống.")
        @Size(max = 254)
        String login,

        @NotBlank(message = "OTP không được để trống.")
        @Pattern(
                regexp = "\\d{6}",
                message = "OTP phải gồm đúng 6 chữ số."
        )
        String otp,

        @NotBlank(message = "Mật khẩu mới không được để trống.")
        @Size(
                min = 8,
                message = "Mật khẩu phải có ít nhất 8 ký tự."
        )
        String newPassword

) {}
