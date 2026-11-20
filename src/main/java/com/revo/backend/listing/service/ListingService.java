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
import com.revo.backend.auth.entity.User;
import com.revo.backend.category.CategoryStatus;
import com.revo.backend.listing.dto.ListingDetailResponse;
import com.revo.backend.listing.entity.ListingStatus;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.category.CategoryStatus;
import com.revo.backend.category.entity.Category;
import com.revo.backend.category.repository.CategoryRepository;
import com.revo.backend.media.service.MediaService;
import com.revo.backend.listing.dto.CreateListingRequest;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class ListingService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE;

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MediaService mediaService;
    private final EntityManager entityManager;

    public ListingService(
            ListingRepository listingRepository,
            ListingImageRepository imageRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            MediaService mediaService,
            EntityManager entityManager
    ) {
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.mediaService = mediaService;
        this.entityManager = entityManager;
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
                        image -> mediaService.presignImageUrl(image.getImageUrl()),
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

    @Transactional(readOnly = true)
    public ListingDetailResponse getDetail(
            String rawListingId,
            Long viewerId
    ) {
        // 1. Kiểm tra ID.
        Long listingId = parseListingId(rawListingId);

        // 2. Tìm bài đăng.
        ProductListing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.LISTING_NOT_FOUND
                ));

        // 3. Bài đã xóa logic không được xem qua B03.
        if (listing.isDeleted()) {
            throw new BusinessException(
                    ErrorCode.LISTING_NOT_FOUND
            );
        }

        User seller = listing.getSeller();

        boolean isOwner = viewerId != null
                && viewerId.equals(seller.getId());

        // 4. Kiểm tra bài đủ điều kiện công khai.
        boolean publicVisible =
                !listing.isAdminHidden()
                        && seller.getStatus() == User.Status.ACTIVE
                        && (
                        listing.getStatus() == ListingStatus.AVAILABLE
                                || listing.getStatus() == ListingStatus.SOLD
                );

        // 5. Kiểm tra người mua của đơn đang xử lý.
        boolean isRelatedBuyer = false;

        boolean viewableOrderStatus =
                listing.getStatus() == ListingStatus.IN_TRANSACTION
                        || listing.getStatus() == ListingStatus.SOLD;

        if (!isOwner
                && !publicVisible
                && !listing.isAdminHidden()
                && viewableOrderStatus
                && viewerId != null) {

            isRelatedBuyer =
                    listingRepository.countViewableOrdersForBuyer(
                            listingId,
                            viewerId
                    ) > 0;
        }

        // 6. Không đủ quyền thì trả 404.
        if (!isOwner && !publicVisible && !isRelatedBuyer) {
            throw new BusinessException(
                    ErrorCode.LISTING_NOT_FOUND
            );
        }

        // 7. Lấy hình ảnh.
        List<ListingDetailResponse.ImageInfo> images =
                imageRepository
                        .findByListing_IdOrderBySortOrderAsc(listingId)
                        .stream()
                        .map(image -> new ListingDetailResponse.ImageInfo(
                                image.getId(),
                                mediaService.presignImageUrl(image.getImageUrl()),
                                image.getSortOrder()
                        ))
                        .toList();

        // 8. Chỉ tính quyền quản lý khi là chủ bài.
        OwnerActions actions = isOwner
                ? resolveOwnerActions(listing)
                : null;

        // 9. Chuyển Entity sang DTO.
        return new ListingDetailResponse(
                listing.getId(),

                new ListingDetailResponse.SellerInfo(
                        seller.getId(),
                        seller.getFullName(),
                        seller.getAvatarUrl()
                ),

                new ListingDetailResponse.CategoryInfo(
                        listing.getCategory().getId(),
                        listing.getCategory().getName(),
                        listing.getCategory().getStatus()
                ),

                listing.getTitle(),
                listing.getDescription(),
                listing.getListedPrice(),
                listing.getItemCondition(),
                listing.getProvince(),
                listing.getWard(),
                listing.getStatus(),
                images,

                listing.getCreatedAt().atOffset(ZoneOffset.UTC),
                listing.getUpdatedAt().atOffset(ZoneOffset.UTC),

                isOwner ? listing.getHiddenReasonType() : null,
                isOwner ? listing.isAdminHidden() : null,
                isOwner ? listing.isDeleted() : null,

                isOwner ? actions.canEdit() : null,
                isOwner ? actions.canHide() : null,
                isOwner ? actions.canRestore() : null,
                isOwner ? actions.canDelete() : null,
                isOwner ? actions.canMarkSold() : null,
                isOwner ? actions.lockReason() : null
        );
    }
    private static Long parseListingId(String raw) {
        try {
            long id = Long.parseLong(raw.trim());

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_LISTING_ID
            );
        }
    }
    private record OwnerActions(
            boolean canEdit,
            boolean canHide,
            boolean canRestore,
            boolean canDelete,
            boolean canMarkSold,
            String lockReason
    ) {}

    private OwnerActions resolveOwnerActions(
            ProductListing listing
    ) {
        boolean activeSeller =
                listing.getSeller().getStatus() == User.Status.ACTIVE;

        boolean adminHidden = listing.isAdminHidden();

        boolean hasPending =
                listingRepository.countPendingRequests(
                        listing.getId()
                ) > 0;

        boolean hasActiveOrder =
                listingRepository.countActiveOrders(
                        listing.getId()
                ) > 0;

        boolean unlocked =
                activeSeller
                        && !adminHidden
                        && !hasPending
                        && !hasActiveOrder;

        boolean available =
                listing.getStatus() == ListingStatus.AVAILABLE;

        boolean hidden =
                listing.getStatus() == ListingStatus.HIDDEN;

        boolean restorableReason = hidden
                && (
                "SELLER".equals(listing.getHiddenReasonType())
                        || "RETURNED".equals(listing.getHiddenReasonType())
        );

        boolean hasContactPhone =
                listing.getSeller().getPhone() != null
                        && !listing.getSeller().getPhone().isBlank();

        boolean categoryActive =
                listing.getCategory().getStatus()
                        == CategoryStatus.ACTIVE;

        boolean canEdit =
                unlocked && (available || restorableReason);

        boolean canHide =
                unlocked && available;

        boolean canRestore =
                unlocked
                        && restorableReason
                        && hasContactPhone
                        && categoryActive;

        boolean canDelete =
                canEdit;

        // Có thể kết thúc yêu cầu PENDING khi mark-sold.
        boolean canMarkSold =
                activeSeller
                        && !adminHidden
                        && !hasActiveOrder
                        && available;

        String lockReason = null;

        if (adminHidden) {
            lockReason = "Bài đăng đang bị ADMIN ẩn.";
        } else if (!activeSeller) {
            lockReason = "Tài khoản không đủ quyền thao tác.";
        } else if (hasPending || hasActiveOrder) {
            lockReason = "Bài đang có yêu cầu mua hoặc đơn xử lý.";
        } else if (listing.getStatus() == ListingStatus.SOLD) {
            lockReason = "Bài đã bán không thể chỉnh sửa.";
        } else if (restorableReason && !categoryActive) {
            lockReason = "Danh mục đã ngừng hoạt động.";
        } else if (restorableReason && !hasContactPhone) {
            lockReason = "Cần cập nhật số điện thoại liên hệ.";
        }

        return new OwnerActions(
                canEdit,
                canHide,
                canRestore,
                canDelete,
                canMarkSold,
                lockReason
        );
    }

    @Transactional
    public ListingDetailResponse create(
            Long sellerId,
            CreateListingRequest request
    ) {
        // 1. Khóa người bán để tránh hai request
        // cùng sử dụng một uploadToken.
        User seller = userRepository
                .findByIdForUpdate(sellerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        if (seller.getRole() != User.Role.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (seller.getStatus() == User.Status.RESTRICTED) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }

        if (seller.getStatus() != User.Status.ACTIVE
                || !seller.isEmailVerified()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        // Không cần phoneVerified theo quyết định mới.
        if (seller.getPhone() == null
                || seller.getPhone().isBlank()) {
            throw new BusinessException(
                    ErrorCode.CONTACT_PHONE_REQUIRED
            );
        }

        // 2. Kiểm tra danh mục.
        Category category = categoryRepository
                .findByIdForListing(request.categoryId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CATEGORY_NOT_FOUND
                ));

        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_INACTIVE
            );
        }

        // 3. Kiểm tra giá là số nguyên VND dương.
        long price;

        try {
            price = request.listedPrice().longValueExact();

            if (price <= 0) {
                throw new ArithmeticException();
            }

        } catch (ArithmeticException ex) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 4. Chuẩn hóa các chuỗi.
        String title = request.title().trim();
        String description = request.description().trim();
        String province = request.province().trim();
        String ward = request.ward().trim();

        // 5. Kiểm tra danh sách ảnh và thứ tự.
        List<CreateListingRequest.ImageInput> images =
                new ArrayList<>(request.images());

        images.sort(
                Comparator.comparing(
                        CreateListingRequest.ImageInput::sortOrder
                )
        );

        for (int i = 0; i < images.size(); i++) {
            if (!Objects.equals(
                    images.get(i).sortOrder(),
                    i + 1
            )) {
                throw new BusinessException(
                        ErrorCode.INVALID_IMAGE_SET
                );
            }
        }

        // 6. Xác minh tất cả token trước khi ghi dữ liệu.
        Set<String> uniqueKeys = new HashSet<>();

        List<MediaService.ListingTicket> tickets =
                new ArrayList<>();

        for (CreateListingRequest.ImageInput image : images) {

            MediaService.ListingTicket ticket =
                    mediaService.validateListingTicket(
                            image.uploadToken(),
                            sellerId
                    );

            if (!uniqueKeys.add(ticket.destinationKey())) {
                throw new BusinessException(
                        ErrorCode.INVALID_IMAGE_SET
                );
            }

            if (imageRepository.existsByImageUrl(
                    ticket.destinationKey()
            )) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            tickets.add(ticket);
        }

        // 7. Tạo bài đăng.
        ProductListing listing = ProductListing.create(
                seller,
                category,
                title,
                description,
                price,
                request.itemCondition(),
                province,
                ward
        );

        listingRepository.saveAndFlush(listing);

        // 8. Copy từng ảnh từ staging sang nơi chính thức.
        List<ListingImage> listingImages =
                new ArrayList<>();

        for (int i = 0; i < tickets.size(); i++) {

            MediaService.ListingTicket ticket = tickets.get(i);

            mediaService.promoteListingImage(ticket);

            listingImages.add(
                    ListingImage.create(
                            listing,
                            ticket.destinationKey(),
                            i + 1
                    )
            );
        }

        // 9. Lưu 1–8 ảnh trong cùng transaction với bài.
        imageRepository.saveAllAndFlush(listingImages);

        // 10. Tải lại các thời điểm do MySQL sinh ra.
        entityManager.refresh(listing);

        // 11. Dùng lại DTO B03 để trả chi tiết bài vừa tạo.
        return getDetail(
                listing.getId().toString(),
                sellerId
        );
    }
}