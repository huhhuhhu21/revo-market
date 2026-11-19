package com.revo.backend.auth.repository;

import com.revo.backend.auth.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
}