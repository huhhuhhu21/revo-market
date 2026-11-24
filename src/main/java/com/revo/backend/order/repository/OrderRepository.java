package com.revo.backend.order.repository;

import com.revo.backend.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import com.revo.backend.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.revo.backend.order.entity.CodStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPurchaseRequest_Id(Long requestId);

    @Query("""
        SELECT o
        FROM Order o
        JOIN FETCH o.listing l
        JOIN FETCH o.buyer b
        JOIN FETCH o.seller s
        WHERE (
            (:role = 'ALL' AND (
                b.id = :userId OR s.id = :userId
            ))
            OR (:role = 'BUYER' AND b.id = :userId)
            OR (:role = 'SELLER' AND s.id = :userId)
        )
        AND o.status IN :statuses
        ORDER BY o.createdAt DESC, o.id DESC
        """)
    List<Order> findVisibleOrders(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("statuses") List<OrderStatus> statuses
    );

    @Query(value = """
        SELECT
            o.id,

            EXISTS (
                SELECT 1
                FROM return_requests r
                WHERE r.order_id = o.id
                  AND r.status IN (
                      'PENDING',
                      'AWAITING_HANDOVER',
                      'RETURNING'
                  )
            ),

            EXISTS (
                SELECT 1
                FROM order_exceptions e
                WHERE e.order_id = o.id
                  AND e.status = 'PENDING'
            ),

            JSON_CONTAINS(
                o.shipping_history,
                JSON_OBJECT(
                    'event_type', 'CANCEL_REQUESTED'
                )
            )

        FROM orders o
        WHERE o.id IN (:orderIds)
        """, nativeQuery = true)
    List<Object[]> findOrderUiConditions(
            @Param("orderIds") List<Long> orderIds
    );

    @Query("""
        SELECT o
        FROM Order o
        JOIN FETCH o.purchaseRequest
        JOIN FETCH o.listing
        JOIN FETCH o.buyer b
        JOIN FETCH o.seller s
        LEFT JOIN FETCH o.canceledBy
        WHERE o.id = :orderId
          AND (
              b.id = :userId
              OR s.id = :userId
          )
        """)
    Optional<Order> findVisibleOrderDetail(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId
    );

    @Query(value = """
        SELECT
            jt.event_type,
            jt.actual_at,
            jt.recorded_at,
            jt.actor_type
        FROM orders o
        CROSS JOIN JSON_TABLE(
            o.shipping_history,
            '$[*]'
            COLUMNS (
                seq_no FOR ORDINALITY,
                event_type VARCHAR(64)
                    PATH '$.event_type',
                actual_at VARCHAR(40)
                    PATH '$.actual_at',
                recorded_at VARCHAR(40)
                    PATH '$.recorded_at',
                actor_type VARCHAR(24)
                    PATH '$.actor_type'
            )
        ) AS jt
        WHERE o.id = :orderId
        ORDER BY jt.recorded_at ASC, jt.seq_no ASC
        """, nativeQuery = true)
    List<Object[]> findShippingTimeline(
            @Param("orderId") Long orderId
    );

    @Query(value = """
        SELECT r.id
        FROM return_requests r
        WHERE r.order_id = :orderId
        """, nativeQuery = true)
    Optional<Long> findReturnRequestId(
            @Param("orderId") Long orderId
    );


    @Query("""
    SELECT o.listing.id
    FROM Order o
    WHERE o.id = :orderId
    """)
    Optional<Long> findListingIdByOrderId(
            @Param("orderId") Long orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT o
    FROM Order o
    WHERE o.id = :orderId
    """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId
    );

    @Query(value = """
    SELECT jt.recorded_at, jt.reason
    FROM orders o
    JOIN JSON_TABLE(
        o.shipping_history,
        '$[*]'
        COLUMNS (
            seq_no FOR ORDINALITY,
            event_type VARCHAR(64) PATH '$.event_type',
            recorded_at VARCHAR(48) PATH '$.recorded_at',
            reason VARCHAR(2000) PATH '$.reason'
        )
    ) AS jt
    WHERE o.id = :orderId
      AND jt.event_type = 'CANCEL_REQUESTED'
    ORDER BY jt.seq_no DESC
    """, nativeQuery = true)
    List<Object[]> findCancellationRequests(
            @Param("orderId") Long orderId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
    UPDATE orders
    SET shipping_history = JSON_ARRAY_APPEND(
            shipping_history,
            '$',
            JSON_OBJECT(
                'event_type', :eventType,
                'actual_at', :isoAt,
                'recorded_at', :isoAt,
                'actor_type', :actorType,
                'actor_user_id', :actorUserId,
                'admin_id', NULL,
                'reason', :reason
            )
        ),
        updated_at = :now
    WHERE id = :orderId
    """, nativeQuery = true)
    int appendShippingEvent(
            @Param("orderId") Long orderId,
            @Param("eventType") String eventType,
            @Param("isoAt") String isoAt,
            @Param("actorType") String actorType,
            @Param("actorUserId") Long actorUserId,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now
    );


    @Query(
            value = """
        SELECT o
        FROM Order o
        JOIN FETCH o.buyer
        JOIN FETCH o.seller
        WHERE (:orderId IS NULL OR o.id = :orderId)
          AND (:status IS NULL OR o.status = :status)
          AND (:codStatus IS NULL OR o.codStatus = :codStatus)
        """,
            countQuery = """
        SELECT COUNT(o)
        FROM Order o
        WHERE (:orderId IS NULL OR o.id = :orderId)
          AND (:status IS NULL OR o.status = :status)
          AND (:codStatus IS NULL OR o.codStatus = :codStatus)
        """
    )
    Page<Order> findAdminOrders(
            @Param("orderId") Long orderId,
            @Param("status") OrderStatus status,
            @Param("codStatus") CodStatus codStatus,
            Pageable pageable
    );


}