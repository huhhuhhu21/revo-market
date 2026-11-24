
package com.revo.backend.order.entity;

import com.revo.backend.auth.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "reason", nullable = false,
            columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_images", nullable = false,
            columnDefinition = "json")
    private List<String> evidenceImages;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReturnStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "admin_reason", columnDefinition = "TEXT")
    private String adminReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "return_deadline")
    private LocalDateTime returnDeadline;

    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    protected ReturnRequest() {}

    public static ReturnRequest create(
            Order order,
            User buyer,
            String reason,
            List<String> evidenceImages,
            LocalDateTime now
    ) {
        ReturnRequest result = new ReturnRequest();

        result.order = order;
        result.buyer = buyer;
        result.reason = reason;
        result.evidenceImages = List.copyOf(evidenceImages);
        result.status = ReturnStatus.PENDING;
        result.createdAt = now;

        return result;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public User getBuyer() {
        return buyer;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getEvidenceImages() {
        return evidenceImages;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public User getProcessedBy() {
        return processedBy;
    }

    public String getAdminReason() {
        return adminReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getReturnDeadline() {
        return returnDeadline;
    }

    public LocalDateTime getHandedOverAt() {
        return handedOverAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }


    public void approve(
            User admin,
            String note,
            LocalDateTime now
    ) {
        if (status != ReturnStatus.PENDING) {
            throw new IllegalStateException(
                    "Return request is not pending"
            );
        }

        this.status = ReturnStatus.AWAITING_HANDOVER;
        this.processedBy = admin;
        this.processedAt = now;
        this.returnDeadline = now.plusHours(48);
        this.adminReason = note;
    }

    public void recordHandover(LocalDateTime actualAt) {
        if (status != ReturnStatus.AWAITING_HANDOVER
                || processedAt == null
                || returnDeadline == null
                || handedOverAt != null) {
            throw new IllegalStateException(
                    "Return request is not ready for handover"
            );
        }

        this.handedOverAt = actualAt;
        this.status = ReturnStatus.RETURNING;

        // Không cập nhật returnedAt.
        // returnedAt chỉ được ghi khi D11 hoàn tất.
    }

}
