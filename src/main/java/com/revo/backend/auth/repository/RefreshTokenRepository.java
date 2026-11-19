package com.revo.backend.auth.repository;

import com.revo.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {
}