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
import com.revo.backend.purchase.dto.SentPurchaseRequestItem;
import com.revo.backend.purchase.dto.ReceivedPurchaseRequestItem;
import com.revo.backend.purchase.entity.PurchaseRequestStatus;

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
    public List<SentPurchaseRequestItem> getSentRequests(
            Long buyerId,
            String status
    ) {
        PurchaseRequestStatus filter = parseStatusFilter(status);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<PurchaseRequest> requests =
                purchaseRequestRepository
                        .findByBuyer_IdOrderByCreatedAtDescIdDesc(buyerId);

        Map<Long, OrderLink> orderLinks = loadOrderLinks(requests);

        List<SentPurchaseRequestItem> result = new ArrayList<>();

        for (PurchaseRequest request : requests) {

            PurchaseRequestStatus effective =
                    effectiveStatus(request, now);

            if (filter != null && filter != effective) {
                continue;
            }

            OrderLink orderLink = orderLinks.get(request.getId());

            result.add(new SentPurchaseRequestItem(
                    request.getId(),
                    listingRefForBuyer(request, orderLink),
                    request.getOfferedPrice(),
                    effective,
                    request.getCreatedAt().atOffset(ZoneOffset.UTC),
                    request.getExpiresAt().atOffset(ZoneOffset.UTC),
                    orderLink == null ? null : orderLink.orderId(),
                    effective == PurchaseRequestStatus.PENDING
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<ReceivedPurchaseRequestItem> getReceivedRequests(
            Long sellerId,
            String status
    ) {
        PurchaseRequestStatus filter = parseStatusFilter(status);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<PurchaseRequest> requests =
                purchaseRequestRepository
                        .findByListing_Seller_IdOrderByCreatedAtDescIdDesc(
                                sellerId
                        );

        Map<Long, OrderLink> orderLinks = loadOrderLinks(requests);

        List<ReceivedPurchaseRequestItem> result = new ArrayList<>();

        for (PurchaseRequest request : requests) {

            PurchaseRequestStatus effective =
                    effectiveStatus(request, now);

            if (filter != null && effective != filter) {
                continue;
            }

            ProductListing listing = request.getListing();
            User seller = listing.getSeller();
            User buyer = request.getBuyer();

            boolean listingAvailable =
                    listing.getStatus() == ListingStatus.AVAILABLE
                            && !listing.isDeleted()
                            && !listing.isAdminHidden()
                            && listing.getHiddenReasonType() == null;

            boolean sellerEligible = isEligible(seller);
            boolean buyerEligible = isEligible(buyer);

            boolean pending =
                    effective == PurchaseRequestStatus.PENDING;

            boolean canReject =
                    pending
                            && sellerEligible
                            && listingAvailable;

            boolean canAccept =
                    canReject
                            && buyerEligible;

            OrderLink orderLink = orderLinks.get(request.getId());

            boolean detailAccessible = !listing.isDeleted();

            result.add(new ReceivedPurchaseRequestItem(
                    request.getId(),
                    new PurchaseRequestResponse.ListingRef(
                            listing.getId(),
                            detailAccessible ? listing.getTitle() : null,
                            detailAccessible
                    ),
                    new PurchaseRequestResponse.Party(
                            buyer.getId(),
                            buyer.getFullName()
                    ),
                    request.getOfferedPrice(),
                    effective,
                    request.getCreatedAt().atOffset(ZoneOffset.UTC),
                    request.getExpiresAt().atOffset(ZoneOffset.UTC),
                    orderLink == null ? null : orderLink.orderId(),
                    canAccept,
                    canReject
            ));
        }

        return result;
    }


    private static PurchaseRequestStatus parseStatusFilter(String raw) {

        String normalized = raw == null
                ? "ALL"
                : raw.trim().toUpperCase(Locale.ROOT);

        if ("ALL".equals(normalized)) {
            return null;
        }

        try {
            return PurchaseRequestStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_FILTER
            );
        }
    }

    private static PurchaseRequestStatus effectiveStatus(
            PurchaseRequest request,
            LocalDateTime now
    ) {
        if (request.getStatus() == PurchaseRequestStatus.PENDING
                && now.isAfter(request.getExpiresAt())) {

            return PurchaseRequestStatus.EXPIRED;
        }

        return request.getStatus();
    }

    private Map<Long, OrderLink> loadOrderLinks(
            List<PurchaseRequest> requests
    ) {
        Map<Long, OrderLink> links = new HashMap<>();

        if (requests.isEmpty()) {
            return links;
        }

        List<Long> requestIds = requests.stream()
                .map(PurchaseRequest::getId)
                .toList();

        // Chia nhóm để tránh truyền danh sách ID quá lớn vào IN (...).
        final int batchSize = 500;

        for (int start = 0; start < requestIds.size(); start += batchSize) {

            int end = Math.min(start + batchSize, requestIds.size());

            List<Long> batch = requestIds.subList(start, end);

            for (Object[] row :
                    purchaseRequestRepository.findOrderLinks(batch)) {

                Long requestId = ((Number) row[0]).longValue();
                Long orderId = ((Number) row[1]).longValue();
                String orderStatus = row[2].toString();

                links.put(
                        requestId,
                        new OrderLink(orderId, orderStatus)
                );
            }
        }

        return links;
    }

    private PurchaseRequestResponse.ListingRef listingRefForBuyer(
            PurchaseRequest request,
            OrderLink orderLink
    ) {
        ProductListing listing = request.getListing();

        User seller = listing.getSeller();

        boolean publiclyAccessible =
                seller.getStatus() == User.Status.ACTIVE
                        && seller.isEmailVerified()
                        && seller.getPhone() != null
                        && !seller.getPhone().isBlank()
                        && (
                        listing.getStatus() == ListingStatus.AVAILABLE
                                || listing.getStatus() == ListingStatus.SOLD
                );

        boolean activeOrder =
                orderLink != null
                        && (
                        "PENDING_PICKUP".equals(orderLink.status())
                                || "IN_TRANSIT".equals(orderLink.status())
                                || "DELIVERED".equals(orderLink.status())
                );

        boolean transactionAccessible =
                listing.getStatus() == ListingStatus.IN_TRANSACTION
                        && activeOrder;

        boolean detailAccessible =
                !listing.isDeleted()
                        && !listing.isAdminHidden()
                        && listing.getHiddenReasonType() == null
                        && (publiclyAccessible || transactionAccessible);

        return new PurchaseRequestResponse.ListingRef(
                listing.getId(),
                detailAccessible ? listing.getTitle() : null,
                detailAccessible
        );
    }
}
