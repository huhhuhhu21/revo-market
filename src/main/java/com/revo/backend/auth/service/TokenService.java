
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

    public AuthSessionResponse createSession(User user) {
        Instant now = Instant.now();

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String refreshToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = sha256(refreshToken);

        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                now.plusSeconds(REFRESH_TTL_DAYS * 24 * 60 * 60),
                ZoneOffset.UTC
        );

        RefreshToken session = refreshTokenRepository.saveAndFlush(
                new RefreshToken(user, tokenHash, refreshExpiresAt)
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("revo")
                .subject(user.getId().toString())
                .claim("sid", session.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TTL_SECONDS))
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
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.", ex
            );
        }
    }
}
