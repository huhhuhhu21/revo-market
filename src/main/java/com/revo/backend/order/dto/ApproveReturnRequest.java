
package com.revo.backend.order.dto;

import jakarta.validation.constraints.Size;

public record ApproveReturnRequest(
        @Size(max = 2000)
        String note
) {}
