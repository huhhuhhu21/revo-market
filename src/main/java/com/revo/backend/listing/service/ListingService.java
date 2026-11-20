package com.revo.backend.listing.service;

import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.listing.dto.*;
import com.revo.backend.listing.entity.*;
import com.revo.backend.listing.repository.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE;

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository imageRepository
    ) {
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
    }

    @Transactional(readOnly = true)
    public ListingSearchResponse search(ListingSearchRequest request) {

        int page = parsePage(request.page());
        validateSize(request.size());

        String keyword = optionalText(request.q(), 100);

        Long categoryId = positiveLong(
                request.categoryId(), ErrorCode.INVALID_FILTER
        );

        Long minPrice = positiveLong(
                request.minPrice(), ErrorCode.INVALID_PRICE_RANGE
        );

        Long maxPrice = positiveLong(
                request.maxPrice(), ErrorCode.INVALID_PRICE_RANGE
        );

        if (minPrice != null && maxPrice != null
                && minPrice > maxPrice) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRICE_RANGE
            );
        }

        ItemCondition condition = parseCondition(request.condition());
        String province = optionalText(request.province(), 100);
        String ward = optionalText(request.ward(), 100);

        Pageable pageable = PageRequest.of(
                page,
                PAGE_SIZE,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<ProductListing> result = listingRepository.findAll(
                ListingSpecifications.publiclyVisible(
                        keyword, categoryId, minPrice, maxPrice,
                        condition, province, ward
                ),
                pageable
        );

        List<Long> ids = result.getContent()
                .stream()
                .map(ProductListing::getId)
                .toList();

        Map<Long, String> thumbnails = ids.isEmpty()
                ? Map.of()
                : imageRepository.findCoverImages(ids)
                .stream()
                .collect(Collectors.toMap(
                        ListingImage::getListingId,
                        ListingImage::getImageUrl,
                        (first, ignored) -> first
                ));

        List<ListingSearchResponse.ListingCard> items =
                result.getContent()
                        .stream()
                        .map(listing -> toCard(
                                listing,
                                thumbnails.get(listing.getId())
                        ))
                        .toList();

        return new ListingSearchResponse(
                items,
                page,
                PAGE_SIZE,
                result.getTotalElements(),
                result.hasNext()
        );
    }

    private ListingSearchResponse.ListingCard toCard(
            ProductListing listing,
            String thumbnailUrl
    ) {
        return new ListingSearchResponse.ListingCard(
                listing.getId(),
                listing.getTitle(),
                listing.getListedPrice(),
                listing.getItemCondition(),
                new ListingSearchResponse.CategoryCard(
                        listing.getCategory().getId(),
                        listing.getCategory().getName()
                ),
                listing.getProvince(),
                listing.getWard(),
                thumbnailUrl,
                listing.getStatus(),
                listing.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    private static int parsePage(String raw) {
        if (raw == null) {
            return 0;
        }

        try {
            int page = Integer.parseInt(raw.trim());

            if (page < 0 || page > MAX_PAGE) {
                throw new NumberFormatException();
            }

            return page;
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGINATION
            );
        }
    }

    private static void validateSize(String raw) {
        if (raw != null && !raw.trim().equals("20")) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGINATION
            );
        }
    }

    private static Long positiveLong(
            String raw,
            ErrorCode errorCode
    ) {
        if (raw == null) {
            return null;
        }

        try {
            long value = Long.parseLong(raw.trim());

            if (value <= 0) {
                throw new NumberFormatException();
            }

            return value;
        } catch (NumberFormatException ex) {
            throw new BusinessException(errorCode);
        }
    }

    private static ItemCondition parseCondition(String raw) {
        if (raw == null) {
            return null;
        }

        try {
            return ItemCondition.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FILTER);
        }
    }

    private static String optionalText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim();

        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_FILTER);
        }

        return normalized.isEmpty() ? null : normalized;
    }
}