
package com.revo.backend.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        USER, ADMIN
    }

    public enum Status {
        PENDING_VERIFICATION, ACTIVE, RESTRICTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING_VERIFICATION;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "login_failed_at",
            nullable = false,
            columnDefinition = "json"
    )
    private List<String> loginFailedAt = new ArrayList<>();

    @Column(name = "login_locked_until")
    private LocalDateTime loginLockedUntil;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    protected User() {
    }

    public User(
            String fullName,
            String email,
            String phone,
            String passwordHash
    ) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Long getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void activateWithEmail() {
        this.emailVerified = true;
        this.status = Status.ACTIVE;
    }

    public void activateWithPhone() {
        this.phoneVerified = true;
        this.status = Status.ACTIVE;
    }

    public boolean isLoginLocked(Instant now) {
        if (loginLockedUntil == null) {
            return false;
        }

        Instant lockedUntil =
                loginLockedUntil.toInstant(ZoneOffset.UTC);

        return now.isBefore(lockedUntil);
    }


    public boolean recordFailedLogin(Instant now) {

        Instant windowStart = now.minus(15, ChronoUnit.MINUTES);

        List<String> recentFailures = new ArrayList<>();

        for (String timestamp : loginFailedAt) {
            Instant failedAt = Instant.parse(timestamp);

            if (failedAt.isAfter(windowStart)) {
                recentFailures.add(timestamp);
            }
        }

        recentFailures.add(now.toString());

        this.loginFailedAt = recentFailures;

        if (recentFailures.size() >= 5) {
            this.loginLockedUntil = LocalDateTime.ofInstant(
                    now.plus(15, ChronoUnit.MINUTES),
                    ZoneOffset.UTC
            );

            return true;
        }

        this.loginLockedUntil = null;
        return false;
    }

    public void clearLoginFailures() {
        this.loginFailedAt = new ArrayList<>();
        this.loginLockedUntil = null;
    }


}
