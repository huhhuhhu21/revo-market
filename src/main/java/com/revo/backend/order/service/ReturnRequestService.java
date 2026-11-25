
package com.revo.backend.order.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;

import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;

import com.revo.backend.listing.entity.ListingStatus;
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

import com.revo.backend.order.dto.AdminReturnHandoverRequest;

import java.util.Objects;

import com.revo.backend.listing.entity.ProductListing;
import com.revo.backend.listing.repository.ListingRepository;

import com.revo.backend.order.dto.AdminReturnReceiptRequest;
import com.revo.backend.order.dto.AdminReturnReceiptResponse;

import com.revo.backend.order.dto.RejectReturnRequest;


@Service
public class ReturnRequestService {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MediaService mediaService;
    private final ListingRepository listingRepository;

    public ReturnRequestService(
            OrderRepository orderRepository,
            ReturnRequestRepository returnRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            MediaService mediaService,
            ListingRepository listingRepository
    ) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.mediaService = mediaService;
        this.listingRepository = listingRepository;
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

    /** D10: Chặn trực tiếp khi không đủ điều kiện, không tạo order_exceptions. */
    @Transactional
    public ReturnRequestResponse recordReturnHandover(
            Long adminId,
            String rawReturnId,
            AdminReturnHandoverRequest request
    ) {
        Long returnId = parseReturnId(rawReturnId);
        if (request == null || request.actualAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (admin.getRole() != User.Role.ADMIN
                || admin.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime recordedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime actualAt = request.actualAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MILLIS);
        if (actualAt.isAfter(recordedAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Long orderId = returnRepository.findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returnRequest = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND));

        ReturnStatus status = returnRequest.getStatus();
        // Idempotency: không tạo thêm event hoặc thông báo.
        if (status == ReturnStatus.RETURNING || status == ReturnStatus.RETURNED) {
            if (Objects.equals(returnRequest.getHandedOverAt(), actualAt)) {
                return toAdminResponse(returnRequest);
            }
            throw new BusinessException(ErrorCode.RETURN_NOT_AWAITING_HANDOVER);
        }
        if (status == ReturnStatus.HANDOVER_EXPIRED) {
            throw new BusinessException(ErrorCode.RETURN_HANDOVER_DEADLINE_EXPIRED);
        }
        if (status != ReturnStatus.AWAITING_HANDOVER) {
            throw new BusinessException(ErrorCode.RETURN_NOT_AWAITING_HANDOVER);
        }

        LocalDateTime approvedAt = returnRequest.getProcessedAt();
        LocalDateTime deadline = returnRequest.getReturnDeadline();
        if (approvedAt == null || deadline == null
                || !deadline.equals(approvedAt.plusHours(48))) {
            throw new BusinessException(ErrorCode.RETURN_NOT_AWAITING_HANDOVER);
        }
        if (actualAt.isBefore(approvedAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        // Với quy trình mô phỏng: ADMIN phải xác nhận trong hạn.
        // Không có nhánh xử lý ngoại lệ để mở lại yêu cầu đã quá hạn.
        if (actualAt.isAfter(deadline) || recordedAt.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.RETURN_HANDOVER_DEADLINE_EXPIRED);
        }

        if (order.getStatus() != OrderStatus.DELIVERED
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN);
        }
        if (order.getCodStatus() != CodStatus.HELD) {
            throw new BusinessException(ErrorCode.COD_NOT_HELD);
        }

        String note = request.note();
        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) note = null;
        }

        returnRequest.recordHandover(actualAt);
        returnRepository.saveAndFlush(returnRequest);
        int updated = orderRepository.appendReturnHandoverEvent(
                orderId, returnId, adminId,
                actualAt.atOffset(ZoneOffset.UTC).toString(),
                recordedAt.atOffset(ZoneOffset.UTC).toString(),
                note, recordedAt
        );
        if (updated != 1) {
            throw new IllegalStateException("Cannot append RETURN_HANDOVER event");
        }

