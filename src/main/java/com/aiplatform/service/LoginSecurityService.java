package com.aiplatform.service;

import com.aiplatform.model.LoginEvent;
import com.aiplatform.model.User;
import com.aiplatform.repository.LoginEventRepository;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginSecurityService {

    private final LoginEventRepository loginEventRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Called on every successful login.
     * Records the event and sends a security alert if IP is new.
     */
    @Transactional
    public void recordSuccessfulLogin(User user, String ipAddress, String userAgent) {
        boolean isNewIp = !loginEventRepository.existsByUserAndIpAddress(user, ipAddress);

        // Parse device info from User-Agent
        DeviceInfo device = parseUserAgent(userAgent);

        // Save login event
        LoginEvent event = LoginEvent.builder()
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .browser(device.browser)
                .operatingSystem(device.os)
                .deviceType(device.deviceType)
                .status("SUCCESS")
                .isNewIp(isNewIp)
                .createdAt(LocalDateTime.now())
                .build();
        loginEventRepository.save(event);

        // Update user's last login metadata
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        user.setLastLoginUserAgent(userAgent);
        user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0L) + 1);
        userRepository.save(user);

        // Send security alert for new IP
        if (isNewIp) {
            log.info("🔐 [LoginSecurity] New IP detected for user {} from {}", user.getEmail(), ipAddress);
            try {
                emailService.sendNewLoginAlertEmail(user, ipAddress, device, isNewIp,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));
            } catch (Exception e) {
                log.warn("⚠️ [LoginSecurity] Could not send login alert email: {}", e.getMessage());
            }
        }
    }

    /**
     * Called on every failed login attempt.
     * Records the event for audit trail.
     */
    @Transactional
    public void recordFailedLogin(User user, String ipAddress, String userAgent, String reason) {
        if (user == null) return; // Unknown email - don't log

        DeviceInfo device = parseUserAgent(userAgent);

        LoginEvent event = LoginEvent.builder()
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .browser(device.browser)
                .operatingSystem(device.os)
                .deviceType(device.deviceType)
                .status("FAILED")
                .failureReason(reason)
                .isNewIp(!loginEventRepository.existsByUserAndIpAddress(user, ipAddress))
                .createdAt(LocalDateTime.now())
                .build();
        loginEventRepository.save(event);

        // Check for brute-force: 5+ failures in 10 minutes
        long recentFailures = loginEventRepository.countFailedLoginsAfter(
                user, LocalDateTime.now().minusMinutes(10));
        if (recentFailures >= 5) {
            log.warn("🚨 [LoginSecurity] Brute-force detected for user {} from IP {}: {} failures in 10 min",
                    user.getEmail(), ipAddress, recentFailures);
        }
    }

    /**
     * Enterprise-grade User-Agent parser.
     * Detects browser, OS, and device type without external libraries.
     */
    public DeviceInfo parseUserAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return new DeviceInfo("Unknown Browser", "Unknown OS", "DESKTOP");
        }
        String uaLower = ua.toLowerCase();

        // ── Device Type ──────────────────────────────────────────────────────
        String deviceType;
        if (uaLower.contains("mobile") || uaLower.contains("android") && uaLower.contains("mobile")) {
            deviceType = "MOBILE";
        } else if (uaLower.contains("tablet") || uaLower.contains("ipad")) {
            deviceType = "TABLET";
        } else {
            deviceType = "DESKTOP";
        }

        // ── Browser Detection ────────────────────────────────────────────────
        String browser;
        if (uaLower.contains("edg/") || uaLower.contains("edge/")) {
            browser = "Microsoft Edge";
        } else if (uaLower.contains("opr/") || uaLower.contains("opera")) {
            browser = "Opera";
        } else if (uaLower.contains("brave")) {
            browser = "Brave";
        } else if (uaLower.contains("chrome") && !uaLower.contains("chromium")) {
            // Extract Chrome version
            String version = extractVersion(ua, "Chrome/");
            browser = "Chrome" + (version != null ? " " + version : "");
        } else if (uaLower.contains("firefox")) {
            String version = extractVersion(ua, "Firefox/");
            browser = "Firefox" + (version != null ? " " + version : "");
        } else if (uaLower.contains("safari") && !uaLower.contains("chrome")) {
            String version = extractVersion(ua, "Version/");
            browser = "Safari" + (version != null ? " " + version : "");
        } else if (uaLower.contains("msie") || uaLower.contains("trident")) {
            browser = "Internet Explorer";
        } else if (uaLower.contains("postman")) {
            browser = "Postman";
        } else if (uaLower.contains("curl")) {
            browser = "cURL";
        } else {
            browser = "Unknown Browser";
        }

        // ── OS Detection ─────────────────────────────────────────────────────
        String os;
        if (uaLower.contains("windows nt 10") || uaLower.contains("windows nt 11")) {
            os = "Windows 10/11";
        } else if (uaLower.contains("windows nt 6.3")) {
            os = "Windows 8.1";
        } else if (uaLower.contains("windows nt 6.1")) {
            os = "Windows 7";
        } else if (uaLower.contains("windows")) {
            os = "Windows";
        } else if (uaLower.contains("mac os x") || uaLower.contains("macos")) {
            os = "macOS";
        } else if (uaLower.contains("iphone")) {
            os = "iOS (iPhone)";
        } else if (uaLower.contains("ipad")) {
            os = "iOS (iPad)";
        } else if (uaLower.contains("android")) {
            String version = extractVersion(ua, "Android ");
            os = "Android" + (version != null ? " " + version : "");
        } else if (uaLower.contains("linux")) {
            os = "Linux";
        } else if (uaLower.contains("ubuntu")) {
            os = "Ubuntu";
        } else {
            os = "Unknown OS";
        }

        return new DeviceInfo(browser, os, deviceType);
    }

    private String extractVersion(String ua, String prefix) {
        try {
            int idx = ua.indexOf(prefix);
            if (idx == -1) return null;
            String after = ua.substring(idx + prefix.length());
            // Take only major.minor version
            String[] parts = after.split("[\\s;)]+")[0].split("\\.");
            return parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get recent login history for a user (last 20 events - success + failed).
     */
    public List<LoginEvent> getRecentLogins(User user) {
        return loginEventRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().limit(20).toList();
    }

    /**
     * Value object for parsed device information.
     */
    public static class DeviceInfo {
        public final String browser;
        public final String os;
        public final String deviceType;

        public DeviceInfo(String browser, String os, String deviceType) {
            this.browser = browser;
            this.os = os;
            this.deviceType = deviceType;
        }

        public String getDeviceEmoji() {
            return switch (deviceType) {
                case "MOBILE"  -> "📱";
                case "TABLET"  -> "📟";
                default        -> "💻";
            };
        }
    }
}
