package com.aiplatform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@lombok.extern.slf4j.Slf4j
public class EncryptionUtils {

    @org.springframework.beans.factory.annotation.Value("${encryption.secret}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String LEGACY_TRANSFORMATION = "AES";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String V2_PREFIX = "v2:";

    public String encrypt(String data) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new java.security.SecureRandom().nextBytes(iv);

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(GCM_TRANSFORMATION);
        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        byte[] keyBytes = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] finalKey = new byte[16];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 16));
        
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(finalKey, ALGORITHM);
        
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
        byte[] ciphertext = cipher.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return V2_PREFIX + java.util.Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedData) throws Exception {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return null;
        }

        if (encryptedData.startsWith(V2_PREFIX)) {
            return decryptGcm(encryptedData.substring(V2_PREFIX.length()));
        } else {
            return decryptLegacy(encryptedData);
        }
    }

    private String decryptGcm(String base64Data) throws Exception {
        byte[] combined = java.util.Base64.getDecoder().decode(base64Data);
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        
        int ciphertextLength = combined.length - GCM_IV_LENGTH;
        byte[] ciphertext = new byte[ciphertextLength];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(GCM_TRANSFORMATION);
        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        byte[] keyBytes = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] finalKey = new byte[16];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 16));
        
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(finalKey, ALGORITHM);
        
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        byte[] decrypted = cipher.doFinal(ciphertext);
        
        return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String encryptedData) throws Exception {
        byte[] keyBytes = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] finalKey = new byte[16];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 16));
        
        javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(finalKey, ALGORITHM);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(LEGACY_TRANSFORMATION);
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key);
        byte[] decoded = java.util.Base64.getDecoder().decode(encryptedData);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
    }
}
