package com.revo.backend.media.service;

import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.media.dto.MediaUploadResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;


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

    public record AiImage(
            String mimeType,
            byte[] content
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
        return upload(userId, file, "LISTING_IMAGE");
    }

    public MediaUploadResponse upload(
            Long userId,
            MultipartFile file,
            String purpose
    ) {

        if (!"LISTING_IMAGE".equals(purpose)
                && !"RETURN_EVIDENCE".equals(purpose)) {
            throw new BusinessException(ErrorCode.INVALID_FILTER);
        }

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

        // Dùng chung bộ kiểm tra chữ ký file và giải mã JPEG/PNG/WebP.
        ImageFormatInspector.Result inspected =
                ImageFormatInspector.inspect(content);

        String extension = "." + inspected.extension();
        String mimeType = inspected.mimeType();

        // 3. Sinh tên file ngẫu nhiên.

        String stagingPrefix =
                "RETURN_EVIDENCE".equals(purpose)
                        ? "staging/returns/"
                        : "staging/listings/";

        String objectKey = stagingPrefix
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
                expiresAt,
                purpose
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

    private String signUploadTicket(
            Long userId,
            String objectKey,
            Instant expiresAt,
            String purpose
    ) {

        String payload = String.join(
                "|",
                "v1",
                userId.toString(),
                purpose,
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
                    "staging/listings/[a-f0-9]{32}\\.(jpg|png|webp)"
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
                            || "image/webp".equals(head.contentType())
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


    public record ReturnEvidenceTicket(
            String sourceKey,
            String destinationKey
    ) {}


    public ReturnEvidenceTicket validateReturnEvidenceTicket(
            String token,
            Long userId,
            Long orderId,
            boolean checkStorage
    ) {
        try {
            if (token == null || token.isBlank()
                    || orderId == null || orderId <= 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            String[] segments = token.split("\\.", -1);

            if (segments.length != 2) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();

            byte[] payloadBytes = decoder.decode(segments[0]);
            byte[] signature = decoder.decode(segments[1]);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    ticketKey,
                    "HmacSHA256"
            ));

            if (!MessageDigest.isEqual(
                    mac.doFinal(payloadBytes),
                    signature
            )) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            String[] parts = new String(
                    payloadBytes,
                    StandardCharsets.UTF_8
            ).split("\\|", -1);

            if (parts.length != 5
                    || !"v1".equals(parts[0])
                    || !userId.toString().equals(parts[1])
                    || !"RETURN_EVIDENCE".equals(parts[2])) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            String sourceKey = parts[3];

            if (!sourceKey.matches(
                    "staging/returns/[a-f0-9]{32}\\.(jpg|png|webp)"
            )) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }

            long expiresAt = Long.parseLong(parts[4]);

            if (checkStorage) {
                if (Instant.now().getEpochSecond() >= expiresAt) {
                    throw new BusinessException(
                            ErrorCode.UPLOAD_TOKEN_EXPIRED
                    );
                }

                HeadObjectResponse head = s3Client.headObject(
                        HeadObjectRequest.builder()
                                .bucket(r2Bucket)
                                .key(sourceKey)
                                .build()
                );

                Long bytes = head.contentLength();
                String mime = head.contentType();

                if (bytes == null || bytes <= 0
                        || bytes > MAX_BYTES
                        || !(
                        "image/jpeg".equals(mime)
                                || "image/png".equals(mime)
                                || "image/webp".equals(mime)
                )) {
                    throw new BusinessException(
                            ErrorCode.INVALID_RETURN_EVIDENCE
                    );
                }
            }

            String filename = sourceKey.substring(
                    "staging/returns/".length()
            );

            return new ReturnEvidenceTicket(
                    sourceKey,
                    "returns/" + orderId + "/" + filename
            );

        } catch (BusinessException ex) {
            throw ex;

        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BusinessException(
                        ErrorCode.INVALID_RETURN_EVIDENCE
                );
            }
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );

        } catch (SdkException ex) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );

        } catch (GeneralSecurityException
                 | IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_EVIDENCE
            );
        }
    }


    public void promoteReturnEvidence(
            ReturnEvidenceTicket ticket
    ) {
        promoteListingImage(
                new ListingTicket(
                        ticket.sourceKey(),
                        ticket.destinationKey()
                )
        );
    }

    public String presignReturnEvidenceUrl(
            String storedKey
    ) {
        if (storedKey == null
                || !storedKey.matches(
                "returns/[0-9]+/[a-f0-9]{32}\\.(jpg|png|webp)"
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_RETURN_EVIDENCE
            );
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

    public AiImage readImageForAi(String objectKey) {

        boolean stagingKey = objectKey != null
                && objectKey.matches(
                "staging/listings/[a-f0-9]{32}\\.(jpg|png|webp)"
        );

        boolean listingKey = objectKey != null
                && objectKey.matches(
                "listings/[0-9]+/[a-f0-9]{32}\\.(jpg|png|webp)"
        );

        if (!stagingKey && !listingKey) {
            throw new BusinessException(
                    ErrorCode.INVALID_AI_INPUT
            );
        }

        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(r2Bucket)
                            .key(objectKey)
                            .build()
            );

            Long size = head.contentLength();
            String mimeType = head.contentType();

            if (size == null
                    || size <= 0
                    || size > 3L * 1024 * 1024
                    || !(
                    "image/jpeg".equals(mimeType)
                            || "image/png".equals(mimeType)
                            || "image/webp".equals(mimeType)
            )) {

                throw new BusinessException(
                        ErrorCode.INVALID_AI_INPUT
                );
            }

            byte[] content = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(r2Bucket)
                            .key(objectKey)
                            .build()
            ).asByteArray();

            if (content.length == 0
                    || content.length > 3L * 1024 * 1024) {

                throw new BusinessException(
                        ErrorCode.INVALID_AI_INPUT
                );
            }

            return new AiImage(mimeType, content);

        } catch (S3Exception ex) {

            if (ex.statusCode() == 404) {
                throw new BusinessException(
                        ErrorCode.INVALID_AI_INPUT
                );
            }

            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );

        } catch (SdkException ex) {
            throw new BusinessException(
                    ErrorCode.MEDIA_STORAGE_UNAVAILABLE
            );
        }
    }

    private static final class ImageFormatInspector {

        private ImageFormatInspector() {
        }

        public record Result(
                String mimeType,
                String extension
        ) {}

        public static Result inspect(byte[] content) {

            if (content == null || content.length < 12) {
                throw invalidImage();
            }

            Result detected = detectFormat(content);

            try (ImageInputStream input =
                         new MemoryCacheImageInputStream(
                                 new ByteArrayInputStream(content)
                         )) {

                Iterator<ImageReader> readers =
                        ImageIO.getImageReaders(input);

                if (!readers.hasNext()) {
                    throw invalidImage();
                }

                ImageReader reader = readers.next();

                try {
                    reader.setInput(input, true, true);

                    String readerFormat = reader.getFormatName();

                    boolean matches = switch (detected.extension()) {
                        case "jpg" ->
                                "JPEG".equalsIgnoreCase(readerFormat);

                        case "png" ->
                                "PNG".equalsIgnoreCase(readerFormat);

                        case "webp" ->
                                "WebP".equalsIgnoreCase(readerFormat);

                        default -> false;
                    };

                    if (!matches) {
                        throw invalidImage();
                    }

                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    if (width <= 0
                            || height <= 0
                            || (long) width * height > MAX_PIXELS) {
                        throw invalidImage();
                    }

                    BufferedImage image = reader.read(0);

                    if (image == null) {
                        throw invalidImage();
                    }

                    return detected;

                } finally {
                    reader.dispose();
                }

            } catch (IOException | IllegalArgumentException ex) {
                throw invalidImage();
            }
        }

        private static Result detectFormat(byte[] bytes) {

            // JPEG: FF D8 FF
            if ((bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {

                return new Result("image/jpeg", "jpg");
            }

            // PNG signature
            byte[] png = {
                    (byte) 0x89, 0x50, 0x4e, 0x47,
                    0x0d, 0x0a, 0x1a, 0x0a
            };

            boolean isPng = true;

            for (int i = 0; i < png.length; i++) {
                if (bytes[i] != png[i]) {
                    isPng = false;
                    break;
                }
            }

            if (isPng) {
                return new Result("image/png", "png");
            }

            // WebP: RIFF....WEBP
            boolean isWebp =
                    bytes[0] == 'R'
                            && bytes[1] == 'I'
                            && bytes[2] == 'F'
                            && bytes[3] == 'F'
                            && bytes[8] == 'W'
                            && bytes[9] == 'E'
                            && bytes[10] == 'B'
                            && bytes[11] == 'P';

            if (isWebp) {

                // Không nhận ảnh WebP động ở phiên bản này.
                // VP8X flags: bit 1 biểu thị animation.
                if (bytes.length > 20
                        && bytes[12] == 'V'
                        && bytes[13] == 'P'
                        && bytes[14] == '8'
                        && bytes[15] == 'X'
                        && (bytes[20] & 0x02) != 0) {
                    throw invalidImage();
                }

                return new Result("image/webp", "webp");
            }

            throw invalidImage();
        }

        private static BusinessException invalidImage() {
            return new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
