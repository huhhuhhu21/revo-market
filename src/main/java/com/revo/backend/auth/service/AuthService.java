package com.revo.backend.auth.service;

import com.revo.backend.auth.dto.*;
import com.revo.backend.auth.entity.OtpCode;
import com.revo.backend.auth.entity.RefreshToken;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.OtpCodeRepository;
import com.revo.backend.auth.repository.RefreshTokenRepository;
import com.revo.backend.auth.repository.UserRepository;
import com.revo.backend.common.exception.BusinessException;
import com.revo.backend.common.exception.ErrorCode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.Locale;
import java.util.regex.Pattern;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import java.util.Locale;

import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(?:0[0-9]{9}|\\+84[0-9]{9})$");

    private static final String FORGOT_PASSWORD_MESSAGE =
            "Hệ thống đã gửi mã xác minh tới %s. ";



    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpSender emailOtpSender;


    private final SecureRandom secureRandom = new SecureRandom();

    private final TokenService tokenService;

    private final String dummyPasswordHash;

    private static final class LoginFailedException
            extends BusinessException {

        private LoginFailedException(ErrorCode errorCode) {
            super(errorCode);
        }
    }

    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
            UserRepository userRepository,
            OtpCodeRepository otpCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailOtpSender emailOtpSender,
            TokenService tokenService,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpSender = emailOtpSender;
        this.tokenService = tokenService;

        this.dummyPasswordHash = passwordEncoder.encode(
                "dummy-password-for-missing-user"
        );
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // Chưa có nhà cung cấp SMS: không báo đăng ký thành công.
        if (!"EMAIL".equals(request.contactType())) {
            throw new BusinessException(
                    ErrorCode.OTP_DELIVERY_UNAVAILABLE
            );
        }

        String email = request.contact()
                .strip()
                .toLowerCase(Locale.ROOT);

        String fullName = request.fullName().strip();

        if (email.length() > 254
                || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // BCrypt chỉ xử lý tối đa 72 byte dữ liệu đầu vào.
        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    ErrorCode.CONTACT_ALREADY_USED
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        User user = new User(
                fullName,
                email,
                null,
                passwordHash
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {

            String cause = ex.getMostSpecificCause().getMessage();

            if (cause != null
                    && cause.contains("uq_users_email")) {
                throw new BusinessException(
                        ErrorCode.CONTACT_ALREADY_USED
                );
            }

            throw ex;
        }

        // Sinh OTP 6 chữ số bằng SecureRandom.
        String otp = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String otpHash = passwordEncoder.encode(otp);

        LocalDateTime expiresAt =
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        OtpCode otpCode = new OtpCode(
                user,
                email,
                otpHash,
                expiresAt
        );

        otpCodeRepository.saveAndFlush(otpCode);

        // Chỉ trả thành công nếu SMTP đã tiếp nhận email.
        emailOtpSender.send(email, otp);

        String maskedEmail = email.charAt(0)
                + "***"
                + email.substring(email.indexOf('@'));

        return new RegisterResponse(
                user.getId(),
                user.getStatus().name(),
                new RegisterResponse.Verification(
                        "EMAIL",
                        maskedEmail,
                        300,
                        60
                )
        );
    }


    @Transactional(noRollbackFor = OtpAttemptException.class)
    public VerifyRegistrationResponse verifyRegistration(
            VerifyRegistrationRequest request
    ) {
        User user = userRepository
                .findByIdForUpdate(request.userId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.OTP_INVALID)
                );

        if (user.getStatus() != User.Status.PENDING_VERIFICATION) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ALREADY_VERIFIED
            );
        }

        OtpCode otpCode = otpCodeRepository
                .findFirstByUser_IdAndPurposeOrderByIdDesc(
                        user.getId(),
                        "REGISTER"
                )
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.OTP_INVALID)
                );

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (otpCode.getConsumedAt() != null) {
            throw new BusinessException(
                    ErrorCode.OTP_ALREADY_USED
            );
        }

        if (!now.isBefore(otpCode.getExpiresAt())) {
            throw new BusinessException(
                    ErrorCode.OTP_EXPIRED
            );
        }

        if (otpCode.getFailedAttempts() >= 5) {
            throw new BusinessException(
                    ErrorCode.OTP_ATTEMPTS_EXCEEDED
            );
        }

        String target = otpCode.getTargetOtp();

        boolean isEmail = target.equals(user.getEmail());
        boolean isPhone = target.equals(user.getPhone());

        if (!isEmail && !isPhone) {
            throw new BusinessException(
                    ErrorCode.OTP_INVALID
            );
        }

        if (!passwordEncoder.matches(
                request.otp(),
                otpCode.getCodeHash()
        )) {
            otpCode.incrementFailedAttempts();

            ErrorCode errorCode =
                    otpCode.getFailedAttempts() >= 5
                            ? ErrorCode.OTP_ATTEMPTS_EXCEEDED
                            : ErrorCode.OTP_INVALID;

            throw new OtpAttemptException(errorCode);
        }

        otpCode.markConsumed(now);

        if (isEmail) {
            user.activateWithEmail();
        } else {
            user.activateWithPhone();
        }

        return new VerifyRegistrationResponse(
                true,
                user.getStatus().name()
        );
    }
    private static final class OtpAttemptException
            extends BusinessException {

        private OtpAttemptException(ErrorCode errorCode) {
            super(errorCode);
        }
    }


    @Transactional
    public ResendRegistrationResponse resendRegistration(
            ResendRegistrationRequest request
    ) {
        // 1. Khóa User để tránh gửi lại và xác minh đồng thời.
        User user = userRepository
                .findByIdForUpdate(request.userId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND
                ));

        // 2. Chỉ tài khoản chưa xác minh mới được gửi lại.
        if (user.getStatus() != User.Status.PENDING_VERIFICATION) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ALREADY_VERIFIED
            );
        }

        // 3. Lấy OTP REGISTER gần nhất.
        OtpCode previousOtp = otpCodeRepository
                .findFirstByUser_IdAndPurposeOrderByIdDesc(
                        user.getId(),
                        "REGISTER"
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.OTP_INVALID
                ));

        // 4. Kiểm tra thời gian chờ 60 giây.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        LocalDateTime nextAllowedAt =
                previousOtp.getLastSentAt().plusSeconds(60);

        if (now.isBefore(nextAllowedAt)) {
            throw new BusinessException(
                    ErrorCode.OTP_RESEND_TOO_SOON
            );
        }

        // 5. Chỉ gửi tới email đã đăng ký.
        String email = user.getEmail();

        if (email == null
                || !email.equals(previousOtp.getTargetOtp())) {
            throw new BusinessException(
                    ErrorCode.OTP_DELIVERY_UNAVAILABLE
            );
        }

        // 6. Sinh OTP mới.
        String otp = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String otpHash = passwordEncoder.encode(otp);

        OtpCode newOtp = new OtpCode(
                user,
                email,
                otpHash,
                now.plusMinutes(5)
        );

        // 7. Vô hiệu hóa OTP cũ.
        if (previousOtp.getConsumedAt() == null) {
            previousOtp.markConsumed(now);
        }

        // 8. Lưu OTP mới.
        otpCodeRepository.saveAndFlush(newOtp);

        // 9. Gửi email đến đúng liên hệ đã đăng ký.
        emailOtpSender.send(email, otp);

        // 10. Tạo response, không trả OTP thô.
        String maskedEmail = email.charAt(0)
                + "***"
                + email.substring(email.indexOf('@'));

        return new ResendRegistrationResponse(
                new RegisterResponse.Verification(
                        "EMAIL",
                        maskedEmail,
                        300,
                        60
                )
        );
    }


    @Transactional(noRollbackFor = LoginFailedException.class)
    public AuthSessionResponse login(LoginRequest request) {

        // 1. Chuẩn hóa thông tin đăng nhập.
        String login = request.login().strip();

        if (login.contains("@")) {
            login = login.toLowerCase(Locale.ROOT);
        }

        Instant now = Instant.now();

        // 2. Tìm và khóa bản ghi User.
        User user = (
                login.contains("@")
                        ? userRepository.findByEmailForUpdate(login)
                        : userRepository.findByPhoneForUpdate(login)
        ).orElse(null);

        // 3. Kiểm tra tài khoản có đang bị khóa không.
        if (user != null && user.isLoginLocked(now)) {
            throw new BusinessException(
                    ErrorCode.LOGIN_TEMPORARILY_LOCKED
            );
        }

        // 4. Kiểm tra mật khẩu bằng BCrypt.
        // Vẫn thực hiện phép kiểm tra với dummy hash
        // nếu không tồn tại tài khoản.
        String storedHash = user == null
                ? dummyPasswordHash
                : user.getPasswordHash();

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                storedHash
        );

        // 5. Đăng nhập thất bại.
        if (user == null || !passwordMatches) {

            if (user != null) {
                boolean lockedNow = user.recordFailedLogin(now);

                ErrorCode errorCode = lockedNow
                        ? ErrorCode.LOGIN_TEMPORARILY_LOCKED
                        : ErrorCode.INVALID_CREDENTIALS;

                // Exception này cho phép commit bộ đếm.
                throw new LoginFailedException(errorCode);
            }

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        // 6. Mật khẩu đúng nhưng chưa xác minh OTP.
        if (user.getStatus() == User.Status.PENDING_VERIFICATION) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_VERIFIED
            );
        }

        // 7. Tạo phiên đăng nhập.
        AuthSessionResponse response = tokenService.createSession(user);

        // 8. Xóa lịch sử nhập sai sau khi đăng nhập thành công.
        user.clearLoginFailures();

        return response;
    }


    @Transactional
    public AuthSessionResponse refresh(RefreshTokenRequest request) {

        // 1. Hash token nhận từ client.
        String tokenHash = tokenService.hashRefreshToken(
                request.refreshToken()
        );

        // 2. Tìm ID phiên dựa trên hash hiện tại.
        Long sessionId = refreshTokenRepository
                .findIdByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REFRESH_TOKEN_INVALID
                ));

        // 3. Khóa phiên và đọc lại trạng thái mới nhất.
        RefreshToken session = refreshTokenRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REFRESH_TOKEN_INVALID
                ));

        // 4. Kiểm tra phiên đã bị thu hồi.
        if (session.getRevokedAt() != null) {
            throw new BusinessException(
                    ErrorCode.SESSION_REVOKED
            );
        }

        // 5. Kiểm tra thời hạn refresh token.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (!session.getExpiresAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_EXPIRED
            );
        }

        // 6. Tài khoản chưa xác minh không được refresh.
        User user = session.getUser();

        if (user.getStatus() == User.Status.PENDING_VERIFICATION) {
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_INVALID
            );
        }

        // 7. Kiểm tra lại hash sau khi lấy khóa.
        if (!session.getTokenHash().equals(tokenHash)) {
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_ALREADY_ROTATED
            );
        }

        // 8. Xoay vòng token trên cùng phiên.
        return tokenService.rotateSession(session);
    }


    @Transactional
    public void logout(Long userId, Long sessionId) {

        // 1. Tìm và khóa phiên cần đăng xuất.
        RefreshToken session = refreshTokenRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED
                ));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 2. Kiểm tra phiên thuộc đúng người dùng.
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }

        // 3. Không cho đăng xuất phiên đã thu hồi.
        if (session.getRevokedAt() != null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }

        // 4. Không chấp nhận phiên hết hạn.
        if (!session.getExpiresAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }

        // 5. Đánh dấu thu hồi phiên.
        session.revoke(now);
    }



    @Transactional
    public ForgotPasswordResponse forgotPassword(
            ForgotPasswordRequest request
    ) {
        String login = request.login().strip();

        ForgotPasswordResponse response =
                new ForgotPasswordResponse(
                        FORGOT_PASSWORD_MESSAGE.formatted(request.login().strip())
                );

        // Hiện chỉ hỗ trợ gửi OTP qua email.
        // Số điện thoại hợp lệ vẫn nhận phản hồi trung tính.
        if (!login.contains("@")) {
            if (!PHONE_PATTERN.matcher(login).matches()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR
                );
            }

            return response;
        }

        String email = login.toLowerCase(Locale.ROOT);

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // Khóa User để tránh tạo nhiều OTP đồng thời.
        User user = userRepository
                .findByEmailForUpdate(email)
                .orElse(null);

        // Không tiết lộ tài khoản có tồn tại hay không.
        if (user == null || !user.isEmailVerified()) {
            return response;
        }

        LocalDateTime now =
                LocalDateTime.now(ZoneOffset.UTC);

        OtpCode previousOtp = otpCodeRepository
                .findFirstByUser_IdAndPurposeOrderByIdDesc(
                        user.getId(),
                        "PASSWORD_RESET"
                )
                .orElse(null);

        // Giới hạn gửi lại tối thiểu 60 giây.
        // Không trả 429 riêng cho tài khoản có thật,
        // vì có thể làm lộ sự tồn tại của tài khoản.
        if (previousOtp != null
                && previousOtp.getLastSentAt() != null
                && now.isBefore(
                previousOtp.getLastSentAt().plusSeconds(60)
        )) {
            return response;
        }

        String otp = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String otpHash = passwordEncoder.encode(otp);

        // OTP cũ không còn được sử dụng sau khi cấp mã mới.
        if (previousOtp != null
                && previousOtp.getConsumedAt() == null) {
            previousOtp.markConsumed(now);
        }

        OtpCode newOtp = new OtpCode(
                user,
                email,
                "PASSWORD_RESET",
                otpHash,
                now.plusMinutes(5)
        );

        otpCodeRepository.saveAndFlush(newOtp);

        try {
            emailOtpSender.sendPasswordReset(email, otp);
        } catch (BusinessException ex) {
            if (ex.getErrorCode()
                    != ErrorCode.OTP_DELIVERY_UNAVAILABLE) {
                throw ex;
            }

            // SMTP thất bại: không lưu OTP mới,
            // đồng thời giữ OTP cũ nếu còn hợp lệ.
            TransactionAspectSupport
                    .currentTransactionStatus()
                    .setRollbackOnly();

            log.warn(
                    "Password reset OTP email delivery unavailable."
            );
        }

        return response;
    }


    @Transactional(noRollbackFor = OtpAttemptException.class)
    public void resetPassword(ResetPasswordRequest request) {

        // 1. Chuẩn hóa thông tin đăng nhập.
        String login = request.login().strip();
        boolean emailLogin = login.contains("@");

        if (emailLogin) {
            login = login.toLowerCase(Locale.ROOT);
        }

        // BCrypt trong dự án giới hạn mật khẩu ở 72 byte UTF-8.
        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR
            );
        }

        // 2. Tìm và khóa User.
        User user = (
                emailLogin
                        ? userRepository.findByEmailForUpdate(login)
                        : userRepository.findByPhoneForUpdate(login)
        ).orElse(null);

        // Không tiết lộ tài khoản có tồn tại hay không.
        if (user == null) {
            passwordEncoder.matches(
                    request.otp(),
                    dummyPasswordHash
            );

            throw new BusinessException(
                    ErrorCode.OTP_INVALID_OR_EXPIRED
            );
        }

        // 3. Kiểm tra liên hệ đã được xác minh.
        String target = emailLogin
                ? user.getEmail()
                : user.getPhone();

        boolean contactVerified = emailLogin
                ? user.isEmailVerified()
                : user.isPhoneVerified();

        if (!contactVerified || target == null) {
            passwordEncoder.matches(
                    request.otp(),
                    dummyPasswordHash
            );

            throw new BusinessException(
                    ErrorCode.OTP_INVALID_OR_EXPIRED
            );
        }

        // 4. Lấy OTP PASSWORD_RESET mới nhất.
        OtpCode otpCode = otpCodeRepository
                .findFirstByUser_IdAndPurposeOrderByIdDesc(
                        user.getId(),
                        "PASSWORD_RESET"
                )
                .orElse(null);

        // 5. Kiểm tra OTP có đúng đích và chưa sử dụng.
        if (otpCode == null
                || !target.equals(otpCode.getTargetOtp())
                || otpCode.getConsumedAt() != null) {

            passwordEncoder.matches(
                    request.otp(),
                    dummyPasswordHash
            );

            throw new BusinessException(
                    ErrorCode.OTP_INVALID_OR_EXPIRED
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 6. Kiểm tra thời gian hết hạn.
        if (!now.isBefore(otpCode.getExpiresAt())) {
            throw new BusinessException(
                    ErrorCode.OTP_INVALID_OR_EXPIRED
            );
        }

        // 7. Kiểm tra giới hạn nhập sai.
        if (otpCode.getFailedAttempts() >= 5) {
            throw new BusinessException(
                    ErrorCode.OTP_ATTEMPTS_EXCEEDED
            );
        }

        // 8. Đối chiếu OTP với hash đã lưu.
        if (!passwordEncoder.matches(
                request.otp(),
                otpCode.getCodeHash()
        )) {
            otpCode.incrementFailedAttempts();

            ErrorCode errorCode =
                    otpCode.getFailedAttempts() >= 5
                            ? ErrorCode.OTP_ATTEMPTS_EXCEEDED
                            : ErrorCode.OTP_INVALID_OR_EXPIRED;

            throw new OtpAttemptException(errorCode);
        }

        // 9. Mã hóa và cập nhật mật khẩu mới.
        String newPasswordHash = passwordEncoder.encode(
                request.newPassword()
        );

        user.changePasswordHash(newPasswordHash);

        // 10. Đánh dấu OTP đã sử dụng.
        otpCode.markConsumed(now);

        // 11. Thu hồi tất cả phiên của tài khoản.
        refreshTokenRepository.revokeAllActiveByUserId(
                user.getId(),
                now
        );
    }



}
