package com.aiplatform.controller;

import com.aiplatform.dto.AuthDtos.MessageResponse;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class AdController {

    private final UserRepository userRepository;

    @PostMapping("/ad-start")
    public ResponseEntity<MessageResponse> startAdSession() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();

        user.setLastAdStartedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Ad session synchronized. Watch for 30s to claim."));
    }

    @PostMapping("/ad-reward")
    public ResponseEntity<MessageResponse> rewardUserForAd() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();

        // 1. Subscription Check (Hardened)
        if (user.getSubscriptionTier() != null && "SUPER_PRO".equals(user.getSubscriptionTier().name())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Super Pro users do not receive ads or ad-based rewards."));
        }

        // 2. Handshake Validation (Security Hardening)
        if (user.getLastAdStartedAt() == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Ad session not started properly."));
        }

        long secondsElapsed = Duration.between(user.getLastAdStartedAt(), LocalDateTime.now()).toSeconds();
        if (secondsElapsed < 25) { // 5s buffer for network lag
            return ResponseEntity.status(HttpStatus.TOO_EARLY)
                    .body(new MessageResponse("Error: Ad not watched completely (" + secondsElapsed + "/30s)."));
        }

        // 3. Daily Limit Check
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        if (user.getLastAdViewedAt() != null && user.getLastAdViewedAt().isAfter(today)) {
            if (user.getDailyAdsViewed() >= 10) {
                return ResponseEntity.badRequest().body(new MessageResponse("Daily limit of 10 ads reached. Try again tomorrow!"));
            }
        } else {
            user.setDailyAdsViewed(0); // Reset for new day
        }

        // 4. Grant Reward
        user.setBonusCredits((user.getBonusCredits() != null ? user.getBonusCredits() : 0.0) + 0.5);
        user.setDailyAdsViewed(user.getDailyAdsViewed() + 1);
        user.setLastAdViewedAt(LocalDateTime.now());
        user.setLastAdStartedAt(null); // One-time use

        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Successfully earned 0.5 bonus credits!"));
    }
}
