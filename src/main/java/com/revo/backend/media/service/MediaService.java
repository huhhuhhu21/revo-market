package com.revo.backend.media.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.media.dto.MediaUploadResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import software.amazon.awssdk.services.s3.S3Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.*;

import java.security.MessageDigest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;


@Service
public class MediaService {

    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final long TOKEN_TTL_SECONDS = 7200;

    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final String r2Bucket;
    private final byte[] ticketKey;
    private final S3Presigner s3Presigner;

    private static final Logger log =
            LoggerFactory.getLogger(MediaService.class);

    public record ListingTicket(
            String sourceKey,
            String destinationKey
    ) {}

    public MediaService(
            UserRepository userRepository,
            S3Client s3Client,
            @Value("${app.r2.bucket}") String r2Bucket,
            @Value("${app.media.ticket-secret-base64}") String secretBase64,
            S3Presigner s3Presigner
    ) {
        this.userRepository = userRepository;
        this.s3Client = s3Client;
        this.r2Bucket = r2Bucket;
        this.s3Presigner = s3Presigner;

        try {
            this.ticketKey = Base64.getDecoder()
                    .decode(secretBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "MEDIA_TICKET_SECRET_BASE64 không hợp lệ."
            );
        }

        if (ticketKey.length < 32) {
            throw new IllegalStateException(
                    "Media ticket secret phải có ít nhất 32 bytes."
            );
        }
    }

