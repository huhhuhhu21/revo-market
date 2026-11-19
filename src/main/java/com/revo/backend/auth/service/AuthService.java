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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpSender emailOtpSender;

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            OtpCodeRepository otpCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailOtpSender emailOtpSender
    ) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpSender = emailOtpSender;
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
}
