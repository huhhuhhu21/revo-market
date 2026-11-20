package com.revo.backend.listing.repository;

import com.revo.backend.listing.entity.ProductListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface ListingRepository
        extends JpaRepository<ProductListing, Long>,
        JpaSpecificationExecutor<ProductListing> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<ProductListing> findAll(
            Specification<ProductListing> spec,
            Pageable pageable
    );
}