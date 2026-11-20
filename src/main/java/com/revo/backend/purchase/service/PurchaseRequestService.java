package com.revo.backend.purchase.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.listing.entity.ListingStatus;
import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.listing.repository.ListingRepository;
import com.revo.backend.notification.entity.Notification;
import com.revo.backend.notification.repository.NotificationRepository;
import com.revo.backend.purchase.dto.CreatePurchaseRequestRequest;
import com.revo.backend.purchase.dto.PurchaseRequestResponse;
import com.revo.backend.purchase.entity.PurchaseRequest;
import com.revo.backend.purchase.repository.PurchaseRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.revo.backend.purchase.dto.SentPurchaseRequestItem;
import com.revo.backend.purchase.dto.SentPurchaseRequestPage;
import com.revo.backend.purchase.entity.PurchaseRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
@Service
public class PurchaseRequestService {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final NotificationRepository notificationRepository;
    private record OrderLink(
            Long orderId,
            String status
    ) {}

    public PurchaseRequestService(
            UserRepository userRepository,
            ListingRepository listingRepository,
            PurchaseRequestRepository purchaseRequestRepository,
            NotificationRepository notificationRepository
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public PurchaseRequestResponse create(Long buyerId, CreatePurchaseRequestRequest input) {
        // Buyer không được lấy từ body. USER RESTRICTED không được tạo yêu cầu mới.
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        ensureEligible(buyer);

        if (purchaseRequestRepository.countBuyerWithSavedAddress(buyerId) == 0) {
            throw new BusinessException(ErrorCode.DELIVERY_ADDRESS_REQUIRED);
        }

        String recipientName = requiredText(input.recipientName());
        String recipientPhone = requiredText(input.recipientPhone());
        String addressLine = requiredText(input.deliveryAddressLine());
        String province = requiredText(input.deliveryProvince());
        String ward = requiredText(input.deliveryWard());
        String note = input.note() == null ? null : input.note().trim();
        if (note != null && note.isEmpty()) note = null;

        // Theo định dạng SĐT Việt Nam thường dùng trong Revo. Không bắt buộc là SĐT tài khoản.
        if (!recipientPhone.matches("(?:0\\d{9}|\\+84\\d{9})")) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_CONTACT);
        }
        if (recipientName.isEmpty() || addressLine.isEmpty()
                || province.isEmpty() || ward.isEmpty()) {
            throw new BusinessException(ErrorCode.DELIVERY_ADDRESS_REQUIRED);
        }

        // Khóa listing làm điểm điều phối chung cho các request của cùng một bài.
        ProductListing listing = listingRepository.findByIdForUpdate(input.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        if (listing.isDeleted()) {
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        }
        if (buyerId.equals(listing.getSeller().getId())) {
            throw new BusinessException(ErrorCode.CANNOT_BUY_OWN_LISTING);
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE
                || listing.isAdminHidden()
                || listing.getHiddenReasonType() != null) {
            throw new BusinessException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        User seller = listing.getSeller();
        if (!isEligible(seller) || listingRepository.countActiveOrders(listing.getId()) > 0) {
            throw new BusinessException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        // SQL DATETIME(3) lưu UTC; đúng mốc expires_at vẫn chưa bị hết hạn.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);
        purchaseRequestRepository.expirePendingForListing(listing.getId(), now);

        if (purchaseRequestRepository.countPendingForBuyer(listing.getId(), buyerId) > 0) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_ALREADY_PENDING);
        }

        PurchaseRequest request = PurchaseRequest.create(
                listing, buyer, input.offeredPrice(), recipientName,
                recipientPhone, addressLine, province, ward, note, now
        );

        try {
            request = purchaseRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException ex) {
            if (isPendingUniqueConflict(ex)) {
                throw new BusinessException(ErrorCode.PURCHASE_REQUEST_ALREADY_PENDING);
            }
            throw ex;
        }

        // Notification và yêu cầu mua commit/rollback cùng transaction.
        notificationRepository.save(Notification.purchaseRequestReceived(seller, request.getId()));
        return PurchaseRequestResponse.fromCreated(request);
    }

    private static String requiredText(String value) {
        return value == null ? "" : value.trim();
    }

    private static void ensureEligible(User user) {
        if (user.getRole() != User.Role.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(ErrorCode.CONTACT_PHONE_REQUIRED);
        }
        // Quyết định nghiệp vụ mới: không yêu cầu user.isPhoneVerified().
    }

    private static boolean isEligible(User user) {
        return user.getRole() == User.Role.USER
                && user.getStatus() == User.Status.ACTIVE
                && user.isEmailVerified()
                && user.getPhone() != null
                && !user.getPhone().isBlank();
    }

    private static boolean isPendingUniqueConflict(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sql
                    && sql.getErrorCode() == 1062
                    && sql.getMessage() != null
                    && sql.getMessage().contains("uq_request_pending_buyer")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public SentPurchaseRequestPage getSentRequests(
            Long buyerId,
            int page,
            int size,
            String status
    ) {
        // 1. Kiểm tra phân trang.
        if (page < 0 || size < 1 || size > 20) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGINATION
            );
        }

        // 2. Chuẩn hóa bộ lọc trạng thái.
        String filter = status == null
                ? "ALL"
                : status.trim().toUpperCase(Locale.ROOT);

        PurchaseRequestStatus filterStatus = null;

