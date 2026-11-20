package com.revo.backend.purchase.repository;

import com.revo.backend.purchase.entity.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequest, Long>,
        JpaSpecificationExecutor<PurchaseRequest> {

    // Chỉ gọi trong transaction đã khóa product_listings tương ứng.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE purchase_requests
               SET status = 'EXPIRED'
             WHERE listing_id = :listingId
               AND status = 'PENDING'
               AND expires_at < :now
            """, nativeQuery = true)
    int expirePendingForListing(
            @Param("listingId") Long listingId,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT COUNT(*) FROM purchase_requests
             WHERE listing_id = :listingId
               AND buyer_id = :buyerId
               AND status = 'PENDING'
            """, nativeQuery = true)
    long countPendingForBuyer(
            @Param("listingId") Long listingId,
            @Param("buyerId") Long buyerId
    );

    // SRS yêu cầu người mua đã có địa chỉ hiện hành hợp lệ trên users.
    // Query native tránh sửa User.java chỉ để đọc ba cột sẵn có trong MySQL.
    @Query(value = """
            SELECT COUNT(*) FROM users
             WHERE id = :userId
               AND NULLIF(TRIM(address_line), '') IS NOT NULL
               AND NULLIF(TRIM(province), '') IS NOT NULL
               AND NULLIF(TRIM(ward), '') IS NOT NULL
            """, nativeQuery = true)
    long countBuyerWithSavedAddress(@Param("userId") Long userId);

    @Override
    @EntityGraph(attributePaths = {
            "listing",
            "listing.seller"
    })
    Page<PurchaseRequest> findAll(
            Specification<PurchaseRequest> spec,
            Pageable pageable
    );

    @Query(value = """
        SELECT
            purchase_request_id,
            id,
            status
        FROM orders
        WHERE purchase_request_id IN (:requestIds)
        """, nativeQuery = true)
    List<Object[]> findOrderLinks(
            @Param("requestIds") List<Long> requestIds
    );

    @EntityGraph(attributePaths = {
            "listing",
            "listing.seller",
            "buyer"
    })
    List<PurchaseRequest> findByBuyer_IdOrderByCreatedAtDescIdDesc(
            Long buyerId
    );

    @EntityGraph(attributePaths = {
            "listing",
            "listing.seller",
            "buyer"
    })
    List<PurchaseRequest> findByListing_Seller_IdOrderByCreatedAtDescIdDesc(
            Long sellerId
    );

}
