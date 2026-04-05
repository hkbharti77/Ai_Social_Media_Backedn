package com.aiplatform.scheduler;

import com.aiplatform.service.OwnerSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityScheduler {

    private final OwnerSecurityService ownerSecurityService;

    /**
     * Automatically rotates the platform owner's password every Monday at midnight.
     * Cron: "0 0 0 * * MON" (Seconds Minutes Hours Day Month Weekday)
     * Timezone: Asia/Kolkata (IST)
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Kolkata")
    public void scheduledOwnerPasswordRotation() {
        log.info("🗓️ [SecurityScheduler] Weekly Security Audit: Rotating Owner Password...");
        try {
            String result = ownerSecurityService.rotateOwnerPassword();
            log.info("✅ [SecurityScheduler] Weekly Rotation Successful: {}", result);
        } catch (Exception e) {
            log.error("❌ [SecurityScheduler] Weekly Rotation Failed!", e);
        }
    }
}
