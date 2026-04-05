package com.aiplatform.service;

import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final UserRepository userRepository;

    private static final List<String> DISPOSABLE_EMAIL_DOMAINS = Arrays.asList(
            "mailinator.com", "temp-mail.org", "guerrillamail.com", "10minutemail.com", "trashmail.com"
    );

    public boolean isDisposableEmail(String email) {
        if (email == null) return false;
        String domain = email.substring(email.lastIndexOf("@") + 1).toLowerCase();
        return DISPOSABLE_EMAIL_DOMAINS.contains(domain);
    }

    public boolean isPotentialSelfReferral(User newUser, String referrerCode) {
        if (referrerCode == null || referrerCode.isBlank()) return false;

        Optional<User> referrerOpt = userRepository.findByReferralCode(referrerCode);
        if (referrerOpt.isEmpty()) return false;

        User referrer = referrerOpt.get();

        // Check if IP matches
        if (newUser.getRegistrationIp() != null && 
            newUser.getRegistrationIp().equals(referrer.getRegistrationIp())) {
            return true;
        }

        // Check if Fingerprint matches
        if (newUser.getDeviceFingerprint() != null && 
            newUser.getDeviceFingerprint().equals(referrer.getDeviceFingerprint())) {
            return true;
        }

        return false;
    }
    
    public void evaluateFraud(User user) {
        if (isDisposableEmail(user.getEmail())) {
            user.setIsFraudFlagged(true);
        }
        
        if (user.getReferredBy() != null) {
            if (isPotentialSelfReferral(user, user.getReferredBy())) {
                user.setIsFraudFlagged(true);
                user.setReferralStatus("REJECTED");
            }
        }
    }
}