        if (!filter.equals("ALL")) {
            try {
                filterStatus = PurchaseRequestStatus.valueOf(
                        filter
                );
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATUS_FILTER
                );
            }
        }

        // 3. Lấy thời gian server UTC.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);

        PurchaseRequestStatus selectedStatus = filterStatus;

        // 4. Chỉ lấy yêu cầu của chính buyerId.
        Specification<PurchaseRequest> spec =
                (root, query, cb) -> {

                    Predicate owner = cb.equal(
                            root.get("buyer").get("id"),
                            buyerId
                    );

                    if (selectedStatus == null) {
                        return owner;
                    }

                    Predicate statusPredicate;

                    if (selectedStatus == PurchaseRequestStatus.PENDING) {

                        statusPredicate = cb.and(
                                cb.equal(
                                        root.get("status"),
                                        PurchaseRequestStatus.PENDING
                                ),
                                cb.greaterThanOrEqualTo(
                                        root.<LocalDateTime>get("expiresAt"),
                                        now
                                )
                        );

                    } else if (
                            selectedStatus == PurchaseRequestStatus.EXPIRED
                    ) {

                        statusPredicate = cb.or(
                                cb.equal(
                                        root.get("status"),
                                        PurchaseRequestStatus.EXPIRED
                                ),
                                cb.and(
                                        cb.equal(
                                                root.get("status"),
                                                PurchaseRequestStatus.PENDING
                                        ),
                                        cb.lessThan(
                                                root.<LocalDateTime>get("expiresAt"),
                                                now
                                        )
                                )
                        );

                    } else {

                        statusPredicate = cb.equal(
                                root.get("status"),
                                selectedStatus
                        );
                    }

                    return cb.and(owner, statusPredicate);
                };

        // 5. Phân trang, mới nhất trước, có ID để tránh
        // thứ tự không ổn định khi createdAt trùng nhau.
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<PurchaseRequest> result =
                purchaseRequestRepository.findAll(
                        spec,
                        pageable
                );

        // 6. Lấy orderId của các request trong trang.
        Map<Long, OrderLink> orderLinks = new HashMap<>();

        List<Long> requestIds = result.getContent()
                .stream()
                .map(PurchaseRequest::getId)
                .toList();

        if (!requestIds.isEmpty()) {

            for (Object[] row :
                    purchaseRequestRepository.findOrderLinks(requestIds)) {

                Long requestId = ((Number) row[0]).longValue();
                Long orderId = ((Number) row[1]).longValue();
                String orderStatus = row[2].toString();

                orderLinks.put(
                        requestId,
                        new OrderLink(orderId, orderStatus)
                );
            }
        }

        // 7. Chuyển Entity thành DTO danh sách.
        List<SentPurchaseRequestItem> items = new ArrayList<>();

        for (PurchaseRequest request : result.getContent()) {

            ProductListing listing = request.getListing();
            OrderLink orderLink = orderLinks.get(request.getId());

            // Chỉ công khai tiêu đề bài hiện tại nếu
            // người mua có quyền đọc chi tiết bài.
            boolean sellerActive =
                    listing.getSeller().getStatus() == User.Status.ACTIVE;

            boolean publiclyAccessible =
                    sellerActive
                            && (
                            listing.getStatus() == ListingStatus.AVAILABLE
                                    || listing.getStatus() == ListingStatus.SOLD
                    );

            // Người mua của đơn đang xử lý có thể
            // xem bài IN_TRANSACTION liên quan.
            boolean activeOrderOfBuyer =
                    orderLink != null
                            && (
                            "PENDING_PICKUP".equals(orderLink.status())
                                    || "IN_TRANSIT".equals(orderLink.status())
                                    || "DELIVERED".equals(orderLink.status())
                    );

            boolean orderAccessible =
                    listing.getStatus() == ListingStatus.IN_TRANSACTION
                            && activeOrderOfBuyer;

            boolean detailAccessible =
                    !listing.isDeleted()
                            && !listing.isAdminHidden()
                            && (
                            publiclyAccessible
                                    || orderAccessible
                    )
                            && (
                            listing.getHiddenReasonType() == null
                    );

            String currentTitle = detailAccessible
                    ? listing.getTitle()
                    : null;

            PurchaseRequestStatus effectiveStatus =
                    request.getStatus();

            // Hiển thị hết hạn nếu đã quá 48 giờ,
            // kể cả khi scheduled job chưa cập nhật DB.
            if (effectiveStatus == PurchaseRequestStatus.PENDING
                    && now.isAfter(request.getExpiresAt())) {

                effectiveStatus = PurchaseRequestStatus.EXPIRED;
            }

            boolean canWithdraw =
                    effectiveStatus == PurchaseRequestStatus.PENDING;

            items.add(
                    new SentPurchaseRequestItem(
                            request.getId(),
                            new PurchaseRequestResponse.ListingRef(
                                    listing.getId(),
                                    currentTitle,
                                    detailAccessible
                            ),
                            request.getOfferedPrice(),
                            effectiveStatus,
                            request.getCreatedAt().atOffset(ZoneOffset.UTC),
                            request.getExpiresAt().atOffset(ZoneOffset.UTC),
                            orderLink == null
                                    ? null
                                    : orderLink.orderId(),
                            canWithdraw
                    )
            );
        }

        return new SentPurchaseRequestPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }
}
