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
}
