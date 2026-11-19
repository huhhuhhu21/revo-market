
package com.revo.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    OTP_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mã xác minh không hợp lệ."
    ),

    OTP_EXPIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mã xác minh đã hết hạn."
    ),

    OTP_ALREADY_USED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mã xác minh đã được sử dụng."
    ),

    OTP_ATTEMPTS_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "Bạn đã nhập sai mã xác minh quá số lần cho phép."
    ),

    ACCOUNT_ALREADY_VERIFIED(
            HttpStatus.CONFLICT,
            "Tài khoản đã được xác minh."
    ),

    CONTACT_ALREADY_USED(
            HttpStatus.CONFLICT,
            "Email hoặc số điện thoại đã được sử dụng."
    ),

    OTP_DELIVERY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa thể gửi mã xác minh. Vui lòng thử lại."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Bạn cần đăng nhập để thực hiện thao tác này."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Bạn không có quyền thực hiện thao tác này."
    ),

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Dữ liệu không hợp lệ."
    ),

    LISTING_LOCKED(
            HttpStatus.CONFLICT,
            "Bài đăng đang có giao dịch chưa hoàn tất."
    ),

    CATEGORY_INACTIVE(
            HttpStatus.CONFLICT,
            "Danh mục đã ngừng sử dụng."
    );

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }


}
