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
        log.info("\ud83d\udee0\ufe0f [OwnerProvisioner] Checking owner account status...");
        ownerSecurityService.provisionOwner();
        
        // As requested: Rotate password on EVERY server restart for maximum security (Owner only access)
        log.info("\ud83d\udd10 [OwnerProvisioner] Rotating Owner Password on Startup...");
        try {
            ownerSecurityService.rotateOwnerPassword();
            log.info("\u2705 [OwnerProvisioner] Startup Security Rotation Successful. Email: {}", ownerSecurityService.getOwnerEmail());
        } catch (Exception e) {
            log.error("\u274c [OwnerProvisioner] Startup Security Rotation Failed!", e);
        }
    }
}
