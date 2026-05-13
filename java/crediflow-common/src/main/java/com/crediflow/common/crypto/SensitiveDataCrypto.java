package com.crediflow.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 应用层字段加密。密钥由环境变量 {@value #ENV_KEY} 提供（32 字节 Base64）。
 */
public final class SensitiveDataCrypto {

    public static final String ENV_KEY = "CREDIFLOW_AES256_KEY";

    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public SensitiveDataCrypto(byte[] rawKey32) {
        if (rawKey32 == null || rawKey32.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        this.secretKey = new SecretKeySpec(rawKey32, AES);
    }

    public static SensitiveDataCrypto fromEnvOrNull() {
        String b64 = System.getenv(ENV_KEY);
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        byte[] key = Base64.getDecoder().decode(b64.trim());
        return new SensitiveDataCrypto(key);
    }

    public String encryptToBase64(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decryptFromBase64(String blob) {
        if (blob == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(blob);
            ByteBuffer buf = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] cipherBytes = new byte[buf.remaining()];
            buf.get(cipherBytes);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}
