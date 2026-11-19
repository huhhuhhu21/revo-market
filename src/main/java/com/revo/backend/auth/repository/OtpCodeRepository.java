package com.revo.backend.auth.repository;

import com.revo.backend.auth.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpCode> findFirstByUser_IdAndPurposeOrderByIdDesc(
            Long userId,
            String purpose
    );
}