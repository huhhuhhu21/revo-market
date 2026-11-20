package com.revo.backend.purchase.dto;

import com.revo.backend.purchase.entity.PurchaseRequest;
import com.revo.backend.purchase.entity.PurchaseRequestStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
public record PurchaseRequestResponse(
        Long id,
        ListingRef listing,
        Party buyer,
        Party seller,
        Long offeredPrice,
        String note,
        PurchaseRequestStatus status,
        DeliveryContact deliveryContact,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime respondedAt,
        Long orderId,
        boolean canAccept,
        boolean canReject,
        boolean canWithdraw
) {
    public record ListingRef(Long id, String currentTitle, boolean detailAccessible) {}
    public record Party(Long id, String fullName) {}
    public record DeliveryContact(
            String recipientName,
            String recipientPhone,
            String deliveryAddressLine,
            String deliveryProvince,
            String deliveryWard
    ) {}

    public static PurchaseRequestResponse fromCreated(PurchaseRequest request) {
        var listing = request.getListing();
        var buyer = request.getBuyer();
        var seller = listing.getSeller();
        return new PurchaseRequestResponse(
                request.getId(),
                new ListingRef(listing.getId(), listing.getTitle(), true),
                new Party(buyer.getId(), buyer.getFullName()),
                new Party(seller.getId(), seller.getFullName()),
                request.getOfferedPrice(),
                request.getNote(),
                request.getStatus(),
                new DeliveryContact(
                        request.getRecipientName(),
                        request.getRecipientPhone(),
                        request.getDeliveryAddressLine(),
                        request.getDeliveryProvince(),
                        request.getDeliveryWard()
                ),
                request.getCreatedAt().atOffset(ZoneOffset.UTC),
                request.getExpiresAt().atOffset(ZoneOffset.UTC),
                request.getRespondedAt() == null
                        ? null : request.getRespondedAt().atOffset(ZoneOffset.UTC),
                null,   // C05 chưa tạo đơn.
                false,  // Người gửi C01 là BUYER, không có quyền accept.
                false,
                true    // Có thể rút khi đang PENDING và chưa hết hạn.
        );
    }

}
