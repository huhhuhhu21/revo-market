
package com.revo.backend.order.repository;

import com.revo.backend.order.entity.OrderException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderExceptionRepository
        extends JpaRepository<OrderException, Long> {
}
