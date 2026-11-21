package com.revo.backend.listing.repository;

import com.revo.backend.listing.entity.ProductListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository
        extends JpaRepository<ProductListing, Long>,
        JpaSpecificationExecutor<ProductListing> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<ProductListing> findAll(
            Specification<ProductListing> spec,
            Pageable pageable
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM purchase_requests
        WHERE listing_id = :listingId
          AND status = 'PENDING'
        """, nativeQuery = true)
    long countPendingRequests(
            @Param("listingId") Long listingId
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM orders
        WHERE listing_id = :listingId
          AND status IN (
              'PENDING_PICKUP',
              'IN_TRANSIT',
              'DELIVERED'
          )
        """, nativeQuery = true)
    long countActiveOrders(
            @Param("listingId") Long listingId
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM orders
        WHERE listing_id = :listingId
          AND buyer_id = :buyerId
          AND status IN (
              'PENDING_PICKUP',
              'IN_TRANSIT',
              'DELIVERED',
              'COMPLETED'
          )
        """, nativeQuery = true)
    long countViewableOrdersForBuyer(
            @Param("listingId") Long listingId,
            @Param("buyerId") Long buyerId
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM ProductListing p
        WHERE p.id = :listingId
        """)
    Optional<ProductListing> findByIdForUpdate(
            @Param("listingId") Long listingId
    );



    @Query(value = """
    SELECT pr.listing_id
    FROM purchase_requests pr
    WHERE pr.listing_id IN (:listingIds)
      AND pr.status = 'PENDING'
    GROUP BY pr.listing_id
    """, nativeQuery = true)
    List<Long> findListingIdsWithPendingRequests(
            @Param("listingIds") Collection<Long> listingIds
    );

    @Query(value = """
    SELECT o.listing_id
    FROM orders o
    WHERE o.listing_id IN (:listingIds)
      AND o.status IN (
          'PENDING_PICKUP',
          'IN_TRANSIT',
          'DELIVERED'
      )
    GROUP BY o.listing_id
    """, nativeQuery = true)
    List<Long> findListingIdsWithActiveOrders(
            @Param("listingIds") Collection<Long> listingIds
    );


    @Query(value = """
    SELECT COUNT(*)
    FROM orders o
    JOIN return_requests r ON r.order_id = o.id
    WHERE o.listing_id = :listingId
      AND o.status = 'RETURNED'
      AND o.cod_status = 'REFUNDED'
      AND o.cod_refunded_at IS NOT NULL
      AND r.status = 'RETURNED'
      AND r.returned_at IS NOT NULL
    """, nativeQuery = true)
    long countSuccessfullyReturnedOrders(
            @Param("listingId") Long listingId
    );

    @Query(value = """
    SELECT COUNT(*)
    FROM orders o
    JOIN return_requests r ON r.order_id = o.id
    WHERE o.listing_id = :listingId
      AND r.status IN (
          'PENDING',
          'AWAITING_HANDOVER',
          'RETURNING'
      )
    """, nativeQuery = true)
    long countUnfinishedReturns(
            @Param("listingId") Long listingId
    );

}