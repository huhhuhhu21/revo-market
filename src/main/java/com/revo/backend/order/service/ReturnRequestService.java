
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;

import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;

import com.revo.backend.media.service.MediaService;
import com.revo.backend.media.service.MediaService.ReturnEvidenceTicket;

import com.revo.backend.notification.entity.Notification;
import com.revo.backend.notification.repository.NotificationRepository;

import com.revo.backend.order.dto.CreateReturnRequest;
import com.revo.backend.order.dto.ReturnRequestResponse;

import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.Order;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.ReturnRequest;

import com.revo.backend.order.repository.OrderRepository;
import com.revo.backend.order.repository.ReturnRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.revo.backend.order.dto.ReturnRequestLookupResponse;
import com.revo.backend.order.entity.ReturnStatus;

import com.revo.backend.order.dto.ApproveReturnRequest;
import com.revo.backend.order.entity.ReturnStatus;

import java.util.Objects;

@Service
public class ReturnRequestService {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MediaService mediaService;

    public ReturnRequestService(
            OrderRepository orderRepository,
            ReturnRequestRepository returnRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            MediaService mediaService
    ) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.mediaService = mediaService;
    }

    public record CreateResult(
            ReturnRequestResponse response,
            boolean created
    ) {}

    @Transactional
    public CreateResult create(
            Long currentUserId,
            String rawOrderId,
            CreateReturnRequest request
    ) {
        // 1. Kiểm tra request và ID đơn.
        Long orderId = parseOrderId(rawOrderId);

        if (request == null
                || request.reason() == null
                || request.evidenceImages() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        String reason = request.reason().trim();

        if (reason.isEmpty() || reason.length() > 2000) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Khóa đơn để tránh tạo trùng hoặc tranh chấp
        // với các tác vụ thay đổi trạng thái/COD.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 3. Chỉ người mua thực sự của đơn mới được gửi.
        if (!order.getBuyer().getId().equals(currentUserId)) {
            throw new BusinessException(
                    ErrorCode.ONLY_BUYER_CAN_RETURN
            );
        }

        // 4. Kiểm tra tài khoản.
        User buyer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        if (buyer.getRole() != User.Role.USER) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        // 5. Xử lý yêu cầu đã tồn tại trước khi kiểm tra
        // thời hạn hoặc sự tồn tại của ảnh tạm trên R2.
        ReturnRequest existing =
                returnRepository.findByOrder_Id(orderId)
                        .orElse(null);

        if (existing != null) {
            List<String> submittedKeys = resolveEvidence(
                    request,
                    currentUserId,
                    orderId,
                    false
            );

            if (existing.getReason().equals(reason)
                    && existing.getEvidenceImages()
                    .equals(submittedKeys)) {

                // Retry cùng payload: không ghi dữ liệu mới.
                return new CreateResult(
                        buildResponse(existing),
                        false
                );
            }

            throw new BusinessException(
                    ErrorCode.RETURN_REQUEST_ALREADY_EXISTS
            );
        }

        // 6. Chỉ tài khoản ACTIVE được tạo yêu cầu mới.
        if (buyer.getStatus() == User.Status.RESTRICTED) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }

        if (buyer.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        // 7. Kiểm tra trạng thái đơn và COD.
        if ((order.getStatus() != OrderStatus.DELIVERED
                && order.getStatus() != OrderStatus.COMPLETED)
                || order.getDeliveredAt() == null
                || order.getCodStatus() != CodStatus.HELD) {

            throw new BusinessException(
                    ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN
            );
        }

        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

        // 8. Kiểm tra hạn 48 giờ.
        // Đúng mốc deadline vẫn hợp lệ.
        LocalDateTime deadline =
                order.getDeliveredAt().plusHours(48);

        if (now.isAfter(deadline)) {
            throw new BusinessException(
                    ErrorCode.RETURN_REQUEST_WINDOW_EXPIRED
            );
        }

        if (now.isBefore(order.getDeliveredAt())) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN
            );
        }

        // 9. Không cho tạo khi có ngoại lệ giao/COD
        // chưa được giải quyết.
        if (orderRepository.countBlockingReturnExceptions(
                orderId
        ) > 0) {
            throw new BusinessException(
                    ErrorCode.ORDER_HAS_BLOCKING_EXCEPTION
            );
        }

        // 10. Xác thực tất cả ảnh trước khi sử dụng.
        List<ReturnEvidenceTicket> tickets =
                validateTickets(
                        request,
                        currentUserId,
                        orderId,
                        true
                );

        List<String> evidenceKeys = tickets.stream()
                .map(ReturnEvidenceTicket::destinationKey)
                .toList();

        // 11. Tạo return_requests PENDING.
        ReturnRequest created = ReturnRequest.create(
                order,
                buyer,
                reason,
                evidenceKeys,
                now
        );

        created = returnRepository.saveAndFlush(created);

        // 12. Chuyển ảnh từ staging sang khu vực
        // lưu bằng chứng riêng tư trên Cloudflare R2.
        for (ReturnEvidenceTicket ticket : tickets) {
            mediaService.promoteReturnEvidence(ticket);
        }

        // 13. Ghi sự kiện lịch sử trong cùng transaction.
        String isoAt = now.atOffset(ZoneOffset.UTC).toString();

        int updated = orderRepository.appendReturnRequestedEvent(
                orderId,
                created.getId(),
                currentUserId,
                isoAt,
                now
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot record return event for order "
                            + orderId
            );
        }

        // 14. Tạo thông báo cho người bán và ADMIN.
        List<Notification> notifications = new ArrayList<>();

        notifications.add(
                Notification.returnRequestReceived(
                        order.getSeller(),
                        created.getId(),
                        false
                )
        );

        List<User> admins = userRepository.findAllByRoleAndStatus(
                User.Role.ADMIN,
                User.Status.ACTIVE
        );

        for (User admin : admins) {
            notifications.add(
                    Notification.returnRequestReceived(
                            admin,
                            created.getId(),
                            true
                    )
            );
        }

        notificationRepository.saveAll(notifications);

        // Không thay đổi orders.status hoặc cod_status.
        return new CreateResult(
                buildResponse(created),
                true
        );
    }

    private List<String> resolveEvidence(
            CreateReturnRequest request,
            Long userId,
            Long orderId,
            boolean checkStorage
    ) {
        return validateTickets(
                request,
                userId,
                orderId,
                checkStorage
        ).stream()
                .map(ReturnEvidenceTicket::destinationKey)
                .toList();
    }

    private List<ReturnEvidenceTicket> validateTickets(
            CreateReturnRequest request,
            Long userId,
            Long orderId,
            boolean checkStorage
    ) {
        if (request.evidenceImages() == null
                || request.evidenceImages().isEmpty()
                || request.evidenceImages().size() > 5) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_EVIDENCE
            );
        }

        List<ReturnEvidenceTicket> tickets =
                new ArrayList<>();

        for (CreateReturnRequest.EvidenceImage image
                : request.evidenceImages()) {

            if (image == null
                    || image.uploadToken() == null
                    || image.uploadToken().isBlank()) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            tickets.add(
                    mediaService.validateReturnEvidenceTicket(
                            image.uploadToken(),
                            userId,
                            orderId,
                            checkStorage
                    )
            );
        }

        // Không cho phép lặp cùng một ảnh trong request.
        List<String> keys = tickets.stream()
                .map(ReturnEvidenceTicket::destinationKey)
                .toList();

        if (new HashSet<>(keys).size() != keys.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_EVIDENCE
            );
        }

        return tickets;
    }


    private ReturnRequestResponse buildResponse(
            ReturnRequest returnRequest
    ) {
        // D01 luôn trả response cho USER.
        return buildResponse(returnRequest, false);
    }

    private ReturnRequestResponse buildResponse(
            ReturnRequest returnRequest,
            boolean isAdmin
    ) {
        // Chỉ tạo URL ảnh sau khi đã kiểm tra quyền.
        List<String> imageUrls =
                returnRequest.getEvidenceImages()
                        .stream()
                        .map(mediaService::presignReturnEvidenceUrl)
                        .toList();

        ReturnStatus status = returnRequest.getStatus();

        // Các cờ này là gợi ý cho giao diện ADMIN.
        // API D08-D11 vẫn phải tự kiểm tra điều kiện.
        boolean canApprove =
                isAdmin && status == ReturnStatus.PENDING;

        boolean canReject =
                isAdmin && status == ReturnStatus.PENDING;

        boolean canRecordHandover =
                isAdmin
                        && status == ReturnStatus.AWAITING_HANDOVER
                        && returnRequest.getReturnDeadline() != null
                        && !LocalDateTime.now(ZoneOffset.UTC).isAfter(
                        returnRequest.getReturnDeadline()
                );

        boolean canRecordReceipt =
                isAdmin && status == ReturnStatus.RETURNING;

        // ID ADMIN xử lý chỉ trả cho ADMIN.
        Long processedBy =
                isAdmin && returnRequest.getProcessedBy() != null
                        ? returnRequest.getProcessedBy().getId()
                        : null;

        return new ReturnRequestResponse(
                returnRequest.getId(),
                returnRequest.getOrder().getId(),
                returnRequest.getBuyer().getId(),
                returnRequest.getReason(),
                imageUrls,
                status,
                utc(returnRequest.getCreatedAt()),
                processedBy,
                utc(returnRequest.getProcessedAt()),
                returnRequest.getAdminReason(),
                utc(returnRequest.getReturnDeadline()),
                utc(returnRequest.getHandedOverAt()),
                utc(returnRequest.getReturnedAt()),
                canApprove,
                canReject,
                canRecordHandover,
                canRecordReceipt
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
            // Trả mã lỗi chuẩn bên dưới.
        }

        throw new BusinessException(
                ErrorCode.INVALID_ORDER_ID
        );
    }

    private static OffsetDateTime utc(
            LocalDateTime value
    ) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }


    @Transactional(readOnly = true)
    public ReturnRequestLookupResponse getForOrder(
            Long currentUserId,
            String rawOrderId
    ) {
        // 1. Kiểm tra mã đơn.
        Long orderId = parseOrderId(rawOrderId);

        // 2. Tìm đơn hàng.
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 3. Kiểm tra tài khoản đang đăng nhập.
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 4. ADMIN phải đang ACTIVE.
        boolean isAdmin =
                currentUser.getRole() == User.Role.ADMIN
                        && currentUser.getStatus() == User.Status.ACTIVE;

        // 5. Kiểm tra người mua hoặc người bán.
        // USER bị RESTRICTED vẫn được xem đơn cũ.
        boolean isParticipant =
                currentUser.getRole() == User.Role.USER
                        && (
                        order.getBuyer().getId()
                                .equals(currentUserId)
                                || order.getSeller().getId()
                                .equals(currentUserId)
                );

        // 6. Người không có quyền nhận 404.
        if (!isAdmin && !isParticipant) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_FOUND
            );
        }

        // 7. Tìm yêu cầu trả hàng.
        ReturnRequest returnRequest =
                returnRepository.findByOrder_Id(orderId)
                        .orElse(null);

        // 8. Đơn chưa có yêu cầu trả hàng.
        if (returnRequest == null) {
            return new ReturnRequestLookupResponse(null);
        }

        // 9. Có yêu cầu: trả đầy đủ dữ liệu theo quyền.
        return new ReturnRequestLookupResponse(
                buildResponse(returnRequest, isAdmin)
        );
    }

    ReturnRequestResponse toAdminResponse(
            ReturnRequest returnRequest
    ) {
        return buildResponse(returnRequest, true);
    }

    @Transactional
    public ReturnRequestResponse approveReturn(
            Long adminId,
            String rawReturnId,
            ApproveReturnRequest request
    ) {
        // 1. Kiểm tra returnId.
        Long returnId = parseReturnId(rawReturnId);

        // 2. Kiểm tra ADMIN theo dữ liệu mới nhất.
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

        // 3. Chuẩn hóa ghi chú.
        String note = request == null ? null : request.note();

        if (note != null) {
            note = note.trim();

            if (note.isEmpty()) {
                note = null;
            }
        }

        // 4. Tìm orderId của yêu cầu.
        Long orderId = returnRepository
                .findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 5. Khóa Order trước để đồng bộ với D01
        // và các thao tác thay đổi đơn hàng/COD.
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 6. Khóa yêu cầu trả hàng sau Order.
        ReturnRequest returnRequest = returnRepository
                .findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 7. Kiểm tra gửi lại cùng yêu cầu phê duyệt.
        if (returnRequest.getStatus()
                == ReturnStatus.AWAITING_HANDOVER) {

            if (Objects.equals(
                    returnRequest.getAdminReason(),
                    note
            )) {
                // Retry: giữ nguyên thời điểm và deadline.
                // Không ghi lịch sử hoặc thông báo mới.
                return toAdminResponse(returnRequest);
            }

            throw new BusinessException(
                    ErrorCode.RETURN_NOT_PENDING
            );
        }

        // 8. Chỉ được duyệt yêu cầu PENDING.
        if (returnRequest.getStatus()
                != ReturnStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_PENDING
            );
        }

        // 9. Kiểm tra đơn hàng và COD.
        boolean validOrderStatus =
                order.getStatus() == OrderStatus.DELIVERED
                        || order.getStatus() == OrderStatus.COMPLETED;

        if (!validOrderStatus
                || order.getCodStatus() != CodStatus.HELD
                || order.getDeliveredAt() == null) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN
            );
        }

        // Không kiểm tra now <= deliveredAt + 48h.
        // Thời hạn đó đã được kiểm tra khi tạo D01.

        // 10. Tạo thời điểm phê duyệt tại backend.
        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

        // 11. Cập nhật ReturnRequest.
        returnRequest.approve(admin, note, now);

        returnRepository.saveAndFlush(returnRequest);

        // 12. Ghi lịch sử phê duyệt.
        String isoAt =
                now.atOffset(ZoneOffset.UTC).toString();

        int updated = orderRepository.appendReturnApprovedEvent(
                orderId,
                returnId,
                adminId,
                isoAt,
                note,
                now
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot append RETURN_APPROVED event"
            );
        }

        // 13. Thông báo cho người mua và người bán.
        notificationRepository.saveAll(
                List.of(
                        Notification.returnApproved(
                                order.getBuyer(),
                                returnId,
                                true
                        ),
                        Notification.returnApproved(
                                order.getSeller(),
                                returnId,
                                false
                        )
                )
        );

        // 14. Trả về DTO dành cho ADMIN.
        return toAdminResponse(returnRequest);
    }

    private static Long parseReturnId(String raw) {
        if (raw == null || !raw.matches("[0-9]+")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        try {
            long id = Long.parseLong(raw);

            if (id > 0) {
                return id;
            }

        } catch (NumberFormatException ignored) {
            // Xử lý theo mã lỗi chung.
        }

        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR
        );
    }

}
