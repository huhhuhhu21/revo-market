package com.revo.backend.auth.service;

import com.revo.backend.auth.dto.RegisterRequest;
import com.revo.backend.auth.dto.RegisterResponse;
import com.revo.backend.auth.entity.OtpCode;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.OtpCodeRepository;
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

import com.revo.backend.auth.dto.VerifyRegistrationRequest;
import com.revo.backend.auth.dto.VerifyRegistrationResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.revo.backend.auth.dto.ResendRegistrationRequest;
import com.revo.backend.auth.dto.ResendRegistrationResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import com.revo.backend.auth.dto.LoginRequest;
import com.revo.backend.auth.dto.AuthSessionResponse;

import java.util.Locale;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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

    public AuthService(
            UserRepository userRepository,
            OtpCodeRepository otpCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailOtpSender emailOtpSender,
            TokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpSender = emailOtpSender;
        this.tokenService = tokenService;

        this.dummyPasswordHash = passwordEncoder.encode(
                "dummy-password-for-missing-user"
        );
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





}
