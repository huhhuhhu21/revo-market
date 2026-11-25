
package com.revo.backend.order.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectReturnRequest(
        @NotBlank String reason
) {}
