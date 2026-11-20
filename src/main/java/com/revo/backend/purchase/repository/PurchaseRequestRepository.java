package com.revo.backend.purchase.repository;

import com.revo.backend.purchase.entity.PurchaseRequest;
import com.revo.backend.purchase.entity.PurchaseRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
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

    // Chỉ đọc listingId để biết cần khóa bài nào trước.
// Chưa khóa request tại bước này.
    @Query("""
        SELECT p.listing.id
        FROM PurchaseRequest p
        WHERE p.id = :requestId
        """)
    Optional<Long> findListingIdByRequestId(
            @Param("requestId") Long requestId
    );

    // Chỉ được gọi sau khi transaction đã khóa listing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM PurchaseRequest p
        WHERE p.id = :requestId
        """)
    Optional<PurchaseRequest> findByIdForUpdate(
            @Param("requestId") Long requestId
    );

    // Tìm những người mua khác đang chờ xác nhận.
    @Query("""
        SELECT p.buyer.id
        FROM PurchaseRequest p
        WHERE p.listing.id = :listingId
          AND p.id <> :acceptedRequestId
          AND p.status = :pendingStatus
        """)
    List<Long> findOtherPendingBuyerIds(
            @Param("listingId") Long listingId,
            @Param("acceptedRequestId") Long acceptedRequestId,
            @Param("pendingStatus") PurchaseRequestStatus pendingStatus
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE purchase_requests
        SET status = 'REJECTED'
        WHERE listing_id = :listingId
          AND id <> :acceptedRequestId
          AND status = 'PENDING'
        """, nativeQuery = true)
    int rejectOtherPending(
            @Param("listingId") Long listingId,
            @Param("acceptedRequestId") Long acceptedRequestId
    );

    @Query("""
        SELECT p.id
        FROM PurchaseRequest p
        WHERE p.listing.id = :listingId
          AND p.buyer.id = :buyerId
          AND p.id <> :acceptedRequestId
          AND p.status = :pendingStatus
        """)
    List<Long> findOtherPendingRequestIds(
            @Param("listingId") Long listingId,
            @Param("buyerId") Long buyerId,
            @Param("acceptedRequestId") Long acceptedRequestId,
            @Param("pendingStatus") PurchaseRequestStatus pendingStatus
    );

    @Query("""
        SELECT p.id, p.buyer.id
        FROM PurchaseRequest p
        WHERE p.listing.id = :listingId
          AND p.id <> :acceptedRequestId
          AND p.status = :pendingStatus
        """)
    List<Object[]> findOtherPendingRecipients(
            @Param("listingId") Long listingId,
            @Param("acceptedRequestId") Long acceptedRequestId,
            @Param("pendingStatus") PurchaseRequestStatus pendingStatus
    );


}