        notificationRepository.saveAll(List.of(
                Notification.returnHandoverRecorded(order.getBuyer(), returnId, true),
                Notification.returnHandoverRecorded(order.getSeller(), returnId, false)
        ));
        return toAdminResponse(returnRequest);
    }

    /** D11: Hoàn COD mô phỏng đúng một lần; không tạo ngoại lệ COD_ORDER. */
    @Transactional
    public AdminReturnReceiptResponse recordReturnReceipt(
            Long adminId,
            String rawReturnId,
            AdminReturnReceiptRequest request
    ) {
        Long returnId = parseReturnId(rawReturnId);
        if (request == null || request.actualAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (admin.getRole() != User.Role.ADMIN
                || admin.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime recordedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime actualAt = request.actualAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MILLIS);
        if (actualAt.isAfter(recordedAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Long orderId = returnRepository.findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND));
        // Khóa Order trước (các job quyết toán phải dùng cùng khóa này).
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returnRequest = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (returnRequest.getStatus() == ReturnStatus.RETURNED) {
            if (Objects.equals(returnRequest.getReturnedAt(), actualAt)
                    && order.getStatus() == OrderStatus.RETURNED
                    && order.getCodStatus() == CodStatus.REFUNDED
                    && order.getCodRefundedAt() != null) {
                // Retry: không đụng COD, listing hay notification.
                return buildReceiptResponse(returnRequest, order, order.getListing());
            }
            throw new BusinessException(ErrorCode.RETURN_NOT_RETURNING);
        }
        if (returnRequest.getStatus() != ReturnStatus.RETURNING
                || returnRequest.getHandedOverAt() == null
                || returnRequest.getReturnedAt() != null) {
            throw new BusinessException(ErrorCode.RETURN_NOT_RETURNING);
        }
        if (actualAt.isBefore(returnRequest.getHandedOverAt())) {
            throw new BusinessException(ErrorCode.RETURN_RECEIPT_TIME_INVALID);
        }
        if ((order.getStatus() != OrderStatus.DELIVERED
                && order.getStatus() != OrderStatus.COMPLETED)
                || order.getDeliveredAt() == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN);
        }
        if (order.getCodStatus() == CodStatus.SETTLED
                || order.getCodSettledAt() != null) {
            // Trạng thái mâu thuẫn: chặn trực tiếp, tuyệt đối không hoàn lần hai.
            throw new BusinessException(ErrorCode.COD_ALREADY_SETTLED);
        }
        if (order.getCodStatus() != CodStatus.HELD
                || order.getCodCollectedAt() == null
                || order.getCodRefundedAt() != null) {
            throw new BusinessException(ErrorCode.COD_NOT_HELD);
        }

        ProductListing listing = listingRepository
                .findByIdForUpdate(order.getListing().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.isDeleted()
                || (listing.getStatus() != ListingStatus.IN_TRANSACTION
                && listing.getStatus() != ListingStatus.SOLD)) {
            throw new BusinessException(ErrorCode.RETURN_LISTING_STATE_INVALID);
        }

        String note = request.note();
        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) note = null;
        }

        returnRequest.completeReturn(actualAt);
        order.finishReturnAndRefund(recordedAt);
        listing.hideAfterSuccessfulReturn();
        returnRepository.saveAndFlush(returnRequest);
        orderRepository.saveAndFlush(order);
        listingRepository.saveAndFlush(listing);

        int updated = orderRepository.appendReturnReceiptEvents(
                orderId, returnId, adminId,
                actualAt.atOffset(ZoneOffset.UTC).toString(),
                recordedAt.atOffset(ZoneOffset.UTC).toString(),
                order.getCodAmount(), note, recordedAt
        );
        if (updated != 1) {
            throw new IllegalStateException("Cannot append return receipt events");
        }
        // Chỉ thông báo hoàn COD cho người mua.
        notificationRepository.save(Notification.codRefunded(order.getBuyer(), orderId));
        return buildReceiptResponse(returnRequest, order, listing);
    }

    private AdminReturnReceiptResponse buildReceiptResponse(
            ReturnRequest returnRequest,
            Order order,
            ProductListing listing
    ) {
        return new AdminReturnReceiptResponse(
                new AdminReturnReceiptResponse.ReturnInfo(
                        returnRequest.getId(),
                        order.getId(),
                        returnRequest.getStatus(),
                        utc(returnRequest.getReturnedAt())
                ),

                new AdminReturnReceiptResponse.OrderInfo(
                        order.getId(),
                        order.getStatus(),
                        order.getCodStatus(),
                        utc(order.getCodRefundedAt()),
                        order.getCodAmount()
                ),

                new AdminReturnReceiptResponse.ListingInfo(
                        listing.getId(),
                        listing.getStatus(),
                        listing.getHiddenReasonType()
                )
        );
    }

    @Transactional
    public ReturnRequestResponse rejectReturn(
            Long adminId,
            String rawReturnId,
            RejectReturnRequest request
    ) {
        // 1. Kiểm tra returnId và request.
        Long returnId = parseReturnId(rawReturnId);

        if (request == null || request.reason() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        String reason = request.reason().trim();

        if (reason.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Kiểm tra ADMIN đang ACTIVE.
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

        // 3. Lấy Order ID liên kết với ReturnRequest.
        Long orderId = returnRepository
                .findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 4. Khóa Order trước.
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 5. Khóa ReturnRequest sau Order.
        ReturnRequest returnRequest = returnRepository
                .findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 6. Retry: yêu cầu đã bị từ chối.
        if (returnRequest.getStatus()
                == ReturnStatus.REJECTED) {

            if (Objects.equals(
                    returnRequest.getAdminReason(),
                    reason
            )) {
                // Giữ nguyên kết quả đã lưu.
                // Không ghi thêm lịch sử hay thông báo.
                return toAdminResponse(returnRequest);
            }

            // Không cho ADMIN thay đổi lý do đã từ chối.
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_PENDING
            );
        }

        // 7. Chỉ từ chối yêu cầu PENDING.
        if (returnRequest.getStatus()
                != ReturnStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_PENDING
            );
        }

        // D09 không kiểm tra lại hạn tạo return.
        // Yêu cầu PENDING đã được tạo hợp lệ tại D01.

        // 8. Thời điểm từ chối do backend tạo.
        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS);

        // 9. Cập nhật ReturnRequest.
        returnRequest.reject(
                admin,
                reason,
                now
        );

        returnRepository.saveAndFlush(returnRequest);

        // 10. Ghi lịch sử từ chối vào Order.
        String isoAt =
                now.atOffset(ZoneOffset.UTC).toString();

        int updated = orderRepository.appendReturnRejectedEvent(
                orderId,
                returnId,
                adminId,
                isoAt,
                reason,
                now
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot append RETURN_REJECTED event"
            );
        }

        // 11. Thông báo cho người mua và người bán.
        notificationRepository.saveAll(
                List.of(
                        Notification.returnRejected(
                                order.getBuyer(),
                                returnId,
                                true
                        ),
                        Notification.returnRejected(
                                order.getSeller(),
                                returnId,
                                false
                        )
                )
        );

        // 12. Trả về DTO dành cho ADMIN.
        return toAdminResponse(returnRequest);
    }

}
