package com.revo.backend.purchase.entity;

import com.revo.backend.auth.entity.User;
import com.revo.backend.listing.entity.ProductListing;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ProductListing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "offered_price", nullable = false)
    private Long offeredPrice;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "delivery_address_line", nullable = false, length = 255)
    private String deliveryAddressLine;

    @Column(name = "delivery_province", nullable = false, length = 100)
    private String deliveryProvince;

    @Column(name = "delivery_ward", nullable = false, length = 100)
    private String deliveryWard;

    @Column(name = "note", length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PurchaseRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // pending_buyer_id là generated column của MySQL, không ánh xạ để ghi.
    protected PurchaseRequest() {}

    public static PurchaseRequest create(
            ProductListing listing,
            User buyer,
            long offeredPrice,
            String recipientName,
            String recipientPhone,
            String deliveryAddressLine,
            String deliveryProvince,
            String deliveryWard,
            String note,
            LocalDateTime createdAt
    ) {
        PurchaseRequest request = new PurchaseRequest();
        request.listing = listing;
        request.buyer = buyer;
        request.offeredPrice = offeredPrice;
        request.recipientName = recipientName;
        request.recipientPhone = recipientPhone;
        request.deliveryAddressLine = deliveryAddressLine;
        request.deliveryProvince = deliveryProvince;
        request.deliveryWard = deliveryWard;
        request.note = note;
        request.status = PurchaseRequestStatus.PENDING;
        request.createdAt = createdAt;
        request.expiresAt = createdAt.plusHours(48);
        return request;
    }

    public Long getId() { return id; }
    public ProductListing getListing() { return listing; }
    public User getBuyer() { return buyer; }
    public Long getOfferedPrice() { return offeredPrice; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getDeliveryAddressLine() { return deliveryAddressLine; }
    public String getDeliveryProvince() { return deliveryProvince; }
    public String getDeliveryWard() { return deliveryWard; }
    public String getNote() { return note; }
    public PurchaseRequestStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public void accept(LocalDateTime now) {
        if (this.status != PurchaseRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Purchase request is not pending"
            );
        }

        this.status = PurchaseRequestStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void reject(LocalDateTime now) {
        if (this.status != PurchaseRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Purchase request is not pending"
            );
        }

        this.status = PurchaseRequestStatus.REJECTED;
        this.respondedAt = now;
    }


    public void withdraw() {
        if (this.status != PurchaseRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Purchase request is not pending"
            );
        }

        this.status = PurchaseRequestStatus.ENDED;
    }

    public void expire() {
        if (this.status != PurchaseRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Purchase request is not pending"
            );
        }

        this.status = PurchaseRequestStatus.EXPIRED;
    }

}
