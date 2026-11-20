
package com.revo.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {



    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "Thông tin đăng nhập không chính xác."
    ),

    ACCOUNT_NOT_VERIFIED(
            HttpStatus.FORBIDDEN,
            "Tài khoản chưa được xác minh."
    ),

    LOGIN_TEMPORARILY_LOCKED(
            HttpStatus.TOO_MANY_REQUESTS,
            "Đăng nhập tạm thời bị khóa. Vui lòng thử lại sau."
    ),

    REFRESH_TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "Refresh Token không hợp lệ."
    ),

    REFRESH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "Refresh Token đã hết hạn."
    ),

    SESSION_REVOKED(
            HttpStatus.UNAUTHORIZED,
            "Phiên đăng nhập đã bị thu hồi."
    ),

    REFRESH_TOKEN_ALREADY_ROTATED(
            HttpStatus.CONFLICT,
            "Refresh Token đã được thay thế bởi yêu cầu khác."
    ),

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

    OTP_RESEND_TOO_SOON(
            HttpStatus.TOO_MANY_REQUESTS,
            "Vui lòng đợi ít nhất 60 giây trước khi gửi lại OTP."
    ),

    ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy tài khoản."
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
    ),

    INVALID_FILTER(
            HttpStatus.BAD_REQUEST,
            "Bộ lọc tìm kiếm không hợp lệ."
    ),

    INVALID_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "Tham số phân trang không hợp lệ."
    ),

    INVALID_PRICE_RANGE(
            HttpStatus.BAD_REQUEST,
            "Khoảng giá không hợp lệ."
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
