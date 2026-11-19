
package com.revo.backend.auth.service;

import com.revo.backend.auth.dto.AuthSessionResponse;
import com.revo.backend.auth.entity.RefreshToken;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.RefreshTokenRepository;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {

    private static final int ACCESS_TTL_SECONDS = 900;
    private static final long REFRESH_TTL_DAYS = 14;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            JwtEncoder jwtEncoder,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // A04: Tạo phiên đăng nhập mới.
    public AuthSessionResponse createSession(User user) {

        Instant now = Instant.now();
        String refreshToken = generateRefreshToken();

        RefreshToken session = refreshTokenRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        hashRefreshToken(refreshToken),
                        toUtcDateTime(
                                now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)
                        )
                )
        );

        return buildAuthSession(
                user,
                session.getId(),
                refreshToken,
                now
        );
    }

    // A05: Xoay vòng Refresh Token trên phiên hiện tại.
    public AuthSessionResponse rotateSession(RefreshToken session) {

        Instant now = Instant.now();
        String refreshToken = generateRefreshToken();

        session.rotate(
                hashRefreshToken(refreshToken),
                toUtcDateTime(
                        now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)
                )
        );

        return buildAuthSession(
                session.getUser(),
                session.getId(),
                refreshToken,
                now
        );
    }

    public String hashRefreshToken(String token) {
        return sha256(token);
    }

    private String generateRefreshToken() {

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private LocalDateTime toUtcDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private AuthSessionResponse buildAuthSession(
            User user,
            Long sessionId,
            String refreshToken,
            Instant now
    ) {

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("revo")
                .subject(user.getId().toString())
                .claim("sid", sessionId.toString())
                .issuedAt(now)
                .expiresAt(
                        now.plusSeconds(ACCESS_TTL_SECONDS)
                )
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new AuthSessionResponse(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TTL_SECONDS,
                new AuthSessionResponse.UserInfo(
                        user.getId(),
                        user.getRole().name(),
                        user.getStatus().name(),
                        user.getFullName(),
                        user.isEmailVerified(),
                        user.isPhoneVerified()
                )
        );
    }

    private String sha256(String value) {

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.", ex
            );
        }
    }
}
