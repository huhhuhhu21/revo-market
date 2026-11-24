
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminReturnPageResponse;
import com.revo.backend.order.dto.ReturnRequestResponse;
import com.revo.backend.order.entity.ReturnRequest;
import com.revo.backend.order.entity.ReturnStatus;
import com.revo.backend.order.repository.ReturnRequestRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminReturnListService {

    private final ReturnRequestRepository returnRepository;
    private final ReturnRequestService returnRequestService;
    private final UserRepository userRepository;

    public AdminReturnListService(
            ReturnRequestRepository returnRepository,
            ReturnRequestService returnRequestService,
            UserRepository userRepository
    ) {
        this.returnRepository = returnRepository;
        this.returnRequestService = returnRequestService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminReturnPageResponse getReturns(
            Long adminId,
            int page,
            int size,
            String status,
            String orderId
    ) {
        // 1. Kiểm tra ADMIN hiện tại.
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

        // 2. Kiểm tra phân trang.
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAGINATION
            );
        }

        // 3. Kiểm tra bộ lọc.
        String statusFilter = parseStatus(status);
        Long orderIdFilter = parseOrderId(orderId);

        // Thứ tự sắp xếp đã được định nghĩa trong SQL.
        PageRequest pageable = PageRequest.of(
                page,
                size
        );

        // 4. Lấy dữ liệu có phân trang.
        Page<ReturnRequest> result =
                returnRepository.findAdminReturns(
                        statusFilter,
                        orderIdFilter,
                        pageable
                );

        // 5. Chuyển dữ liệu sang DTO dành cho ADMIN.
        List<ReturnRequestResponse> items =
                result.getContent()
                        .stream()
                        .map(returnRequestService::toAdminResponse)
                        .toList();

        return new AdminReturnPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    private static String parseStatus(String raw) {
        if (raw == null
                || raw.isBlank()
                || raw.trim().equalsIgnoreCase("ALL")) {
            return null;
        }

        try {
            return ReturnStatus.valueOf(
                    raw.trim().toUpperCase(Locale.ROOT)
            ).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_FILTER
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
            // Trả lỗi chuẩn bên dưới.
        }

        throw new BusinessException(
                ErrorCode.INVALID_ORDER_ID
        );
    }
}
