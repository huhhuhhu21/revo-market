
package com.revo.backend.order.repository;

import com.revo.backend.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReturnRequestRepository
        extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByOrder_Id(Long orderId);
}
