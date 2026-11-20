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
}