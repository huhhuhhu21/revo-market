
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(

        @NotBlank(message = "Thông tin đăng nhập không được trống.")
        @Size(max = 254, message = "Thông tin đăng nhập quá dài.")
        String login

) {}
