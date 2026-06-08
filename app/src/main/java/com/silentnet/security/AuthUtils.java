package com.silentnet.security;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AuthUtils {

    private AuthUtils() {
    }

    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    public static String hashPassword(String password, String salt) {
        try {
            char[] chars = password.toCharArray();
            byte[] saltBytes = Base64.decode(salt, Base64.DEFAULT);

            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(chars, saltBytes, ITERATIONS, KEY_LENGTH);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateSalt() {
        java.security.SecureRandom sr = new java.security.SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static boolean verifyPassword(String password, String salt, String storedHash) {
        String newHash = hashPassword(password, salt);
        return newHash.equals(storedHash);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String rawPassword, String storedHash) {
        return sha256(rawPassword).equals(storedHash);
    }
}
