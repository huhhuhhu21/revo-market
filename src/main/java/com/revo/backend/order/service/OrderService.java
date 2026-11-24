package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.listing.entity.ListingStatus;
import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.order.dto.OrderCardResponse;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.repository.OrderRepository;
import com.revo.backend.purchase.dto.PurchaseRequestResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.revo.backend.order.dto.OrderDetailResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.revo.backend.listing.repository.ListingRepository;
import com.revo.backend.notification.entity.Notification;
import com.revo.backend.notification.repository.NotificationRepository;
import com.revo.backend.order.dto.CancelPendingResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class OrderService {

    private final OrderRepository orderRepository;


    private final ListingRepository listingRepository;
    private final NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderService(
            OrderRepository orderRepository,
            ListingRepository listingRepository,
            NotificationRepository notificationRepository
    ) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.notificationRepository = notificationRepository;
    }


    private record OrderUiConditions(
            boolean activeReturn,
            boolean blockingException,
            boolean cancelRequested
    ) {}

    @Transactional(readOnly = true)
    public List<OrderCardResponse> getOrders(
            Long currentUserId,
            String role,
            String status
    ) {
        // 1. Kiểm tra bộ lọc.
        String normalizedRole = normalizeRole(role);
        List<OrderStatus> statuses = resolveStatuses(status);

        // 2. Chỉ lấy đơn của người đang đăng nhập.
        List<Order> orders = orderRepository.findVisibleOrders(
                currentUserId,
                normalizedRole,
                statuses
        );

        if (orders.isEmpty()) {
            return List.of();
        }

        // 3. Kiểm tra các điều kiện thao tác.
        Map<Long, OrderUiConditions> conditions =
                loadUiConditions(orders);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<OrderCardResponse> result = new ArrayList<>();

        // 4. Chuyển Entity sang DTO.
        for (Order order : orders) {

            boolean isBuyer = order.getBuyer()
                    .getId().equals(currentUserId);

            String roleInOrder = isBuyer
                    ? "BUYER"
                    : "SELLER";

            OrderUiConditions condition = conditions.get(
                    order.getId()
            );

            if (condition == null) {
                throw new IllegalStateException(
                        "Missing UI conditions for order "
                                + order.getId()
                );
            }

            boolean canCancel = false;

            // Đơn chưa bàn giao: cả hai bên được hủy
            // nếu vẫn trong hạn chờ bàn giao.
            if (order.getStatus() == OrderStatus.PENDING_PICKUP) {

                canCancel = !now.isAfter(
                        order.getCreatedAt().plusHours(48)
                );

            } else if (
                    order.getStatus() == OrderStatus.IN_TRANSIT
            ) {

                // Khi đang vận chuyển, chỉ buyer
                // được gửi yêu cầu hủy.
                canCancel = isBuyer
                        && !condition.cancelRequested();
            }

            // Chỉ buyer được xác nhận hoàn thành
            // sau khi giao thành công, không có
            // yêu cầu trả hàng hay ngoại lệ đang chặn.
            boolean canConfirmCompletion =
                    isBuyer
                            && order.getStatus() == OrderStatus.DELIVERED
                            && order.getDeliveredAt() != null
                            && order.getCodStatus() == CodStatus.HELD
                            && !condition.activeReturn()
                            && !condition.blockingException();

            result.add(
                    new OrderCardResponse(
                            order.getId(),
                            buildListingRef(order, isBuyer),
                            roleInOrder,
                            order.getTransactionPrice(),
                            order.getStatus(),
                            order.getCodStatus(),
                            order.getCreatedAt()
                                    .atOffset(ZoneOffset.UTC),
                            order.getUpdatedAt()
                                    .atOffset(ZoneOffset.UTC),
                            canCancel,
                            canConfirmCompletion
                    )
            );
        }

        return result;
    }

    private String normalizeRole(String role) {

        String value = role == null
                ? "ALL"
                : role.trim().toUpperCase(Locale.ROOT);

        if (!value.equals("ALL")
                && !value.equals("BUYER")
                && !value.equals("SELLER")) {

            throw new BusinessException(
                    ErrorCode.INVALID_ROLE_FILTER
            );
        }

        return value;
    }

    private List<OrderStatus> resolveStatuses(String status) {

        String value = status == null
                ? "ALL"
                : status.trim().toUpperCase(Locale.ROOT);

        return switch (value) {

            case "ALL" -> List.of(OrderStatus.values());

            case "PROCESSING" -> List.of(
                    OrderStatus.PENDING_PICKUP,
                    OrderStatus.IN_TRANSIT,
                    OrderStatus.DELIVERED
            );

            case "COMPLETED" -> List.of(
                    OrderStatus.COMPLETED
            );

            case "CANCELED" -> List.of(
                    OrderStatus.CANCELED
            );

            case "RETURNED" -> List.of(
                    OrderStatus.RETURNED
            );

            default -> throw new BusinessException(
                    ErrorCode.INVALID_STATUS_FILTER
            );
        };
    }

    private Map<Long, OrderUiConditions> loadUiConditions(
            List<Order> orders
    ) {
        Map<Long, OrderUiConditions> result = new HashMap<>();

        List<Long> ids = orders.stream()
                .map(Order::getId)
                .toList();

        // Chia nhóm để tránh câu truy vấn IN quá dài.
        final int batchSize = 500;

        for (int start = 0; start < ids.size(); start += batchSize) {

            int end = Math.min(
                    start + batchSize,
                    ids.size()
            );

            List<Long> batch = ids.subList(start, end);

            List<Object[]> rows =
                    orderRepository.findOrderUiConditions(batch);

            for (Object[] row : rows) {

                Long orderId = ((Number) row[0]).longValue();

                result.put(
                        orderId,
                        new OrderUiConditions(
                                sqlTrue(row[1]),
                                sqlTrue(row[2]),
                                sqlTrue(row[3])
                        )
                );
            }
        }

        return result;
    }

    private static boolean sqlTrue(Object value) {

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        return "1".equals(String.valueOf(value));
    }

    private PurchaseRequestResponse.ListingRef buildListingRef(
            Order order,
            boolean isBuyer
    ) {
        ProductListing listing = order.getListing();

        boolean detailAccessible;

        if (!isBuyer) {

            // Chủ bài được xem bài của mình,
            // trừ khi bài đã xóa hoặc bị ADMIN ẩn.
            detailAccessible =
                    !listing.isDeleted()
                            && !listing.isAdminHidden();

        } else {

            User seller = order.getSeller();

            boolean publiclyAccessible =
                    seller.getStatus() == User.Status.ACTIVE
                            && (
                            listing.getStatus() == ListingStatus.AVAILABLE
                                    || listing.getStatus() == ListingStatus.SOLD
                    );

            // Buyer của đơn được xem bài đang giao dịch
            // liên quan đến chính mình.
            boolean transactionAccessible =
                    listing.getStatus()
                            == ListingStatus.IN_TRANSACTION
                            && (
                            order.getStatus() == OrderStatus.PENDING_PICKUP
                                    || order.getStatus() == OrderStatus.IN_TRANSIT
                                    || order.getStatus() == OrderStatus.DELIVERED
                    );

            detailAccessible =
                    !listing.isDeleted()
                            && !listing.isAdminHidden()
                            && listing.getHiddenReasonType() == null
                            && (
                            publiclyAccessible
                                    || transactionAccessible
                    );
        }

        return new PurchaseRequestResponse.ListingRef(
                listing.getId(),
                detailAccessible ? listing.getTitle() : null,
                detailAccessible
        );
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(
            Long currentUserId,
            String rawOrderId
    ) {
        // 1. Kiểm tra ID.
        Long orderId = parseOrderId(rawOrderId);

        // 2. Chỉ lấy đơn của buyer hoặc seller.
        Order order = orderRepository.findVisibleOrderDetail(
                orderId,
                currentUserId
        ).orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND
        ));

        boolean isBuyer = order.getBuyer()
                .getId().equals(currentUserId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 3. Đọc lịch sử vận chuyển.
        List<OrderDetailResponse.ShippingEvent> timeline =
                loadShippingTimeline(orderId);

        // 4. Đọc các điều kiện UI có sẵn từ C08.
        OrderUiConditions conditions =
                loadUiConditions(List.of(order)).get(orderId);

        if (conditions == null) {
            throw new IllegalStateException(
                    "Missing UI conditions for order " + orderId
            );
        }

        // 5. Kiểm tra xem đơn có yêu cầu trả hàng chưa.
        Long returnRequestId = orderRepository
                .findReturnRequestId(orderId)
                .orElse(null);

        // 6. Xác định tình trạng yêu cầu hủy khi vận chuyển.
        boolean cancellationRequested = false;
        boolean returnShippingCompleted = false;
        OffsetDateTime cancellationRequestedAt = null;

        for (OrderDetailResponse.ShippingEvent event : timeline) {

            if ("CANCEL_REQUESTED".equals(event.eventType())) {
                cancellationRequested = true;
                cancellationRequestedAt = event.recordedAt();
            }

            if ("RETURN_SHIPPING_COMPLETED".equals(
                    event.eventType())) {
                returnShippingCompleted = true;
            }
        }

        boolean returnShippingPending =
                order.getStatus() == OrderStatus.IN_TRANSIT
                        && cancellationRequested
                        && !returnShippingCompleted;

        // 7. Quyền hủy đơn: thống nhất với C08.
        boolean canCancel = false;

        if (order.getStatus() == OrderStatus.PENDING_PICKUP) {

            canCancel = !now.isAfter(
                    order.getCreatedAt().plusHours(48)
            );

        } else if (order.getStatus() == OrderStatus.IN_TRANSIT) {

            canCancel = isBuyer
                    && !conditions.cancelRequested();
        }

        // 8. Quyền xác nhận hoàn thành.
        boolean canConfirmCompletion =
                isBuyer
                        && order.getStatus() == OrderStatus.DELIVERED
                        && order.getDeliveredAt() != null
                        && order.getCodStatus() == CodStatus.HELD
                        && !conditions.activeReturn()
                        && !conditions.blockingException();

        // 9. Quyền yêu cầu trả hàng.
        // Đã COMPLETED sớm vẫn có thể trả trong 48 giờ.
        boolean returnableStatus =
                order.getStatus() == OrderStatus.DELIVERED
                        || order.getStatus() == OrderStatus.COMPLETED;

        boolean withinReturnWindow =
                order.getDeliveredAt() != null
                        && !now.isBefore(order.getDeliveredAt())
                        && !now.isAfter(
                        order.getDeliveredAt().plusHours(48)
                );

        boolean canRequestReturn =
                isBuyer
                        && order.getBuyer().getStatus() == User.Status.ACTIVE
                        && returnableStatus
                        && withinReturnWindow
                        && order.getCodStatus() == CodStatus.HELD
                        && returnRequestId == null
                        && !conditions.blockingException();

        // 10. Xác định người hủy nếu đã có.
        OrderDetailResponse.CancelActor canceledBy = null;

        if (order.getCanceledBy() != null) {

            Long canceledById = order.getCanceledBy().getId();

            String actorType;

            if (canceledById.equals(order.getBuyer().getId())) {
                actorType = "BUYER";
            } else if (
                    canceledById.equals(order.getSeller().getId())
            ) {
                actorType = "SELLER";
            } else {
                actorType = "ADMIN";
            }

            canceledBy = new OrderDetailResponse.CancelActor(
                    canceledById,
                    actorType
            );
        }

        // 11. Trả chi tiết đơn hàng.
        return new OrderDetailResponse(
                order.getId(),
                order.getPurchaseRequest().getId(),

                buildListingRef(order, isBuyer),

                new PurchaseRequestResponse.Party(
                        order.getBuyer().getId(),
                        order.getBuyer().getFullName()
                ),

                new PurchaseRequestResponse.Party(
                        order.getSeller().getId(),
                        order.getSeller().getFullName()
                ),

                order.getTransactionPrice(),
                order.getShippingFee(),
                order.getCodAmount(),

                order.getStatus(),
                order.getCodStatus(),

                new PurchaseRequestResponse.DeliveryContact(
                        order.getRecipientName(),
                        order.getRecipientPhone(),
                        order.getDeliveryAddressLine(),
                        order.getDeliveryProvince(),
                        order.getDeliveryWard()
                ),

                utc(order.getCreatedAt().plusHours(48)),
                utc(order.getPickedUpAt()),
                utc(order.getDeliveredAt()),
                utc(order.getCompletedAt()),

                utc(order.getCanceledAt()),
                order.getCancelReason(),
                canceledBy,

                utc(order.getCodCollectedAt()),
                utc(order.getCodSettledAt()),
                utc(order.getCodRefundedAt()),

                new OrderDetailResponse.Cancellation(
                        returnShippingPending,
                        cancellationRequestedAt
                ),

                timeline,
                returnRequestId,

                utc(order.getCreatedAt()),
                utc(order.getUpdatedAt()),

                canCancel,
                canConfirmCompletion,
                canRequestReturn
        );
    }

    private static Long parseOrderId(String rawOrderId) {

        if (rawOrderId == null
                || !rawOrderId.matches("[0-9]+")) {

            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_ID
            );
        }

        try {
            long orderId = Long.parseLong(rawOrderId);

            if (orderId <= 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_ORDER_ID
                );
            }

            return orderId;

        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_ID
            );
        }
    }

    private static OffsetDateTime utc(LocalDateTime value) {

        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }

    private List<OrderDetailResponse.ShippingEvent>
    loadShippingTimeline(Long orderId) {

        List<Object[]> rows =
                orderRepository.findShippingTimeline(orderId);

        List<OrderDetailResponse.ShippingEvent> events =
                new ArrayList<>();

        for (Object[] row : rows) {

            String eventType = row[0] == null
                    ? null
                    : row[0].toString();

            OffsetDateTime actualAt =
                    parseTimelineTime(row[1]);

            OffsetDateTime recordedAt =
                    parseTimelineTime(row[2]);

            String actorType = row[3] == null
                    ? null
                    : row[3].toString();

            events.add(
                    new OrderDetailResponse.ShippingEvent(
                            eventType,
                            actualAt,
                            recordedAt,
                            actorType
                    )
            );
        }

        return events;
    }

    private static OffsetDateTime parseTimelineTime(
            Object value
    ) {
        if (value == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(
                    value.toString()
            ).withOffsetSameInstant(ZoneOffset.UTC);

        } catch (DateTimeParseException ex) {
            throw new IllegalStateException(
                    "Invalid shipping timeline timestamp",
                    ex
            );
        }
    }

    public record CancelResult(
            OrderDetailResponse detail,
            CancelPendingResponse pending
    ) {}


    @Transactional
    public CancelResult cancelOrder(
            Long currentUserId,
            String rawOrderId,
            String rawReason
    ) {
        // 1. Kiểm tra dữ liệu đầu vào.
        Long orderId = parseOrderId(rawOrderId);

        if (rawReason == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        String reason = rawReason.trim();

        if (reason.isEmpty() || reason.length() > 2000) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Lấy listingId trước để khóa đúng thứ tự.
        Long listingId = orderRepository
                .findListingIdByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        ProductListing listing = listingRepository
                .findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 3. Khóa bản ghi đơn hàng.
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 4. Kiểm tra người tham gia.
        boolean isBuyer = order.getBuyer().getId()
                .equals(currentUserId);

        boolean isSeller = order.getSeller().getId()
                .equals(currentUserId);

        if (!isBuyer && !isSeller) {
            // Tránh tiết lộ đơn hàng của người khác.
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_FOUND
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);

        String actorType = isBuyer ? "BUYER" : "SELLER";
        User actor = isBuyer
                ? order.getBuyer()
                : order.getSeller();

        // 5. Nhánh A: Chưa bàn giao hàng.
        if (order.getStatus() == OrderStatus.PENDING_PICKUP) {

            if (now.isAfter(order.getCreatedAt().plusHours(48))
                    || order.getPickedUpAt() != null
                    || order.getCodStatus()
                    != CodStatus.NOT_COLLECTED) {
                throw new BusinessException(
                        ErrorCode.ORDER_NOT_CANCELLABLE
                );
            }

            // Trạng thái bài phải nhất quán với đơn đang hoạt động.
            if (listing.getStatus()
                    != ListingStatus.IN_TRANSACTION) {
                throw new BusinessException(
                        ErrorCode.ORDER_NOT_CANCELLABLE
                );
            }

            // Đơn không còn chiếm sản phẩm.
            order.cancelBeforePickup(actor, reason, now);
            listing.releaseAfterCancellation();

            orderRepository.saveAndFlush(order);
            listingRepository.saveAndFlush(listing);

            appendCancelEvent(
                    orderId,
                    "ORDER_CANCELED",
                    actorType,
                    currentUserId,
                    reason,
                    now
            );

            User recipient = isBuyer
                    ? order.getSeller()
                    : order.getBuyer();

            notificationRepository.save(
                    Notification.orderCancellation(
                            recipient,
                            orderId,
                            false
                    )
            );

            entityManager.refresh(order);

            return new CancelResult(
                    getOrderDetail(currentUserId, rawOrderId),
                    null
            );
        }

        // 6. Nhánh B: Hàng đang vận chuyển.
        if (order.getStatus() == OrderStatus.IN_TRANSIT) {

            if (!isBuyer) {
                throw new BusinessException(
                        ErrorCode.SELLER_CANNOT_CANCEL_IN_TRANSIT
                );
            }

            if (order.getPickedUpAt() == null
                    || order.getCodStatus()
                    != CodStatus.NOT_COLLECTED) {
                throw new BusinessException(
                        ErrorCode.ORDER_NOT_CANCELLABLE
                );
            }

            // Retry: không tạo thêm CANCEL_REQUESTED.
            List<Object[]> previous =
                    orderRepository.findCancellationRequests(orderId);

            if (!previous.isEmpty()) {
                Object[] event = previous.get(0);

                String previousReason = (String) event[1];

                if (!Objects.equals(reason, previousReason)) {
                    throw new BusinessException(
                            ErrorCode.CANCELLATION_ALREADY_PENDING
                    );
                }

                OffsetDateTime requestedAt =
                        OffsetDateTime.parse(
                                event[0].toString()
                        );

                return new CancelResult(
                        null,
                        pendingResponse(orderId, requestedAt)
                );
            }

            // Ghi nhận yêu cầu, KHÔNG chuyển đơn sang CANCELED.
            OffsetDateTime requestedAt =
                    now.atOffset(ZoneOffset.UTC);

            appendCancelEvent(
                    orderId,
                    "CANCEL_REQUESTED",
                    "BUYER",
                    currentUserId,
                    reason,
                    now
            );

            notificationRepository.save(
                    Notification.orderCancellation(
                            order.getSeller(),
                            orderId,
                            true
                    )
            );

            return new CancelResult(
                    null,
                    pendingResponse(orderId, requestedAt)
            );
        }

        // 7. Các trạng thái còn lại không thể hủy bằng C10.
        throw new BusinessException(
                ErrorCode.ORDER_NOT_CANCELLABLE
        );
    }

    private void appendCancelEvent(
            Long orderId,
            String eventType,
            String actorType,
            Long actorId,
            String reason,
            LocalDateTime now
    ) {
        String isoAt = now.atOffset(ZoneOffset.UTC)
                .toString();

        int updated = orderRepository.appendShippingEvent(
                orderId,
                eventType,
                isoAt,
                actorType,
                actorId,
                reason,
                now
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not append order event: " + orderId
            );
        }
    }

    private CancelPendingResponse pendingResponse(
            Long orderId,
            OffsetDateTime requestedAt
    ) {
        return new CancelPendingResponse(
                orderId,
                OrderStatus.IN_TRANSIT,
                "RETURN_SHIPPING_PENDING",
                requestedAt,
                "Yêu cầu hủy đã được ghi nhận. "
                        + "Đơn chỉ hủy khi hàng được chuyển về người bán."
        );
    }


}