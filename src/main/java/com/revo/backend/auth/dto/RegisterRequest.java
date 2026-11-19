
package com.revo.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Họ tên không được để trống.")
        @Size(max = 120, message = "Họ tên tối đa 120 ký tự.")
        String fullName,

        @NotBlank(message = "Loại liên hệ không được để trống.")
        @Pattern(
                regexp = "EMAIL|PHONE",
                message = "Loại liên hệ phải là EMAIL hoặc PHONE."
        )
        String contactType,

        @NotBlank(message = "Thông tin liên hệ không được để trống.")
        @Size(max = 254, message = "Thông tin liên hệ quá dài.")
        String contact,

        @NotBlank(message = "Mật khẩu không được để trống.")
        @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự.")
        String password

) {}
