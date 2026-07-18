package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_deletion_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataDeletionStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmation_code", nullable = false, unique = true)
    private String confirmationCode;

    @Column(name = "facebook_user_id", nullable = false)
    private String facebookUserId;

    @Column(nullable = false)
    private String status; // 'PENDING' or 'COMPLETED'

    @Builder.Default
    @Column(name = "requested_at")
    private LocalDateTime requestedAt = LocalDateTime.now();
}
