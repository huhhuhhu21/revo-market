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

    public static Notification returnApproved(
            User recipient,
            Long returnId,
            boolean isBuyer
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = "RETURN_APPROVED";

        notification.content = isBuyer
                ? "Yêu cầu trả hàng đã được phê duyệt. "
                + "Vui lòng bàn giao hàng trong 48 giờ."
                : "Yêu cầu trả hàng của đơn hàng "
                + "đã được ADMIN phê duyệt.";

        notification.refType = "RETURN_REQUEST";
        notification.refId = returnId;

        return notification;
    }

    public static Notification returnHandoverRecorded(
            User recipient,
            Long returnId,
            boolean isBuyer
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = "RETURN_HANDOVER_RECORDED";

        notification.content = isBuyer
                ? "Đã ghi nhận bạn bàn giao hàng trả. "
                + "Hàng đang được chuyển về người bán."
                : "Người mua đã bàn giao hàng trả. "
                + "Hàng đang được chuyển hoàn.";

        notification.refType = "RETURN_REQUEST";
        notification.refId = returnId;

        return notification;
    }

    public static Notification codRefunded(
            User buyer,
            Long orderId
    ) {
        Notification notification = new Notification();

        notification.user = buyer;
        notification.type = "COD_REFUNDED";
        notification.content =
                "Đơn hàng đã hoàn tất trả hàng. "
                        + "COD đã được hoàn cho bạn (mô phỏng).";

        notification.refType = "ORDER";
        notification.refId = orderId;

        return notification;
    }

    public static Notification returnRejected(
            User recipient,
            Long returnId,
            boolean isBuyer
    ) {
        Notification notification = new Notification();

        notification.user = recipient;
        notification.type = "RETURN_REJECTED";

        notification.content = isBuyer
                ? "Yêu cầu trả hàng của bạn đã bị từ chối. "
                + "Vui lòng xem lý do trong chi tiết yêu cầu."
                : "ADMIN đã từ chối yêu cầu trả hàng "
                + "của người mua.";

        notification.refType = "RETURN_REQUEST";
        notification.refId = returnId;

        return notification;
    }

}
