
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
}