    public MediaUploadResponse upload(
            Long userId,
            MultipartFile file
    ) {
        // 1. Kiểm tra tài khoản.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        if (user.getRole() != User.Role.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_RESTRICTED
            );
        }

        if (!user.isEmailVerified()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        // 2. Kiểm tra file.
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }

        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        if (content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        if (content.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }

        String format = inspectImage(content);

        String extension = format.equals("JPEG")
                ? ".jpg"
                : ".png";

        String mimeType = format.equals("JPEG")
                ? "image/jpeg"
                : "image/png";

        // 3. Sinh tên file ngẫu nhiên.
        String objectKey = "staging/listings/"
                + UUID.randomUUID().toString().replace("-", "")
                + extension;


        // 4. Ghi file vào storage tạm.
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(r2Bucket)
                    .key(objectKey)
                    .contentType(mimeType)
                    .cacheControl("private, no-store")
                    .build();

            s3Client.putObject(
                    putRequest,
                    RequestBody.fromBytes(content)
            );

        } catch (SdkException ex) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );
        }

        // 5. Tạo uploadToken có chữ ký.
        Instant expiresAt = Instant.now()
                .plusSeconds(TOKEN_TTL_SECONDS);

        String uploadToken = signUploadTicket(
                userId,
                objectKey,
                expiresAt
        );

        // 6. Trả DTO.
        return new MediaUploadResponse(
                uploadToken,
                null,
                mimeType,
                content.length,
                OffsetDateTime.ofInstant(
                        expiresAt,
                        ZoneOffset.UTC
                )
        );
    }

    private String inspectImage(byte[] content) {
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(content)
                     )) {

            if (input == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new BusinessException(
                        ErrorCode.UNSUPPORTED_IMAGE_TYPE
                );
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input);

                String format = reader.getFormatName()
                        .toUpperCase(Locale.ROOT);

                if (!format.equals("JPEG")
                        && !format.equals("PNG")) {
                    throw new BusinessException(
                            ErrorCode.UNSUPPORTED_IMAGE_TYPE
                    );
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new BusinessException(
                            ErrorCode.INVALID_IMAGE
                    );
                }

                // Thử giải mã để phát hiện dữ liệu ảnh bị hỏng.
                reader.read(0);

                return format;

            } finally {
                reader.dispose();
            }

        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }

    private String signUploadTicket(
            Long userId,
            String objectKey,
            Instant expiresAt
    ) {
        String payload = String.join(
                "|",
                "v1",
                userId.toString(),
                "LISTING_IMAGE",
                objectKey,
                Long.toString(expiresAt.getEpochSecond())
        );

        byte[] payloadBytes =
                payload.getBytes(StandardCharsets.UTF_8);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    ticketKey,
                    "HmacSHA256"
            ));

            byte[] signature = mac.doFinal(payloadBytes);

            Base64.Encoder encoder =
                    Base64.getUrlEncoder().withoutPadding();

            return encoder.encodeToString(payloadBytes)
                    + "."
                    + encoder.encodeToString(signature);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Không thể ký upload ticket.",
                    ex
            );
        }
    }
    public ListingTicket validateListingTicket(
            String token,
            Long userId
    ) {
        try {
            if (token == null) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            String[] segments = token.split("\\.", -1);

            if (segments.length != 2) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();

            byte[] payloadBytes = decoder.decode(segments[0]);
            byte[] providedSignature = decoder.decode(segments[1]);

            // Tự tính lại chữ ký của payload.
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    ticketKey,
                    "HmacSHA256"
            ));

            byte[] expectedSignature = mac.doFinal(payloadBytes);

            if (!MessageDigest.isEqual(
                    expectedSignature,
                    providedSignature
            )) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            String payload = new String(
                    payloadBytes,
                    StandardCharsets.UTF_8
            );

            String[] parts = payload.split("\\|", -1);

            if (parts.length != 5
                    || !"v1".equals(parts[0])
                    || !userId.toString().equals(parts[1])
                    || !"LISTING_IMAGE".equals(parts[2])) {

                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            long expiresAt = Long.parseLong(parts[4]);

            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_EXPIRED
                );
            }

            String sourceKey = parts[3];

            // Không cho token tham chiếu tùy ý đến object R2.
            if (!sourceKey.matches(
                    "staging/listings/[a-f0-9]{32}\\.(jpg|png)"
            )) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            // Kiểm tra file tạm thực sự tồn tại trên R2.
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(r2Bucket)
                            .key(sourceKey)
                            .build()
            );

            if (head.contentLength() == null
                    || head.contentLength() <= 0
                    || head.contentLength() > 3L * 1024 * 1024
                    || !(
                    "image/jpeg".equals(head.contentType())
                            || "image/png".equals(head.contentType())
            )) {

                throw new BusinessException(
                        ErrorCode.INVALID_IMAGE_SET
                );
            }

            String filename = sourceKey.substring(
                    "staging/listings/".length()
            );

            String destinationKey =
                    "listings/" + userId + "/" + filename;

            return new ListingTicket(
                    sourceKey,
                    destinationKey
            );

        } catch (BusinessException ex) {
            throw ex;

        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BusinessException(
                        ErrorCode.UPLOAD_TOKEN_INVALID
                );
            }

            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );

        } catch (SdkException ex) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );

        } catch (IllegalArgumentException
                 | GeneralSecurityException ex) {

            throw new BusinessException(
                    ErrorCode.UPLOAD_TOKEN_INVALID
            );
        }
    }

    public void promoteListingImage(ListingTicket ticket) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            throw new IllegalStateException(
                    "Thao tác lưu ảnh cần transaction MySQL."
            );
        }

        // Đăng ký hành động dọn dẹp theo kết quả transaction.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        String keyToDelete =
                                status == STATUS_COMMITTED
                                        ? ticket.sourceKey()
                                        : ticket.destinationKey();

                        try {
                            s3Client.deleteObject(
                                    DeleteObjectRequest.builder()
                                            .bucket(r2Bucket)
                                            .key(keyToDelete)
                                            .build()
                            );

                        } catch (SdkException ex) {
                            log.warn(
                                    "R2 cleanup failed for object {}",
                                    keyToDelete,
                                    ex
                            );
                        }
                    }
                }
        );

        try {
            s3Client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(r2Bucket)
                            .sourceKey(ticket.sourceKey())
                            .destinationBucket(r2Bucket)
                            .destinationKey(ticket.destinationKey())
                            .build()
            );

        } catch (SdkException ex) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );
        }
    }

    public String presignImageUrl(String storedKey) {

        if (storedKey == null || storedKey.isBlank()) {
            return null;
        }

        // Giữ tương thích với dữ liệu URL ảnh cũ nếu có.
        if (storedKey.startsWith("https://")
                || storedKey.startsWith("http://")) {
            return storedKey;
        }

        // Chỉ phục vụ object ảnh sản phẩm chính thức.
        if (!storedKey.startsWith("listings/")) {
            return null;
        }

        GetObjectPresignRequest request =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(
                                GetObjectRequest.builder()
                                        .bucket(r2Bucket)
                                        .key(storedKey)
                                        .build()
                        )
                        .build();

        return s3Presigner.presignGetObject(request)
                .url()
                .toString();
    }
}