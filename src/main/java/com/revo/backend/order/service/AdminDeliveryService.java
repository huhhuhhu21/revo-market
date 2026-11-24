
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.notification.entity.Notification;
import com.revo.backend.notification.repository.NotificationRepository;
import com.revo.backend.order.dto.AdminDeliveryRequest;
import com.revo.backend.order.dto.AdminDeliveryResponse;
import com.revo.backend.order.dto.OrderDetailResponse;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderException;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.repository.OrderExceptionRepository;
import com.revo.backend.order.repository.OrderRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
public class AdminDeliveryService {

    private final OrderRepository orderRepository;
    private final OrderExceptionRepository exceptionRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminDeliveryService(
            OrderRepository orderRepository,
            OrderExceptionRepository exceptionRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository
    ) {
        this.orderRepository = orderRepository;
        this.exceptionRepository = exceptionRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    public record DeliveryOutcome(
            AdminDeliveryResponse delivered,
            Long exceptionId
    ) {}

    @Transactional
    public DeliveryOutcome deliver(
            Long adminId,
            String rawOrderId,
            AdminDeliveryRequest request
    ) {
        Long orderId = parseOrderId(rawOrderId);

        if (request == null || request.actualAt() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 1. Kiểm tra ADMIN đang hoạt động.
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

        // 2. Khóa đơn để đồng bộ với D04/C10.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        LocalDateTime recordedAt =
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

        LocalDateTime actualAt =
                request.actualAt()
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.MILLIS);

        if (actualAt.isAfter(recordedAt)) {
            throw new BusinessException(
                    ErrorCode.DELIVERY_TIME_INVALID
            );
        }

        // 3. Retry: đơn đã ghi nhận giao thành công.
        if (order.getDeliveredAt() != null) {

            if (Objects.equals(
                    order.getDeliveredAt(),
                    actualAt
            )) {
                return new DeliveryOutcome(
                        buildResponse(order),
                        null
                );
            }

            throw new BusinessException(
                    ErrorCode.DELIVERY_ALREADY_RECORDED
            );
        }

        // 4. Retry: đã báo giao muộn trước đó.
        List<Object[]> lateReports =
                orderRepository.findLateDeliveryReports(orderId);

        if (!lateReports.isEmpty()) {
            Object[] previous = lateReports.get(0);

            LocalDateTime previousActualAt =
                    OffsetDateTime.parse(
                            previous[0].toString()
                    ).withOffsetSameInstant(
                            ZoneOffset.UTC
                    ).toLocalDateTime();

            if (Objects.equals(previousActualAt, actualAt)) {
                Long exceptionId =
                        ((Number) previous[1]).longValue();

                return new DeliveryOutcome(
                        null,
                        exceptionId
                );
            }

            throw new BusinessException(
                    ErrorCode.DELIVERY_ALREADY_RECORDED
            );
        }

        // 5. Kiểm tra trạng thái.
        if (order.getStatus() != OrderStatus.IN_TRANSIT
                || order.getPickedUpAt() == null
                || order.getCodStatus() != CodStatus.NOT_COLLECTED
                || order.getCodCollectedAt() != null
                || order.getCanceledAt() != null) {

            throw new BusinessException(
                    ErrorCode.ORDER_NOT_READY_FOR_DELIVERY
            );
        }

        if (actualAt.isBefore(order.getPickedUpAt())) {
            throw new BusinessException(
                    ErrorCode.DELIVERY_TIME_INVALID
            );
        }

        // 6. Kiểm tra yêu cầu hủy và ngoại lệ.
        List<Object[]> conditionRows =
                orderRepository.findOrderUiConditions(
                        List.of(orderId)
                );

        if (conditionRows.size() != 1) {
            throw new IllegalStateException(
                    "Missing conditions for order " + orderId
            );
        }

        Object[] conditions = conditionRows.get(0);

        if (sqlTrue(conditions[3])) {
            throw new BusinessException(
                    ErrorCode.DELIVERY_HAS_PENDING_CANCELLATION
            );
        }

        if (sqlTrue(conditions[2]) || sqlTrue(conditions[1])) {
            throw new BusinessException(
                    ErrorCode.DELIVERY_HAS_BLOCKING_EXCEPTION
            );
        }

        // 7. Chuẩn hóa ghi chú.
        String note = request.note();

        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }

        String actualIso = utc(actualAt).toString();
        String recordedIso = utc(recordedAt).toString();

        // 8. Trường hợp ghi nhận muộn hơn 48 giờ.
        if (recordedAt.isAfter(actualAt.plusHours(48))) {

            OrderException exception =
                    exceptionRepository.saveAndFlush(
                            OrderException.lateDelivery(
                                    order,
                                    admin,
                                    actualAt,
                                    recordedAt
                            )
                    );

            int updated =
                    orderRepository.appendLateDeliveryReportEvent(
                            orderId,
                            actualIso,
                            recordedIso,
                            adminId,
                            exception.getId(),
                            note,
                            recordedAt
                    );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Cannot record late delivery"
                );
            }

