
package com.revo.backend.order.repository;

import com.revo.backend.order.entity.OrderException;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderExceptionRepository
        extends JpaRepository<OrderException, Long> {

    @Query(value = """
    SELECT id
    FROM order_exceptions
    WHERE order_id = :orderId
      AND exception_type = 'COD_ORDER'
      AND description LIKE 'D11_COD_ALREADY_SETTLED%'
    ORDER BY id DESC
    LIMIT 1
    """, nativeQuery = true)
    Optional<Long> findExistingCodSettledException(
            @Param("orderId") Long orderId
    );

}
