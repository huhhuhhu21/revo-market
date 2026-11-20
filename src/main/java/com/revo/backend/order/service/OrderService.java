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

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
}