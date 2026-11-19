package com.revo.backend.auth.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_otp", nullable = false, length = 254)
    private String targetOtp;

    @Column(nullable = false, length = 32)
    private String purpose = "REGISTER";

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    protected OtpCode() {
    }

    public OtpCode(
            User user,
            String targetOtp,
            String codeHash,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.targetOtp = targetOtp;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public String getTargetOtp() {
        return targetOtp;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void incrementFailedAttempts() {
        if (failedAttempts < 5) {
            failedAttempts++;
        }
    }

    public void markConsumed(LocalDateTime time) {
        this.consumedAt = time;
    }


}
