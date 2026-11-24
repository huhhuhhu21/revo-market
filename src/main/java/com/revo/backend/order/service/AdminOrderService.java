
package com.revo.backend.order.service;

import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.order.dto.AdminOrderPageResponse;
import com.revo.backend.order.dto.AdminOrderPageResponse.AdminOrderItem;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.repository.OrderRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AdminOrderService(
            OrderRepository orderRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse getAdminOrders(
            Long adminId,
            int page,
            int size,
            String status,
            String codStatus,
            String orderId
    ) {

        // Kiểm tra ADMIN dựa trên dữ liệu hiện tại.
        User admin = userRepository.findById(adminId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.FORBIDDEN)
                );

        if (admin.getRole() != User.Role.ADMIN
                || admin.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Giới hạn phân trang để tránh truy vấn quá lớn.
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGINATION
            );
        }

        OrderStatus orderStatus = parseEnum(
                status,
                OrderStatus.class
        );

        CodStatus cod = parseEnum(
                codStatus,
                CodStatus.class
        );

        Long id = parseOrderId(orderId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<Order> orders = orderRepository.findAdminOrders(
                id,
                orderStatus,
                cod,
                pageable
        );

        // Tận dụng truy vấn điều kiện UI đã có từ C08.
        Map<Long, Object[]> conditions = new HashMap<>();

        if (!orders.isEmpty()) {
            List<Long> ids = orders.getContent()
                    .stream()
                    .map(Order::getId)
                    .toList();

            for (Object[] row :
                    orderRepository.findOrderUiConditions(ids)) {

                Long foundId = ((Number) row[0]).longValue();
                conditions.put(foundId, row);
            }
        }

        List<AdminOrderItem> items = orders.getContent()
                .stream()
                .map(order -> {
                    Object[] flags = conditions.get(order.getId());

                    if (flags == null) {
                        throw new IllegalStateException(
                                "Missing conditions for order "
                                        + order.getId()
                        );
                    }

                    return new AdminOrderItem(
                            order.getId(),
                            order.getBuyer().getId(),
                            order.getSeller().getId(),
                            order.getStatus(),
                            order.getCodStatus(),
                            order.getTransactionPrice(),
                            toUtc(order.getPickedUpAt()),
                            toUtc(order.getDeliveredAt()),
                            isTrue(flags[3]),
                            isTrue(flags[2]),
                            toUtc(order.getCreatedAt())
                    );
                })
                .toList();

        return new AdminOrderPageResponse(
                items,
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.hasNext()
        );
    }

    private static <E extends Enum<E>> E parseEnum(
            String raw,
            Class<E> enumType
    ) {
        if (raw == null
                || raw.isBlank()
                || raw.trim().equalsIgnoreCase("ALL")) {
            return null;
        }

        try {
            return Enum.valueOf(
                    enumType,
                    raw.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    enumType == OrderStatus.class
                            ? ErrorCode.INVALID_STATUS_FILTER
                            : ErrorCode.INVALID_FILTER
            );
        }
    }

    private static Long parseOrderId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            long id = Long.parseLong(raw.trim());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Xử lý bằng mã lỗi chuẩn bên dưới.
        }

        throw new BusinessException(
                ErrorCode.INVALID_ORDER_ID
        );
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        return false;
    }
}
