
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminPickupRequest;
import com.revo.backend.order.dto.AdminPickupResponse;
import com.revo.backend.order.dto.OrderDetailResponse;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderStatus;
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
public class AdminPickupService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminPickupService(
            OrderRepository orderRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AdminPickupResponse pickupOrder(
            Long adminId,
            String rawOrderId,
            AdminPickupRequest request
    ) {
        // 1. Kiểm tra dữ liệu đầu vào.
        Long orderId = parseOrderId(rawOrderId);

        if (request == null || request.actualAt() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Kiểm tra ADMIN theo trạng thái hiện tại.
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

        // 3. Khóa đơn để tránh tiếp nhận hoặc hủy đồng thời.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 4. Chuẩn hóa hai thời điểm về UTC, chính xác ms.
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
                    ErrorCode.PICKUP_TIME_INVALID
            );
        }

        // 5. Idempotency: tiếp nhận trước đó.
        if (order.getPickedUpAt() != null) {

            if (Objects.equals(
                    order.getPickedUpAt(),
                    actualAt
            )) {
                return buildResponse(order);
            }

            throw new BusinessException(
                    ErrorCode.PICKUP_ALREADY_RECORDED
            );
        }

        // 6. Kiểm tra trạng thái đơn và COD.
        if (order.getStatus() != OrderStatus.PENDING_PICKUP
                || order.getCodStatus() != CodStatus.NOT_COLLECTED
                || order.getCanceledAt() != null) {

            throw new BusinessException(
                    ErrorCode.ORDER_NOT_READY_FOR_PICKUP
            );
        }

        // 7. Không tiếp nhận khi người tham gia bị hạn chế.
        if (order.getBuyer().getStatus() != User.Status.ACTIVE
                || order.getSeller().getStatus() != User.Status.ACTIVE) {

            throw new BusinessException(
                    ErrorCode.PARTICIPANT_NOT_ELIGIBLE
            );
        }

        // 8. Kiểm tra thời điểm tiếp nhận.
        if (actualAt.isBefore(order.getCreatedAt())) {
            throw new BusinessException(
                    ErrorCode.PICKUP_TIME_INVALID
            );
        }

        if (actualAt.isAfter(
                order.getCreatedAt().plusHours(48)
        )) {
            throw new BusinessException(
                    ErrorCode.PICKUP_DEADLINE_EXPIRED
            );
        }

        // 9. Chuẩn hóa ghi chú.
        String note = request.note();

        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }

        // 10. Ghi nhận tiếp nhận và chuyển sang IN_TRANSIT.
        order.recordPickedUp(actualAt, recordedAt);
        orderRepository.saveAndFlush(order);

        // 11. Ghi sự kiện trong cùng transaction.
        String actualIso =
                actualAt.atOffset(ZoneOffset.UTC).toString();

        String recordedIso =
                recordedAt.atOffset(ZoneOffset.UTC).toString();

        int updated = orderRepository.appendAdminPickupEvent(
                orderId,
                actualIso,
                recordedIso,
                adminId,
                note,
                recordedAt
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not append pickup event: " + orderId
            );
        }

        // Đồng bộ entity sau khi dùng native UPDATE.
        entityManager.refresh(order);

        return buildResponse(order);
    }

    private AdminPickupResponse buildResponse(Order order) {

        Long orderId = order.getId();

        // Tái sử dụng truy vấn điều kiện UI của C08/D03.
        List<Object[]> conditions =
                orderRepository.findOrderUiConditions(
                        List.of(orderId)
                );

        if (conditions.size() != 1) {
            throw new IllegalStateException(
                    "Missing conditions for order " + orderId
            );
        }

        Object[] flags = conditions.get(0);

        // Đọc lịch sử hiện tại của đơn.
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

        return new AdminPickupResponse(
                orderId,
                order.getBuyer().getId(),
                order.getSeller().getId(),
                order.getStatus(),
                order.getCodStatus(),
                order.getTransactionPrice(),
                utc(order.getPickedUpAt()),
                utc(order.getDeliveredAt()),
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
            // Chuyển thành mã lỗi API chuẩn bên dưới.
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

    private static OffsetDateTime parseTime(Object value) {
        return value == null
                ? null
                : OffsetDateTime.parse(
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
