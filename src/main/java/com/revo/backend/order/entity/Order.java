package com.revo.backend.order.entity;

import com.revo.backend.auth.entity.User;
import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.purchase.entity.PurchaseRequest;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ProductListing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "transaction_price", nullable = false)
    private Long transactionPrice;

    @Column(name = "shipping_fee", nullable = false)
    private Long shippingFee;

    @Column(name = "cod_amount", nullable = false)
    private Long codAmount;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_status", nullable = false, length = 24)
    private CodStatus codStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    private User canceledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cod_collected_at")
    private LocalDateTime codCollectedAt;

    @Column(name = "cod_settled_at")
    private LocalDateTime codSettledAt;

    @Column(name = "cod_refunded_at")
    private LocalDateTime codRefundedAt;

    protected Order() {}

    public static Order fromAcceptedRequest(
            PurchaseRequest request,
            User seller,
            LocalDateTime now
    ) {
        Order order = new Order();

        order.purchaseRequest = request;
        order.listing = request.getListing();
        order.buyer = request.getBuyer();
        order.seller = seller;

        order.transactionPrice = request.getOfferedPrice();
        order.shippingFee = 0L;
        order.codAmount = request.getOfferedPrice();

        // Snapshot giao nhận, không lấy động từ hồ sơ.
        order.recipientName = request.getRecipientName();
        order.recipientPhone = request.getRecipientPhone();
        order.deliveryAddressLine =
                request.getDeliveryAddressLine();
        order.deliveryProvince =
                request.getDeliveryProvince();
        order.deliveryWard =
                request.getDeliveryWard();

        order.status = OrderStatus.PENDING_PICKUP;
        order.codStatus = CodStatus.NOT_COLLECTED;

        order.createdAt = now;
        order.updatedAt = now;

        return order;
    }

    public Long getId() {
        return id;
    }

    public PurchaseRequest getPurchaseRequest() {
        return purchaseRequest;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CodStatus getCodStatus() {
        return codStatus;
    }

    public Long getTransactionPrice() {
        return transactionPrice;
    }

    public Long getShippingFee() {
        return shippingFee;
    }

    public Long getCodAmount() {
        return codAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ProductListing getListing() {
        return listing;
    }

    public User getBuyer() {
        return buyer;
    }

    public User getSeller() {
        return seller;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public User getCanceledBy() {
        return canceledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public LocalDateTime getCodCollectedAt() {
        return codCollectedAt;
    }

    public LocalDateTime getCodSettledAt() {
        return codSettledAt;
    }

    public LocalDateTime getCodRefundedAt() {
        return codRefundedAt;
    }
    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getDeliveryAddressLine() {
        return deliveryAddressLine;
    }

    public String getDeliveryProvince() {
        return deliveryProvince;
    }

    public String getDeliveryWard() {
        return deliveryWard;
    }


    public void cancelBeforePickup(
            User actor,
            String reason,
            LocalDateTime now
    ) {
        if (status != OrderStatus.PENDING_PICKUP
                || pickedUpAt != null
                || codStatus != CodStatus.NOT_COLLECTED) {
            throw new IllegalStateException(
                    "Order cannot be canceled before pickup"
            );
        }

        this.status = OrderStatus.CANCELED;
        this.codStatus = CodStatus.NO_COLLECTION;
        this.canceledBy = actor;
        this.cancelReason = reason;
        this.canceledAt = now;
        this.updatedAt = now;
    }


    public void recordPickedUp(
            LocalDateTime actualAt,
            LocalDateTime recordedAt
    ) {
        if (status != OrderStatus.PENDING_PICKUP
                || pickedUpAt != null
                || codStatus != CodStatus.NOT_COLLECTED) {
            throw new IllegalStateException(
                    "Order is not ready for pickup"
            );
        }

        this.pickedUpAt = actualAt;
        this.status = OrderStatus.IN_TRANSIT;
        this.updatedAt = recordedAt;

        // COD chưa được thu tại bước tiếp nhận.
        // Không thay đổi codStatus hoặc codAmount.
    }

    public void recordDelivered(
            LocalDateTime actualAt,
            LocalDateTime recordedAt
    ) {
        if (status != OrderStatus.IN_TRANSIT
                || pickedUpAt == null
                || deliveredAt != null
                || codStatus != CodStatus.NOT_COLLECTED
                || codCollectedAt != null) {
            throw new IllegalStateException(
                    "Order is not ready for delivery"
            );
        }

        this.deliveredAt = actualAt;
        this.codCollectedAt = recordedAt;
        this.status = OrderStatus.DELIVERED;
        this.codStatus = CodStatus.HELD;
        this.updatedAt = recordedAt;

        // Giữ nguyên codAmount và transactionPrice.
    }


    public void finishReturnAndRefund(LocalDateTime recordedAt) {
        if ((status != OrderStatus.DELIVERED
                && status != OrderStatus.COMPLETED)
                || codStatus != CodStatus.HELD
                || codCollectedAt == null
                || codSettledAt != null
                || codRefundedAt != null) {
            throw new IllegalStateException(
                    "Order is not eligible for COD refund"
            );
        }

        this.status = OrderStatus.RETURNED;
        this.codStatus = CodStatus.REFUNDED;
        this.codRefundedAt = recordedAt;
        this.updatedAt = recordedAt;

        // Không thay đổi codAmount, transactionPrice.
        // Không xóa thời điểm giao hàng hoặc hoàn thành cũ.
    }


}