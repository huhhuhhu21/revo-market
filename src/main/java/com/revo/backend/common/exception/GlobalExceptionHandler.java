
package com.revo.backend.common.exception;

import com.revo.backend.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();

        ApiErrorResponse response = ApiErrorResponse.of(
                errorCode.name(),
                ex.getMessage(),
                List.of(),
                getRequestId(request)
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldErrorDetail> details =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new ApiErrorResponse.FieldErrorDetail(
                                error.getField(),
                                error.getDefaultMessage() == null
                                        ? "Giá trị không hợp lệ."
                                        : error.getDefaultMessage()
                        ))
                        .toList();

        return buildValidationResponse(details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildValidationResponse(List.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> buildValidationResponse(
            List<ApiErrorResponse.FieldErrorDetail> details,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        ApiErrorResponse response = ApiErrorResponse.of(
                errorCode.name(),
                errorCode.getDefaultMessage(),
                details,
                getRequestId(request)
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    private String getRequestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");

        if (value instanceof String id && !id.isBlank()) {
            return id;
        }

        String id = "req-" + UUID.randomUUID();
        request.setAttribute("requestId", id);
        return id;
    }
}
