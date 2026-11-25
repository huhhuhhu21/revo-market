
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
import com.revo.backend.order.entity.OrderException;
import com.revo.backend.order.repository.OrderExceptionRepository;

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
    private final OrderExceptionRepository exceptionRepository;
    private final ListingRepository listingRepository;

    public ReturnRequestService(
            OrderRepository orderRepository,
            ReturnRequestRepository returnRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            MediaService mediaService,
            OrderExceptionRepository exceptionRepository,
            ListingRepository listingRepository
    ) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.mediaService = mediaService;
        this.exceptionRepository = exceptionRepository;
        this.listingRepository = listingRepository;
    }

    public record CreateResult(
            ReturnRequestResponse response,
            boolean created
    ) {}

    public record HandoverOutcome(
            ReturnRequestResponse response,
            Long exceptionId
    ) {}

    public record ReceiptOutcome(
            AdminReturnReceiptResponse response,
            Long exceptionId
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

    @Transactional
    public HandoverOutcome recordReturnHandover(
            Long adminId,
            String rawReturnId,
            AdminReturnHandoverRequest request
    ) {
        // 1. Kiểm tra request.
        Long returnId = parseReturnId(rawReturnId);

        if (request == null || request.actualAt() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Kiểm tra ADMIN hiện tại.
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

        // 3. Chuẩn hóa thời gian UTC đến mili giây.
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
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 4. Tìm Order ID trước khi khóa dữ liệu.
        Long orderId = returnRepository
                .findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 5. Khóa Order trước, ReturnRequest sau.
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        ReturnRequest returnRequest = returnRepository
                .findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        ReturnStatus status = returnRequest.getStatus();

        // 6. Retry khi đã ghi nhận bàn giao thành công.
        if (status == ReturnStatus.RETURNING
                || status == ReturnStatus.RETURNED) {

            if (Objects.equals(
                    returnRequest.getHandedOverAt(),
                    actualAt
            )) {
                return new HandoverOutcome(
                        toAdminResponse(returnRequest),
                        null
                );
            }

            throw new BusinessException(
                    ErrorCode.RETURN_NOT_AWAITING_HANDOVER
            );
        }

        // 7. Kiểm tra báo cáo bàn giao muộn trước đó.
        List<Object[]> previousReports =
                orderRepository.findLateReturnHandoverReports(
                        orderId,
                        returnId
                );

        if (!previousReports.isEmpty()) {
            Object[] previous = previousReports.get(0);

            LocalDateTime previousActualAt =
                    OffsetDateTime.parse(
                                    previous[0].toString()
                            ).withOffsetSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime()
                            .truncatedTo(ChronoUnit.MILLIS);

            if (Objects.equals(previousActualAt, actualAt)) {
                Long exceptionId =
                        ((Number) previous[1]).longValue();

                return new HandoverOutcome(
                        null,
                        exceptionId
                );
            }

            throw new BusinessException(
                    ErrorCode.RETURN_NOT_AWAITING_HANDOVER
            );
        }

        // 8. Chỉ hai trạng thái này được xét tiếp:
        // AWAITING_HANDOVER: luồng bình thường.
        // HANDOVER_EXPIRED: chỉ chuyển xử lý ngoại lệ.
        if (status != ReturnStatus.AWAITING_HANDOVER
                && status != ReturnStatus.HANDOVER_EXPIRED) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_AWAITING_HANDOVER
            );
        }

        LocalDateTime processedAt =
                returnRequest.getProcessedAt();

        LocalDateTime deadline =
                returnRequest.getReturnDeadline();

        if (processedAt == null || deadline == null
                || !deadline.equals(processedAt.plusHours(48))) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_AWAITING_HANDOVER
            );
        }

        // 9. Kiểm tra thời điểm bàn giao thực tế.
        if (actualAt.isBefore(processedAt)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        if (actualAt.isAfter(deadline)) {
            throw new BusinessException(
                    ErrorCode.RETURN_HANDOVER_DEADLINE_EXPIRED
            );
        }

        // 10. Kiểm tra trạng thái đơn và COD.
        boolean validOrderStatus =
                order.getStatus() == OrderStatus.DELIVERED
                        || order.getStatus() == OrderStatus.COMPLETED;

        if (!validOrderStatus) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN
            );
        }

        if (order.getCodStatus() != CodStatus.HELD) {
            throw new BusinessException(
                    ErrorCode.COD_NOT_HELD
            );
        }

        // 11. Chuẩn hóa ghi chú.
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

        // 12. Trường hợp phải xử lý ngoại lệ:
        // - ADMIN ghi nhận sau returnDeadline;
        // - hoặc job đã chuyển HANDOVER_EXPIRED.
        boolean requiresException =
                recordedAt.isAfter(deadline)
                        || status == ReturnStatus.HANDOVER_EXPIRED;

        if (requiresException) {

            OrderException exception =
                    exceptionRepository.saveAndFlush(
                            OrderException.lateReturnHandover(
                                    order,
                                    admin,
                                    returnId,
                                    actualAt,
                                    recordedAt
                            )
                    );

            int updated =
                    orderRepository.appendLateReturnHandoverEvent(
                            orderId,
                            returnId,
                            adminId,
                            exception.getId(),
                            actualIso,
                            recordedIso,
                            note,
                            recordedAt
                    );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Could not record late handover report"
                );
            }

            // Không cập nhật return_requests.status.
            // Không hoàn COD.
            // Controller sẽ trả HTTP 409 sau commit.
            return new HandoverOutcome(
                    null,
                    exception.getId()
            );
        }

        // 13. Kiểm tra ngoại lệ đang chặn.
        List<Object[]> conditionRows =
                orderRepository.findOrderUiConditions(
                        List.of(orderId)
                );

        if (conditionRows.size() != 1) {
            throw new IllegalStateException(
                    "Missing order conditions: " + orderId
            );
        }

        Object blockingException = conditionRows.get(0)[2];

        if (sqlTrueD10(blockingException)) {
            throw new BusinessException(
                    ErrorCode.ORDER_HAS_BLOCKING_EXCEPTION
            );
        }

        // 14. Bàn giao hợp lệ.
        returnRequest.recordHandover(actualAt);
        returnRepository.saveAndFlush(returnRequest);

        // 15. Ghi sự kiện RETURN_HANDOVER.
        int updated = orderRepository.appendReturnHandoverEvent(
                orderId,
                returnId,
                adminId,
                actualIso,
                recordedIso,
                note,
                recordedAt
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not append RETURN_HANDOVER event"
            );
        }

        // 16. Thông báo cho người mua và người bán.
        notificationRepository.saveAll(
                List.of(
                        Notification.returnHandoverRecorded(
                                order.getBuyer(),
                                returnId,
                                true
                        ),
                        Notification.returnHandoverRecorded(
                                order.getSeller(),
                                returnId,
                                false
                        )
                )
        );

        // Không thay đổi Order.status, COD hoặc Listing.
        return new HandoverOutcome(
                toAdminResponse(returnRequest),
                null
        );
    }

    private static boolean sqlTrueD10(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        return false;
    }

    @Transactional
    public ReceiptOutcome recordReturnReceipt(
            Long adminId,
            String rawReturnId,
            AdminReturnReceiptRequest request
    ) {
        // 1. Kiểm tra input.
        Long returnId = parseReturnId(rawReturnId);

        if (request == null || request.actualAt() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Kiểm tra ADMIN đang hoạt động.
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

        // 3. Thời gian ghi nhận do backend tạo.
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
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 4. Tra cứu Order ID từ ReturnRequest.
        Long orderId = returnRepository
                .findOrderIdByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 5. Khóa Order trước.
        // Job quyết toán COD cũng phải sử dụng khóa này.
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));

        // 6. Khóa ReturnRequest sau Order.
        ReturnRequest returnRequest = returnRepository
                .findByIdForUpdate(returnId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RETURN_REQUEST_NOT_FOUND
                ));

        // 7. Retry khi đã hoàn tất trả hàng.
        if (returnRequest.getStatus() == ReturnStatus.RETURNED) {

            boolean sameReceipt = Objects.equals(
                    returnRequest.getReturnedAt(),
                    actualAt
            );

            boolean alreadyRefunded =
                    order.getStatus() == OrderStatus.RETURNED
                            && order.getCodStatus() == CodStatus.REFUNDED
                            && order.getCodRefundedAt() != null;

            if (sameReceipt && alreadyRefunded) {
                // Không cập nhật returnedAt hay codRefundedAt.
                // Không ghi lịch sử hoặc gửi thông báo.
                return new ReceiptOutcome(
                        buildReceiptResponse(
                                returnRequest,
                                order,
                                order.getListing()
                        ),
                        null
                );
            }

            throw new BusinessException(
                    ErrorCode.RETURN_NOT_RETURNING
            );
        }

        // 8. Chỉ yêu cầu RETURNING được tiếp tục.
        if (returnRequest.getStatus() != ReturnStatus.RETURNING
                || returnRequest.getHandedOverAt() == null
                || returnRequest.getReturnedAt() != null) {
            throw new BusinessException(
                    ErrorCode.RETURN_NOT_RETURNING
            );
        }

        // 9. Kiểm tra thời gian người bán nhận hàng.
        if (actualAt.isBefore(
                returnRequest.getHandedOverAt()
        )) {
            throw new BusinessException(
                    ErrorCode.RETURN_RECEIPT_TIME_INVALID
            );
        }

        // 10. Kiểm tra trạng thái đơn.
        boolean validOrderStatus =
                order.getStatus() == OrderStatus.DELIVERED
                        || order.getStatus() == OrderStatus.COMPLETED;

        if (!validOrderStatus || order.getDeliveredAt() == null) {
            throw new BusinessException(
                    ErrorCode.ORDER_NOT_ELIGIBLE_FOR_RETURN
            );
        }

        // 11. COD đã SETTLED: phải tạo ngoại lệ,
        // tuyệt đối không tự hoàn lần thứ hai.
        if (order.getCodStatus() == CodStatus.SETTLED
                || order.getCodSettledAt() != null) {

            Long existingExceptionId = exceptionRepository
                    .findExistingCodSettledException(orderId)
                    .orElse(null);

            if (existingExceptionId != null) {
                return new ReceiptOutcome(
                        null,
                        existingExceptionId
                );
            }

            OrderException exception =
                    exceptionRepository.saveAndFlush(
                            OrderException.codAlreadySettled(
                                    order,
                                    admin,
                                    returnId,
                                    recordedAt
                            )
                    );

            String recordedIso =
                    recordedAt.atOffset(ZoneOffset.UTC).toString();

            int recorded =
                    orderRepository.appendCodSettlementExceptionEvent(
                            orderId,
                            exception.getId(),
                            adminId,
                            recordedIso,
                            recordedAt
                    );

            if (recorded != 1) {
                throw new IllegalStateException(
                        "Cannot record COD exception"
                );
            }

            // Không đổi ReturnRequest / Order / Listing.
            // Controller trả 409 sau khi transaction commit.
            return new ReceiptOutcome(
                    null,
                    exception.getId()
            );
        }

        // 12. COD phải đang giữ và đã được thu hợp lệ.
        if (order.getCodStatus() != CodStatus.HELD
                || order.getCodCollectedAt() == null
                || order.getCodRefundedAt() != null) {
            throw new BusinessException(
                    ErrorCode.COD_NOT_HELD
            );
        }

        // 13. Không hoàn COD khi có ngoại lệ PENDING.
        if (orderRepository.countPendingOrderExceptions(
                orderId
        ) > 0) {
            throw new BusinessException(
                    ErrorCode.ORDER_HAS_BLOCKING_EXCEPTION
            );
        }

        // 14. Khóa bài đăng trước khi thay đổi trạng thái.
        ProductListing listing = listingRepository
                .findByIdForUpdate(order.getListing().getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.LISTING_NOT_FOUND
                ));

        boolean validListing =
                !listing.isDeleted()
                        && (
                        listing.getStatus()
                                == ListingStatus.IN_TRANSACTION
                                || listing.getStatus()
                                == ListingStatus.SOLD
                );

        if (!validListing) {
            throw new BusinessException(
                    ErrorCode.RETURN_LISTING_STATE_INVALID
            );
        }

        // 15. Chuẩn hóa ghi chú.
        String note = request.note();

        if (note != null) {
            note = note.trim();

            if (note.isEmpty()) {
                note = null;
            }
        }

        // 16. Cập nhật ba đối tượng trong cùng transaction.
        returnRequest.completeReturn(actualAt);
        order.finishReturnAndRefund(recordedAt);
        listing.hideAfterSuccessfulReturn();

        returnRepository.saveAndFlush(returnRequest);
        orderRepository.saveAndFlush(order);
        listingRepository.saveAndFlush(listing);

        // 17. Ghi lịch sử.
        String actualIso =
                actualAt.atOffset(ZoneOffset.UTC).toString();

        String recordedIso =
                recordedAt.atOffset(ZoneOffset.UTC).toString();

        int updated = orderRepository.appendReturnReceiptEvents(
                orderId,
                returnId,
                adminId,
                actualIso,
                recordedIso,
                order.getCodAmount(),
                note,
                recordedAt
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Cannot append return receipt events"
            );
        }

        // 18. Chỉ thông báo hoàn COD cho người mua.
        notificationRepository.save(
                Notification.codRefunded(
                        order.getBuyer(),
                        orderId
                )
        );

        // 19. Trả dữ liệu đã cập nhật.
        return new ReceiptOutcome(
                buildReceiptResponse(
                        returnRequest,
                        order,
                        listing
                ),
                null
        );
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
