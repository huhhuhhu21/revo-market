
package com.revo.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

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
