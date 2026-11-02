package com.aiplatform.config;

import com.aiplatform.service.OwnerSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class OwnerProvisioner implements CommandLineRunner {

    private final OwnerSecurityService ownerSecurityService;

    @Override
    public void run(String... args) throws Exception {
        log.info("🛠️ [OwnerProvisioner] Checking owner account status...");
        ownerSecurityService.provisionOwner();
        
        // Run rotation and email dispatch in background so it never blocks web server startup or health checks
        CompletableFuture.runAsync(() -> {
            log.info("🔐 [OwnerProvisioner] Rotating Owner Password on Startup...");
            try {
                ownerSecurityService.rotateOwnerPassword();
                log.info("✅ [OwnerProvisioner] Startup Security Rotation Successful. Email: {}", ownerSecurityService.getOwnerEmail());
            } catch (Exception e) {
                log.error("❌ [OwnerProvisioner] Startup Security Rotation Failed: {}", e.getMessage());
            }
        });
    }
}
