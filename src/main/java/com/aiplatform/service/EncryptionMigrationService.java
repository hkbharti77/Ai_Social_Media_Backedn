package com.aiplatform.service;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.repository.SocialAccountRepository;
import com.aiplatform.security.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncryptionMigrationService {

    private final SocialAccountRepository socialAccountRepository;
    private final EncryptionUtils encryptionUtils;

    private static final String V2_PREFIX = "v2:";

    /**
     * Automatically migrates all legacy-encrypted social tokens to the new AES/GCM format.
     */
    @PostConstruct
    @Transactional
    public void migrateLegacyTokens() {
        log.info("🛡️ [SecurityMigration] Checking for legacy-encrypted social accounts...");
        
        List<SocialAccount> accounts = socialAccountRepository.findAll();
        int migratedCount = 0;

        for (SocialAccount account : accounts) {
            boolean needsMigration = false;
            
            // 1. Check Access Token
            String encryptedAccessToken = account.getEncryptedAccessToken();
            if (encryptedAccessToken != null && !encryptedAccessToken.startsWith(V2_PREFIX)) {
                try {
                    String decrypted = encryptionUtils.decrypt(encryptedAccessToken);
                    account.setEncryptedAccessToken(encryptionUtils.encrypt(decrypted));
                    needsMigration = true;
                } catch (Exception e) {
                    log.error("❌ Failed to migrate access token for account id {}: {}", account.getId(), e.getMessage());
                }
            }

            // 2. Check Refresh Token
            String encryptedRefreshToken = account.getEncryptedRefreshToken();
            if (encryptedRefreshToken != null && !encryptedRefreshToken.startsWith(V2_PREFIX)) {
                try {
                    String decrypted = encryptionUtils.decrypt(encryptedRefreshToken);
                    account.setEncryptedRefreshToken(encryptionUtils.encrypt(decrypted));
                    needsMigration = true;
                } catch (Exception e) {
                    log.error("❌ Failed to migrate refresh token for account id {}: {}", account.getId(), e.getMessage());
                }
            }

            if (needsMigration) {
                socialAccountRepository.save(account);
                migratedCount++;
            }
        }

        if (migratedCount > 0) {
            log.info("✅ [SecurityMigration] Successfully migrated {} social accounts to new encryption format.", migratedCount);
        } else {
            log.info("🛡️ [SecurityMigration] No legacy tokens found. System is up to date.");
        }
    }
}
