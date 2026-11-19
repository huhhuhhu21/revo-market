
package com.revo.backend.common.api;

import java.util.List;

public record ApiErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<FieldErrorDetail> details,
            String requestId
    ) {}

    public record FieldErrorDetail(
            String field,
            String message
    ) {}

    public static ApiErrorResponse of(
            String code,
            String message,
            List<FieldErrorDetail> details,
            String requestId
    ) {
        return new ApiErrorResponse(
                new ErrorBody(
                        code,
                        message,
                        List.copyOf(details),
                        requestId
                )
        );
    }
}
