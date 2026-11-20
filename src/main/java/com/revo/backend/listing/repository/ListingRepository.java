package com.revo.backend.listing.repository;

import com.revo.backend.listing.entity.ProductListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}