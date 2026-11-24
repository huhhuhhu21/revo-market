
package com.revo.backend.order.dto;

import com.revo.backend.order.entity.ReturnStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ReturnRequestResponse(
        Long id,
        Long orderId,
        Long buyerId,

        String reason,
        List<String> evidenceImages,
        ReturnStatus status,

        OffsetDateTime createdAt,
        Long processedBy,
        OffsetDateTime processedAt,
        String adminReason,

        OffsetDateTime returnDeadline,
        OffsetDateTime handedOverAt,
        OffsetDateTime returnedAt,

        boolean canApprove,
        boolean canReject,
        boolean canRecordHandover,
        boolean canRecordReceipt
) {}
