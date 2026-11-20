package com.revo.backend.listing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "listing_images")
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ProductListing listing;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected ListingImage() {
    }

    public Long getListingId() {
        return listing.getId();
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Long getId() {
        return id;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public static ListingImage create(
            ProductListing listing,
            String imageUrl,
            int sortOrder
    ) {
        ListingImage image = new ListingImage();

        image.listing = listing;
        image.imageUrl = imageUrl;
        image.sortOrder = sortOrder;

        return image;
    }
}