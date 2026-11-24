
package com.revo.backend.order.entity;

import com.revo.backend.auth.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_exceptions")
public class OrderException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "exception_type", nullable = false)
    private String exceptionType;

    @Column(name = "description", nullable = false,
            columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderException() {}

    public static OrderException lateDelivery(
            Order order,
            User admin,
            LocalDateTime actualAt,
            LocalDateTime recordedAt
    ) {
        OrderException exception = new OrderException();

        exception.order = order;
        exception.createdBy = admin;
        exception.exceptionType = "DELIVERY";
        exception.status = "PENDING";

        exception.description =
                "ADMIN ghi nhận giao hàng quá muộn. "
                        + "Thời điểm giao thực tế (UTC): "
                        + actualAt;

        exception.createdAt = recordedAt;

        return exception;
    }

    public Long getId() {
        return id;
    }

    public static OrderException lateReturnHandover(
            Order order,
            User admin,
            Long returnId,
            LocalDateTime actualAt,
            LocalDateTime recordedAt
    ) {
        OrderException exception = new OrderException();

        exception.order = order;
        exception.createdBy = admin;
        exception.exceptionType = "RETURN";
        exception.status = "PENDING";

        exception.description =
                "Cần xác minh ghi nhận bàn giao hàng trả. "
                        + "Return ID: " + returnId
                        + ". Thời điểm bàn giao thực tế (UTC): "
                        + actualAt
                        + ". Thời điểm ADMIN ghi nhận (UTC): "
                        + recordedAt;

        exception.createdAt = recordedAt;

        return exception;
    }

    public static OrderException codAlreadySettled(
            Order order,
            User admin,
            Long returnId,
            LocalDateTime recordedAt
    ) {
        OrderException exception = new OrderException();

        exception.order = order;
        exception.createdBy = admin;
        exception.exceptionType = "COD_ORDER";
        exception.status = "PENDING";

        exception.description =
                "D11_COD_ALREADY_SETTLED"
                        + " | Return ID: " + returnId
                        + " | COD đã quyết toán, không thể hoàn lại "
                        + "bằng luồng D11 thông thường.";

        exception.createdAt = recordedAt;

        return exception;
    }

}
