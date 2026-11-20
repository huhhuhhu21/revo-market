package com.revo.backend.ai.service;

import com.revo.backend.ai.dto.ListingSuggestionRequest;
import com.revo.backend.ai.dto.ListingSuggestionResponse;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.category.CategoryStatus;
import com.revo.backend.category.entity.Category;
import com.revo.backend.category.repository.CategoryRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.listing.entity.ListingImage;
import com.revo.backend.listing.entity.ListingStatus;
import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.listing.repository.ListingImageRepository;
import com.revo.backend.listing.repository.ListingRepository;
import com.revo.backend.media.service.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiSuggestionService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final MediaService mediaService;
    private final DeepSeekSuggestionClient aiClient;
    private static final Logger log =
            LoggerFactory.getLogger(AiSuggestionService.class);

    public AiSuggestionService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ListingRepository listingRepository,
            ListingImageRepository imageRepository,
            MediaService mediaService,
            DeepSeekSuggestionClient aiClient
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.listingRepository = listingRepository;
        this.imageRepository = imageRepository;
        this.mediaService = mediaService;
        this.aiClient = aiClient;
    }

    public ListingSuggestionResponse suggest(
            Long userId,
            ListingSuggestionRequest request
    ) {
        // 1. Kiểm tra người dùng.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        if (user.getRole() != User.Role.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }

        if (!user.isEmailVerified()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        if (user.getPhone() == null
                || user.getPhone().isBlank()) {
            throw new BusinessException(
                    ErrorCode.CONTACT_PHONE_REQUIRED
            );
        }

        // 2. Chuẩn hóa mô tả.
        String description = request.descriptionInput() == null
                ? ""
                : request.descriptionInput().trim();

        if (description.length() > 2000) {
            throw new BusinessException(
                    ErrorCode.INVALID_AI_INPUT
            );
        }

        // LinkedHashSet giữ thứ tự ảnh và chống trùng.
        Set<String> objectKeys = new LinkedHashSet<>();

        // 3. Nếu đang sửa bài, lấy ảnh cũ của đúng chủ bài.
        if (request.listingId() != null) {

            ProductListing listing = listingRepository
                    .findById(request.listingId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.LISTING_NOT_FOUND
                    ));

            if (listing.isDeleted()
                    || !listing.getSeller().getId()
                    .equals(userId)) {
                throw new BusinessException(
                        ErrorCode.LISTING_NOT_FOUND
                );
            }

            if (listing.isAdminHidden()) {
                throw new BusinessException(
                        ErrorCode.LISTING_ADMIN_HIDDEN
                );
            }

            boolean editable =
                    listing.getStatus() == ListingStatus.AVAILABLE
                            || (
                            listing.getStatus() == ListingStatus.HIDDEN
                                    && (
                                    "SELLER".equals(
                                            listing.getHiddenReasonType()
                                    )
                                            || "RETURNED".equals(
                                            listing.getHiddenReasonType()
                                    )
                            )
                    );

            if (!editable) {
                throw new BusinessException(
                        ErrorCode.LISTING_NOT_EDITABLE
                );
            }

            if (listingRepository.countPendingRequests(
                    listing.getId()
            ) > 0
                    || listingRepository.countActiveOrders(
                    listing.getId()
            ) > 0) {
                throw new BusinessException(
                        ErrorCode.LISTING_LOCKED
                );
            }

            for (ListingImage image :
                    imageRepository
                            .findByListing_IdOrderBySortOrderAsc(
                                    listing.getId()
                            )) {

                objectKeys.add(image.getImageUrl());
            }
        }

        // 4. Kiểm tra ảnh mới upload từ B06.
        List<String> tokens = request.imageUploadTokens() == null
                ? List.of()
                : request.imageUploadTokens();

        if (tokens.size() > 8) {
            throw new BusinessException(
                    ErrorCode.INVALID_AI_INPUT
            );
        }

        Set<String> uniqueTokens = new HashSet<>();

        for (String token : tokens) {

            if (token == null
                    || token.isBlank()
                    || !uniqueTokens.add(token)) {
                throw new BusinessException(
                        ErrorCode.INVALID_AI_INPUT
                );
            }

            MediaService.ListingTicket ticket =
                    mediaService.validateListingTicket(
                            token,
                            userId
                    );

            if (!objectKeys.add(ticket.sourceKey())) {
                throw new BusinessException(
                        ErrorCode.INVALID_AI_INPUT
                );
            }
        }

        // 5. Có mô tả hoặc ảnh, tối đa 8 ảnh.
        if (objectKeys.size() > 8
                || (description.isBlank()
                && objectKeys.isEmpty())) {
            throw new BusinessException(
                    ErrorCode.INVALID_AI_INPUT
            );
        }

        // 6. Chỉ đưa danh mục ACTIVE cho AI.
        List<Category> categories =
                categoryRepository
                        .findByStatusOrderByNameAsc(
                                CategoryStatus.ACTIVE
                        );

        if (categories.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AI_UNAVAILABLE
            );
        }

        Map<Long, Category> allowedCategories =
                new HashMap<>();

        StringBuilder categoryCatalog =
                new StringBuilder();

        for (Category category : categories) {
            allowedCategories.put(
                    category.getId(),
                    category
            );

            categoryCatalog
                    .append(category.getId())
                    .append(" - ")
                    .append(category.getName()
                            .replace('\n', ' ')
                            .replace('\r', ' '))
                    .append('\n');
        }

        // 7. Đọc ảnh từ R2 private.
        List<MediaService.AiImage> aiImages =
                new ArrayList<>();

        for (String objectKey : objectKeys) {
            aiImages.add(
                    mediaService.readImageForAi(objectKey)
            );
        }
        log.info(
                "B13 calling DeepSeek: imageCount={}, categoryCount={}",
                aiImages.size(),
                categories.size()
        );

        // 8. Gọi AI. Không ghi MySQL.
        DeepSeekSuggestionClient.RawSuggestion raw =
                aiClient.suggest(
                        description,
                        aiImages,
                        categoryCatalog.toString()
                );

        // 9. Kiểm tra và chuẩn hóa kết quả AI.
        List<String> warnings = new ArrayList<>();

        if (raw.title() == null) {
            throw new BusinessException(
                    ErrorCode.AI_UNAVAILABLE
            );
        }

        String title = raw.title()
                .replaceAll("\\p{Cntrl}+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (title.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AI_UNAVAILABLE
            );
        }

        if (title.codePointCount(0, title.length()) > 100) {
            title = title.substring(
                    0,
                    title.offsetByCodePoints(0, 100)
            ).trim();

            warnings.add(
                    "Tiêu đề gợi ý đã được rút gọn."
            );
        }

        ListingSuggestionResponse.SuggestedCategory
                suggestedCategory = null;

        if (raw.categoryId() != null) {

            Category category =
                    allowedCategories.get(
                            raw.categoryId()
                    );

            if (category != null) {
                suggestedCategory =
                        new ListingSuggestionResponse
                                .SuggestedCategory(
                                category.getId(),
                                category.getName()
                        );
            } else {
                warnings.add(
                        "AI không chọn được danh mục hợp lệ."
                );
            }

        } else {
            warnings.add(
                    "AI chưa xác định được danh mục phù hợp."
            );
        }

        return new ListingSuggestionResponse(
                title,
                suggestedCategory,
                raw.listedPrice(),
                raw.itemCondition(),
                warnings
        );
    }
}