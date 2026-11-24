
package com.revo.backend.order.repository;

import com.revo.backend.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository
        extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByOrder_Id(Long orderId);


    @Query(
            value = """
        SELECT r.*
        FROM return_requests r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:orderId IS NULL OR r.order_id = :orderId)
        ORDER BY
            CASE r.status
                WHEN 'PENDING' THEN 0
                WHEN 'AWAITING_HANDOVER' THEN 1
                WHEN 'RETURNING' THEN 2
                WHEN 'REJECTED' THEN 3
                WHEN 'HANDOVER_EXPIRED' THEN 4
                WHEN 'RETURNED' THEN 5
                ELSE 6
            END ASC,
            r.created_at ASC,
            r.id ASC
        """,
            countQuery = """
        SELECT COUNT(*)
        FROM return_requests r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:orderId IS NULL OR r.order_id = :orderId)
        """,
            nativeQuery = true
    )
    Page<ReturnRequest> findAdminReturns(
            @Param("status") String status,
            @Param("orderId") Long orderId,
            Pageable pageable
    );

    // Tìm orderId của yêu cầu trả hàng.
    // Chưa khóa dữ liệu tại bước này.
    @Query("""
    SELECT r.order.id
    FROM ReturnRequest r
    WHERE r.id = :returnId
    """)
    Optional<Long> findOrderIdByReturnId(
            @Param("returnId") Long returnId
    );

    // Chỉ gọi sau khi đã khóa Order tương ứng.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT r
    FROM ReturnRequest r
    WHERE r.id = :returnId
    """)
    Optional<ReturnRequest> findByIdForUpdate(
            @Param("returnId") Long returnId
    );

}
