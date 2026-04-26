package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "roles", "socialAccounts"})
    private User user;

    @Column(nullable = false)
    private Double amount; // Number of credits used (usually 1, but can be 0.25)

    @Column(nullable = false)
    private String purpose; // e.g., "AI Image Generation", "Caption Draft"

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Optional: Reference to a post or media if applicable
    private Long referenceId;
}
