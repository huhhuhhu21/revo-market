package com.revo.backend.listing.repository;

import com.revo.backend.auth.entity.User;
import com.revo.backend.listing.entity.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ListingSpecifications {

    private ListingSpecifications() {
    }

    public static Specification<ProductListing> publiclyVisible(
            String keyword,
            Long categoryId,
            Long minPrice,
            Long maxPrice,
            ItemCondition condition,
            String province,
            String ward
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Điều kiện công khai bắt buộc
            predicates.add(cb.equal(
                    root.get("status"), ListingStatus.AVAILABLE
            ));

            predicates.add(cb.isNull(
                    root.get("hiddenReasonType")
            ));

            predicates.add(cb.isFalse(
                    root.get("adminHidden")
            ));

            predicates.add(cb.isFalse(
                    root.get("deleted")
            ));

            predicates.add(cb.equal(
                    root.join("seller", JoinType.INNER).get("status"),
                    User.Status.ACTIVE
            ));

            // Tìm kiếm theo từ khóa
            if (keyword != null) {
                String pattern = "%" +
                        escapeLike(keyword.toLowerCase(Locale.ROOT)) +
                        "%";

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '!'),
                        cb.like(cb.lower(root.get("description")), pattern, '!')
                ));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(
                        root.get("category").get("id"), categoryId
                ));
            }

            if (minPrice != null) {
                predicates.add(cb.ge(
                        root.get("listedPrice"), minPrice
                ));
            }

            if (maxPrice != null) {
                predicates.add(cb.le(
                        root.get("listedPrice"), maxPrice
                ));
            }

            if (condition != null) {
                predicates.add(cb.equal(
                        root.get("itemCondition"), condition
                ));
            }

            if (province != null) {
                predicates.add(cb.equal(
                        cb.lower(root.get("province")),
                        province.toLowerCase(Locale.ROOT)
                ));
            }

            if (ward != null) {
                predicates.add(cb.equal(
                        cb.lower(root.get("ward")),
                        ward.toLowerCase(Locale.ROOT)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }


    public static Specification<ProductListing> ownedBy(
            Long sellerId,
            ListingStatus status
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(
                    root.get("seller").get("id"),
                    sellerId
            ));

            // Bài đã xóa logic không còn trong danh sách quản lý.
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null) {
                predicates.add(cb.equal(
                        root.get("status"),
                        status
                ));
            }

            return cb.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }

}