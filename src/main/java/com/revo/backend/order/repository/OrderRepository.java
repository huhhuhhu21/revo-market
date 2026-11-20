package com.revo.backend.order.repository;

import com.revo.backend.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.revo.backend.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}