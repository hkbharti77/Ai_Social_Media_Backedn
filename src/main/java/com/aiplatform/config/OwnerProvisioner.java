package com.aiplatform.config;

import com.aiplatform.service.OwnerSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OwnerProvisioner implements CommandLineRunner {

    private final OwnerSecurityService ownerSecurityService;

    @Override
    public void run(String... args) throws Exception {
        log.info("🛠️ [OwnerProvisioner] Checking owner account status...");
        ownerSecurityService.provisionOwner();
        log.info("✅ [OwnerProvisioner] Owner account verified. Password preserved across restarts (weekly rotation scheduled via SecurityScheduler).");
    }
}
