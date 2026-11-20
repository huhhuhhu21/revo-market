package com.revo.backend.order.repository;

import com.revo.backend.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPurchaseRequest_Id(Long requestId);
}