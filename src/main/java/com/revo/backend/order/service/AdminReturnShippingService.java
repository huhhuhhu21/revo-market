
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.listing.entity.ListingStatus;
import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.listing.repository.ListingRepository;
import com.revo.backend.notification.entity.Notification;
import com.revo.backend.notification.repository.NotificationRepository;
import com.revo.backend.order.dto.AdminReturnShippingRequest;
import com.revo.backend.order.dto.AdminReturnShippingResponse;
import com.revo.backend.order.dto.OrderDetailResponse;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AdminReturnShippingService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public AdminReturnShippingService(
            OrderRepository orderRepository,
            ListingRepository listingRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository
    ) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public AdminReturnShippingResponse complete(
            Long adminId,
            String rawOrderId,
            AdminReturnShippingRequest request
    ) {
        // 1. Kiểm tra dữ liệu đầu vào.
        Long orderId = parseOrderId(rawOrderId);

        if (request == null || request.actualAt() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Kiểm tra quyền ADMIN hiện tại.
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FORBIDDEN
                ));

        if (admin.getRole() != User.Role.ADMIN
                || admin.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        // 3. Tra cứu listingId trước khi khóa.
        Long listingId = orderRepository
                .findListingIdByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 4. Khóa Listing trước, Order sau:
        // cùng thứ tự với OrderService.cancelOrder().
        ProductListing listing = listingRepository
                .findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 5. Chuẩn hóa thời gian về UTC, mili giây.
        LocalDateTime recordedAt =
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

        LocalDateTime actualAt = request.actualAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MILLIS);

        if (actualAt.isAfter(recordedAt)) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_TIME_INVALID
            );
        }

        // 6. Kiểm tra sự kiện hoàn tất trước đó.
        List<String> completedTimes =
                orderRepository.findReturnShippingCompletedTimes(
                        orderId
                );

        if (order.getStatus() == OrderStatus.CANCELED) {
            if (!completedTimes.isEmpty()
                    && Objects.equals(
                    parseIsoTime(completedTimes.get(0)),
                    actualAt
            )) {
                // Retry cùng thời điểm:
                // không cập nhật hay gửi thông báo lần hai.
                return buildResponse(order, listing);
            }

            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_ALREADY_COMPLETED
            );
        }

        // Có sự kiện hoàn tất nhưng đơn chưa CANCELED:
        // dữ liệu không nhất quán, không tự sửa.
        if (!completedTimes.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_NOT_READY
            );
        }

        // 7. Chỉ xử lý đơn IN_TRANSIT, có tiếp nhận hàng.
        if (order.getStatus() != OrderStatus.IN_TRANSIT
                || order.getPickedUpAt() == null
                || order.getCanceledAt() != null) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_NOT_READY
            );
        }

        if (actualAt.isBefore(order.getPickedUpAt())) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_TIME_INVALID
            );
        }

        // 8. Phải có sự kiện CANCEL_REQUESTED từ C10.
        List<Object[]> cancellationRows =
                orderRepository.findReturnShippingCancellationBasis(
                        orderId
                );

        if (cancellationRows.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_CANCEL_BASIS_MISSING
            );
        }

        Object[] cancellation = cancellationRows.get(0);

        String actorType =
                cancellation[0] == null
                        ? null
                        : cancellation[0].toString();

        Long originalActorId =
                cancellation[1] instanceof Number number
                        ? number.longValue()
                        : null;

        String cancelReason =
                cancellation[2] == null
                        ? null
                        : cancellation[2].toString();

        LocalDateTime cancelRequestedAt =
                cancellation[3] == null
                        ? null
                        : parseIsoTime(cancellation[3].toString());

        // C10 hiện chỉ cho BUYER yêu cầu hủy IN_TRANSIT.
        // Không nhận sự kiện thiếu người khởi tạo.
        if (!"BUYER".equals(actorType)
                || !order.getBuyer().getId()
                .equals(originalActorId)
                || cancelReason == null
                || cancelReason.isBlank()
                || cancelRequestedAt == null) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_CANCEL_BASIS_MISSING
            );
        }

        // Không ghi nhận hàng về trước khi yêu cầu hủy.
        if (actualAt.isBefore(cancelRequestedAt)) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_TIME_INVALID
            );
        }

        // Giữ chính xác người khởi tạo và lý do gốc.
        User cancellationActor = order.getBuyer();

        // 10. Chuẩn hóa ghi chú.
        String note = request.note();

        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }

        String actualIso =
                actualAt.atOffset(ZoneOffset.UTC).toString();

        String recordedIso =
                recordedAt.atOffset(ZoneOffset.UTC).toString();

        // 12. Có dấu vết giao/thu COD:
        // tạo ngoại lệ DELIVERY và trả 409 sau commit.
        boolean deliveryConflict =
                order.getDeliveredAt() != null
                        || order.getCodCollectedAt() != null
                        || order.getCodSettledAt() != null
                        || order.getCodRefundedAt() != null
                        || order.getCodStatus()
                        != CodStatus.NOT_COLLECTED
                        || orderRepository.countDeliveryEvidence(
                        orderId
                ) > 0;

        if (deliveryConflict) {
            // Không tạo ngoại lệ: từ chối ngay, đơn/COD không thay đổi.
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_DELIVERY_CONFLICT
            );
        }

        // 13. Kiểm tra bài gắn với đơn.
        if (listing.getStatus()
                != ListingStatus.IN_TRANSACTION) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_NOT_READY
            );
        }

        // Đơn IN_TRANSIT không được có return_requests.
        if (orderRepository.findReturnRequestId(orderId)
                .isPresent()) {
            throw new BusinessException(
                    ErrorCode.RETURN_SHIPPING_NOT_READY
            );
        }

        // 14. Cập nhật đơn và giải phóng bài.
        order.completeReturnShippingCancellation(
                cancellationActor,
                cancelReason,
                recordedAt
        );

        listing.releaseAfterCancellation();

        orderRepository.saveAndFlush(order);
        listingRepository.saveAndFlush(listing);

        // 15. Ghi lịch sử trong cùng transaction.
        int updated =
                orderRepository.appendReturnShippingCompletedEvent(
                        orderId,
                        actualIso,
                        recordedIso,
                        adminId,
                        cancelReason,
                        note,
                        recordedAt
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot record return shipping completion"
            );
        }

        // 16. Thông báo cho hai bên và ADMIN ACTIVE.
        List<Notification> notifications = new ArrayList<>();

        notifications.add(
                Notification.returnShippingCompleted(
                        order.getBuyer(),
                        orderId,
                        false
                )
        );

        notifications.add(
                Notification.returnShippingCompleted(
                        order.getSeller(),
                        orderId,
                        false
                )
        );

        List<User> admins = userRepository
                .findAllByRoleAndStatus(
                        User.Role.ADMIN,
                        User.Status.ACTIVE
                );

        for (User recipient : admins) {
            notifications.add(
                    Notification.returnShippingCompleted(
                            recipient,
                            orderId,
                            true
                    )
            );
        }

        notificationRepository.saveAll(notifications);

        // 17. Trả dữ liệu sau khi hoàn tất.
        return buildResponse(order, listing);
    }

    private AdminReturnShippingResponse buildResponse(
            Order order,
            ProductListing listing
    ) {
        List<OrderDetailResponse.ShippingEvent> timeline =
                new ArrayList<>();

        for (Object[] row : orderRepository.findShippingTimeline(
                order.getId()
        )) {
            timeline.add(
                    new OrderDetailResponse.ShippingEvent(
                            row[0] == null
                                    ? null
                                    : row[0].toString(),
                            parseNullableIso(row[1]),
                            parseNullableIso(row[2]),
                            row[3] == null
                                    ? null
                                    : row[3].toString()
                    )
            );
        }

        boolean publiclyVisible =
                listing.getStatus() == ListingStatus.AVAILABLE
                        && !listing.isAdminHidden()
                        && !listing.isDeleted()
                        && listing.getSeller().getStatus()
                        == User.Status.ACTIVE;

        return new AdminReturnShippingResponse(
                order.getId(),
                order.getBuyer().getId(),
                order.getSeller().getId(),
                listing.getId(),

                order.getStatus(),
                order.getCodStatus(),
                order.getTransactionPrice(),

                utc(order.getPickedUpAt()),
                utc(order.getDeliveredAt()),
                utc(order.getCanceledAt()),

                order.getCanceledBy() == null
                        ? null
                        : order.getCanceledBy().getId(),

                order.getCancelReason(),

                listing.getStatus(),
                publiclyVisible,

                utc(order.getCreatedAt()),
                utc(order.getUpdatedAt()),

                timeline
        );
    }

    private static Long parseOrderId(String raw) {
        if (raw == null || !raw.matches("[0-9]+")) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_ID
            );
        }

        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Chuyển thành mã lỗi API chuẩn.
        }

        throw new BusinessException(
                ErrorCode.INVALID_ORDER_ID
        );
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }

    private static LocalDateTime parseIsoTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "Invalid shipping history timestamp"
            );
        }

        return OffsetDateTime.parse(raw)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MILLIS);
    }

    private static OffsetDateTime parseNullableIso(Object value) {
        return value == null
                ? null
                : parseIsoTime(value.toString())
                .atOffset(ZoneOffset.UTC);
    }
}
