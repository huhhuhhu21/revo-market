package com.revo.backend.listing.entity;

import com.revo.backend.auth.entity.User;
import com.revo.backend.category.entity.Category;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_listings")
public class ProductListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "listed_price", nullable = false)
    private Long listedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 16)
    private ItemCondition itemCondition;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ListingStatus status;

    @Column(name = "hidden_reason_type", length = 16)
    private String hiddenReasonType;

    @Column(name = "is_admin_hidden", nullable = false)
    private boolean adminHidden;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    protected ProductListing() {
    }

    public Long getId() { return id; }
    public User getSeller() { return seller; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public Long getListedPrice() { return listedPrice; }
    public ItemCondition getItemCondition() { return itemCondition; }
    public String getProvince() { return province; }
    public String getWard() { return ward; }
    public ListingStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getDescription() {
        return description;
    }

    public String getHiddenReasonType() {
        return hiddenReasonType;
    }

    public boolean isAdminHidden() {
        return adminHidden;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public static ProductListing create(
            User seller,
            Category category,
            String title,
            String description,
            Long listedPrice,
            ItemCondition itemCondition,
            String province,
            String ward
    ) {
        ProductListing listing = new ProductListing();

        listing.seller = seller;
        listing.category = category;
        listing.title = title;
        listing.description = description;
        listing.listedPrice = listedPrice;
        listing.itemCondition = itemCondition;
        listing.province = province;
        listing.ward = ward;

        listing.status = ListingStatus.AVAILABLE;
        listing.hiddenReasonType = null;
        listing.adminHidden = false;
        listing.deleted = false;

        return listing;
    }

    public void applyEditablePatch(
            String title,
            String description,
            Category category,
            Long listedPrice,
            ItemCondition itemCondition,
            String province,
            String ward
    ) {
        if (title != null) {
            this.title = title;
        }

        if (description != null) {
            this.description = description;
        }

        if (category != null) {
            this.category = category;
        }

        if (listedPrice != null) {
            this.listedPrice = listedPrice;
        }

        if (itemCondition != null) {
            this.itemCondition = itemCondition;
        }

        if (province != null) {
            this.province = province;
        }

        if (ward != null) {
            this.ward = ward;
        }
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void hideBySeller() {
        this.status = ListingStatus.HIDDEN;
        this.hiddenReasonType = "SELLER";
    }


    public void markInTransaction() {
        if (getStatus() != ListingStatus.AVAILABLE
                || getHiddenReasonType() != null
                || isAdminHidden()
                || isDeleted()) {

            throw new IllegalStateException(
                    "Listing is not available for transaction"
            );
        }

        this.status = ListingStatus.IN_TRANSACTION;
    }


    public void restoreForSale() {
        this.status = ListingStatus.AVAILABLE;
        this.hiddenReasonType = null;
    }

}