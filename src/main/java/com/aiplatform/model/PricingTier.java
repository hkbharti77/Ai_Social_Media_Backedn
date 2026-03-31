package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "pricing_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String priceInr;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long priceAmount; // Price in INR (not paise, we'll convert to paise for RZP)

    private Double monthlyCredits;
    private Integer dailyLimit;
    private Integer maxProfiles;

    @ElementCollection
    @CollectionTable(name = "tier_features", joinColumns = @JoinColumn(name = "tier_id"))
    @Column(name = "feature")
    private List<String> features;

    private Boolean popular;
}
