
package com.revo.backend.auth.security;

import com.revo.backend.auth.entity.RefreshToken;
import com.revo.backend.auth.entity.User;
import com.revo.backend.auth.repository.RefreshTokenRepository;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtSessionAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RefreshTokenRepository refreshTokenRepository;

    public JwtSessionAuthenticationConverter(
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        Long userId;
        Long sessionId;

        try {
            userId = Long.parseLong(jwt.getSubject());
            sessionId = Long.parseLong(jwt.getClaimAsString("sid"));
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid JWT claims.");
        }

        RefreshToken session = refreshTokenRepository
                .findById(sessionId)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid session.")
                );

        User user = session.getUser();

        if (!user.getId().equals(userId)
                || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(
                LocalDateTime.now(ZoneOffset.UTC)
        )
                || user.getStatus() == User.Status.PENDING_VERIFICATION) {

            throw new BadCredentialsException("Invalid session.");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        if (user.getRole() == User.Role.ADMIN
                && user.getStatus() == User.Status.ACTIVE) {

            authorities.add(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );

        } else if (user.getRole() == User.Role.USER) {

            authorities.add(
                    new SimpleGrantedAuthority("ROLE_USER")
            );
        }

        return new JwtAuthenticationToken(
                jwt,
                authorities,
                userId.toString()
        );
    }
}
