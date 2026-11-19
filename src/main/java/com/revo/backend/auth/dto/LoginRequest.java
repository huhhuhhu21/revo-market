
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Thông tin đăng nhập không được trống.")
        String login,

        @NotBlank(message = "Mật khẩu không được trống.")
        String password

) {}
