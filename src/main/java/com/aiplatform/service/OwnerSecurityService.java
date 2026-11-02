package com.aiplatform.service;

import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OwnerSecurityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.security.owner-email}")
    private String ownerEmail;

    public String getOwnerEmail() {
        return ownerEmail;
    }

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private final SecureRandom random = new SecureRandom();

    /**
     * Centralized check to verify if an email belongs to the platform owner.
     */
    public boolean isOwner(String email) {
        return ownerEmail != null && ownerEmail.equalsIgnoreCase(email);
    }

    /**
     * Ensures the owner account exists on system startup.
     * Preserves existing password across server restarts (no email sent on restarts).
     * If the account is provisioned for the first time, sends initial credentials once.
     */
    @Transactional
    public void provisionOwner() {
        Optional<User> ownerOpt = userRepository.findByEmail(ownerEmail);
        if (ownerOpt.isEmpty()) {
            log.info("🛠️ [OwnerSecurity] Provisioning initial Owner account: {}", ownerEmail);
            String initialPassword = generateRandomPassword();
            User owner = User.builder()
                    .email(ownerEmail)
                    .fullName("GyanVaniAi Owner")
                    .password(passwordEncoder.encode(initialPassword))
                    .emailVerified(true)
                    .roles(new HashSet<>(Collections.singletonList("ROLE_ADMIN")))
                    .build();
            userRepository.save(owner);
            try {
                sendOwnerPasswordEmail(initialPassword);
                log.info("📧 [OwnerSecurity] Initial Owner credentials sent to {}", ownerEmail);
            } catch (Exception e) {
                log.error("❌ [OwnerSecurity] Failed to send initial owner credentials email: {}", e.getMessage());
            }
        } else {
            User owner = ownerOpt.get();
            if (!owner.getRoles().contains("ROLE_ADMIN")) {
                Set<String> roles = new HashSet<>(owner.getRoles());
                roles.add("ROLE_ADMIN");
                owner.setRoles(roles);
                userRepository.save(owner);
            }
            log.info("👤 [OwnerSecurity] Owner account verified: {} (password preserved across restarts, no email sent)", ownerEmail);
        }
    }

    /**
     * Rotates the owner's password and sends it to their email.
     * Invalidates the old password automatically.
     * Invoked weekly by SecurityScheduler or manually by OwnerController.
     */
    @Transactional
    public String rotateOwnerPassword() {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("Owner account not found!"));

        String rawPassword = generateRandomPassword();
        owner.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(owner);

        log.info("🔄 [OwnerSecurity] Password rotated for Owner. Sending to email...");
        
        sendOwnerPasswordEmail(rawPassword);
        
        return "Success: New password generated and sent to " + ownerEmail;
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private void sendOwnerPasswordEmail(String rawPassword) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("Owner account not found!"));
        emailService.sendOwnerRotationEmail(owner, rawPassword);
    }
}
