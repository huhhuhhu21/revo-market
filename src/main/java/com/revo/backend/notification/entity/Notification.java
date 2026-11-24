package com.revo.backend.notification.entity;

import com.revo.backend.auth.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 48)
    private String type;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public static Notification purchaseRequestReceived(User seller, Long requestId) {
        Notification notification = new Notification();
        notification.user = seller;
        notification.type = "PURCHASE_REQUEST_RECEIVED";
        notification.content = "Bạn có một yêu cầu mua mới.";
        notification.refType = "PURCHASE_REQUEST";
        notification.refId = requestId;
        return notification;
    }

    public static Notification purchaseRequestUpdated(
            User buyer,
            Long requestId,
            String content
    ) {
        Notification notification = new Notification();

        notification.user = buyer;
        notification.type = "PURCHASE_REQUEST_UPDATED";
        notification.content = content;
        notification.refType = "PURCHASE_REQUEST";
        notification.refId = requestId;

        return notification;
    }


    public static Notification orderCancellation(
            User recipient,
            Long orderId,
            boolean pending
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = pending
                ? "ORDER_CANCEL_REQUESTED"
                : "ORDER_CANCELED";

        notification.content = pending
                ? "Người mua đã yêu cầu hủy đơn. "
                + "Đang chờ chuyển hoàn."
                : "Đơn hàng đã được hủy.";

        notification.refType = "ORDER";
        notification.refId = orderId;

        return notification;
    }

    public static Notification orderDelivered(
            User recipient,
            Long orderId
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = "ORDER_DELIVERED";
        notification.content =
                "Đơn hàng đã được giao thành công.";

        notification.refType = "ORDER";
        notification.refId = orderId;

        return notification;
    }


    public static Notification returnRequestReceived(
            User recipient,
            Long returnId,
            boolean isAdmin
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = "RETURN_REQUEST_RECEIVED";
        notification.content = isAdmin
                ? "Có yêu cầu trả hàng mới cần xử lý."
                : "Người mua đã gửi yêu cầu trả hàng.";

        notification.refType = "RETURN_REQUEST";
        notification.refId = returnId;

        return notification;
    }

}
