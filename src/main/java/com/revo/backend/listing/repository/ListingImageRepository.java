package com.revo.backend.listing.repository;

import com.revo.backend.listing.entity.ListingImage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingImageRepository
        extends JpaRepository<ListingImage, Long> {

    boolean existsByImageUrl(String imageUrl);

    @Query("""
            SELECT image
            FROM ListingImage image
            WHERE image.listing.id IN :listingIds
              AND image.sortOrder = 1
            """)
    List<ListingImage> findCoverImages(
            @Param("listingIds") Collection<Long> listingIds
    );

    List<ListingImage> findByListing_IdOrderBySortOrderAsc(
            Long listingId
    );
}