
package com.revo.backend.order.dto;

import java.util.List;

public record AdminReturnPageResponse(
        List<ReturnRequestResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {}