            // Không chuyển DELIVERED và không thu COD.
            // Trả kết quả để controller phát HTTP 409
            // SAU KHI transaction đã commit.
            return new DeliveryOutcome(
                    null,
                    exception.getId()
            );
        }

        // 9. Giao hàng thành công bình thường.
        order.recordDelivered(actualAt, recordedAt);
        orderRepository.saveAndFlush(order);

        int updated =
                orderRepository.appendAdminDeliveryEvent(
                        orderId,
                        actualIso,
                        recordedIso,
                        adminId,
                        note,
                        recordedAt
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot append delivery event"
            );
        }

        // Đồng bộ entity sau native UPDATE.
        entityManager.refresh(order);

        // 10. Thông báo cho hai bên, chỉ khi thành công.
        notificationRepository.saveAll(
                List.of(
                        Notification.orderDelivered(
                                order.getBuyer(),
                                orderId
                        ),
                        Notification.orderDelivered(
                                order.getSeller(),
                                orderId
                        )
                )
        );

        return new DeliveryOutcome(
                buildResponse(order),
                null
        );
    }

    private AdminDeliveryResponse buildResponse(Order order) {
        Long orderId = order.getId();

        List<Object[]> rows =
                orderRepository.findOrderUiConditions(
                        List.of(orderId)
                );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "Missing UI conditions for " + orderId
            );
        }

        Object[] flags = rows.get(0);

        List<OrderDetailResponse.ShippingEvent> timeline =
                new ArrayList<>();

        for (Object[] row :
                orderRepository.findShippingTimeline(orderId)) {

            timeline.add(
                    new OrderDetailResponse.ShippingEvent(
                            row[0] == null
                                    ? null : row[0].toString(),
                            parseTime(row[1]),
                            parseTime(row[2]),
                            row[3] == null
                                    ? null : row[3].toString()
                    )
            );
        }

        return new AdminDeliveryResponse(
                orderId,
                order.getBuyer().getId(),
                order.getSeller().getId(),
                order.getStatus(),
                order.getCodStatus(),
                order.getTransactionPrice(),
                order.getCodAmount(),
                utc(order.getPickedUpAt()),
                utc(order.getDeliveredAt()),
                utc(order.getCodCollectedAt()),
                sqlTrue(flags[3]),
                sqlTrue(flags[2]),
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
            long id = Long.parseLong(raw);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Chuyển thành lỗi API chuẩn.
        }

        throw new BusinessException(
                ErrorCode.INVALID_ORDER_ID
        );
    }

    private static OffsetDateTime utc(LocalDateTime time) {
        return time == null
                ? null
                : time.atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }

        return OffsetDateTime.parse(
                value.toString()
        ).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static boolean sqlTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        return false;
    }
}
