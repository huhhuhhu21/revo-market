package com.revo.backend.auth.repository;

import com.revo.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    @Query("""
        SELECT rt.id
        FROM RefreshToken rt
        WHERE rt.tokenHash = :tokenHash
        """)
    Optional<Long> findIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rt
        FROM RefreshToken rt
        WHERE rt.id = :id
        """)
    Optional<RefreshToken> findByIdForUpdate(
            @Param("id") Long id
    );


    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revokedAt = :revokedAt
        WHERE rt.user.id = :userId
          AND rt.revokedAt IS NULL
        """)
    int revokeAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") LocalDateTime revokedAt
    );


}