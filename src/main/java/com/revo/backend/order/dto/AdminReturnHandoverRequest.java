
package com.revo.backend.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AdminReturnHandoverRequest(
        @NotNull OffsetDateTime actualAt,
        @Size(max = 500) String note
) {}
