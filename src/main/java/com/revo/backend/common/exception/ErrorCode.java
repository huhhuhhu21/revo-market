
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
    ),

    INVALID_LISTING_ID(
            HttpStatus.BAD_REQUEST,
        "Mã bài đăng không hợp lệ."
    ),

    LISTING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
        "Không tìm thấy bài đăng."
    ),

    INVALID_IMAGE(
            HttpStatus.BAD_REQUEST,
        "Hình ảnh không hợp lệ."
    ),

    IMAGE_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
        "Hình ảnh vượt quá dung lượng cho phép."
    ),

    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Định dạng hình ảnh không được hỗ trợ."
    ),

    MEDIA_STORAGE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
        "Không thể lưu hình ảnh. Vui lòng thử lại."
    ),

    ACCOUNT_RESTRICTED(
            HttpStatus.FORBIDDEN,
        "Tài khoản đang bị hạn chế thực hiện thao tác này."
    ),

    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
        "Danh mục không tồn tại."
    ),

    INVALID_IMAGE_SET(
            HttpStatus.UNPROCESSABLE_CONTENT,
        "Danh sách hình ảnh không hợp lệ."
    ),

    UPLOAD_TOKEN_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
        "Upload token không hợp lệ hoặc đã được sử dụng."
    ),

    UPLOAD_TOKEN_EXPIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
        "Upload token đã hết hạn."
    ),


    NOT_LISTING_OWNER(
            HttpStatus.FORBIDDEN,
            "Bạn không phải chủ sở hữu bài đăng."
    ),


    LISTING_ADMIN_HIDDEN(
            HttpStatus.CONFLICT,
            "Bài đăng đang bị quản trị viên ẩn."
    ),

    LISTING_NOT_EDITABLE(
            HttpStatus.CONFLICT,
            "Trạng thái bài đăng không cho phép chỉnh sửa."
    ),

    SOLD_LISTING_IMMUTABLE(
            HttpStatus.CONFLICT,
        "Bài đăng đã bán không thể xóa hoặc chỉnh sửa."
    ),
    INVALID_AI_INPUT(
            HttpStatus.BAD_REQUEST,
            "Dữ liệu yêu cầu gợi ý AI không hợp lệ."
    ),

    AI_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Dịch vụ AI tạm thời không khả dụng."
    ),

    AI_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT,
            "AI phản hồi quá thời gian cho phép."
    ),
    // C01 - Gửi yêu cầu mua

    LISTING_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "Bài đăng hiện không nhận yêu cầu mua."
    ),

    CANNOT_BUY_OWN_LISTING(
            HttpStatus.FORBIDDEN,
            "Bạn không thể mua bài đăng của chính mình."
    ),

    PURCHASE_REQUEST_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "Bạn đã có yêu cầu mua đang chờ cho bài này."
    ),

    DELIVERY_ADDRESS_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Vui lòng bổ sung địa chỉ nhận hàng hợp lệ."
    ),

    INVALID_DELIVERY_CONTACT(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Số điện thoại người nhận không hợp lệ."
    ),

    CONTACT_PHONE_REQUIRED(
            HttpStatus.FORBIDDEN,
            "Cần có số điện thoại liên hệ để giao dịch."
    ),

    INVALID_REQUEST_ID(
            HttpStatus.BAD_REQUEST,
            "ID yêu cầu mua không hợp lệ."
    ),

    PURCHASE_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy yêu cầu mua."
    ),

    PURCHASE_REQUEST_NOT_PENDING(
            HttpStatus.CONFLICT,
            "Yêu cầu mua không còn ở trạng thái chờ xác nhận."
    ),

    PURCHASE_REQUEST_EXPIRED(
            HttpStatus.CONFLICT,
            "Yêu cầu mua đã hết thời hạn xử lý."
    ),

    ORDER_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Sản phẩm đã có đơn hàng đang xử lý."
    ),

    PARTICIPANT_NOT_ELIGIBLE(
            HttpStatus.CONFLICT,
            "Người mua hoặc người bán không đủ điều kiện giao dịch."
    ),

    INVALID_REQUEST_BODY(
            HttpStatus.BAD_REQUEST,
            "Yêu cầu này không chấp nhận dữ liệu trong body."
    ),

    INVALID_ROLE_FILTER(
            HttpStatus.BAD_REQUEST,
            "Vai trò lọc đơn hàng không hợp lệ."
    ),

    INVALID_ORDER_ID(
            HttpStatus.BAD_REQUEST,
            "ID đơn hàng không hợp lệ."
    ),

    ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy đơn hàng."
    ),

    OTP_INVALID_OR_EXPIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mã xác minh không hợp lệ hoặc đã hết hạn."
    ),


    NEW_PASSWORD_SAME_AS_CURRENT(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mật khẩu mới không được trùng với mật khẩu hiện tại."
    ),



    CURRENT_PASSWORD_INCORRECT(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Mật khẩu hiện tại không chính xác."
    ),


    LISTING_NOT_HIDEABLE(
            HttpStatus.CONFLICT,
            "Trạng thái bài đăng không cho phép ẩn."
    ),

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy người dùng."
    ),

    LISTING_NOT_RESTORABLE(
            HttpStatus.CONFLICT,
            "Bài đăng chưa đủ điều kiện khôi phục."
    ),



    NOT_REQUEST_BUYER(
            HttpStatus.FORBIDDEN,
            "Bạn không phải người gửi yêu cầu mua này."
    ),

    PURCHASE_REQUEST_ALREADY_ACCEPTED(
            HttpStatus.CONFLICT,
            "Yêu cầu mua đã được chấp nhận. Vui lòng sử dụng chức năng hủy đơn hàng."
    ),


    INVALID_STATUS_FILTER(
            HttpStatus.BAD_REQUEST,
            "Trạng thái lọc không hợp lệ."
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
