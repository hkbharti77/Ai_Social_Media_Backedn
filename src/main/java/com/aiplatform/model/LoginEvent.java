package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Enterprise login audit trail.
 * Stores every login attempt with full security metadata.
 */
@Entity
@Table(name = "login_events", indexes = {
    @Index(name = "idx_login_events_user_id", columnList = "user_id"),
    @Index(name = "idx_login_events_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** IP address of the login request */
    @Column(name = "ip_address")
    private String ipAddress;

    /** Raw User-Agent header */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /** Parsed browser name (e.g. Chrome, Firefox, Safari) */
    @Column(name = "browser")
    private String browser;

    /** Parsed OS name (e.g. Windows 11, macOS, Android) */
    @Column(name = "operating_system")
    private String operatingSystem;

    /** Device type: DESKTOP, MOBILE, TABLET */
    @Column(name = "device_type")
    private String deviceType;

    /** SUCCESS or FAILED */
    @Column(name = "status", nullable = false)
    private String status;

    /** Whether this was a new/unknown IP for this user */
    @Builder.Default
    @Column(name = "is_new_ip")
    private Boolean isNewIp = false;

    /** Failure reason if status = FAILED */
    @Column(name = "failure_reason")
    private String failureReason;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
