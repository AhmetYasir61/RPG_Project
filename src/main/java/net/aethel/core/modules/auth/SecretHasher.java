package net.aethel.core.modules.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * PIN/parola karma islemi. PBKDF2-HMAC-SHA256 kullanilir: her kaydin kendi tuzu olur
 * ve iterasyon sayisi kaba kuvvet denemesini pratikte imkansiz hale getirir.
 */
final class SecretHasher {

    /**
     * Duz SHA-256 yeterli degildir: GPU ile saniyede milyarlarca deneme yapilabilir ve
     * ozellikle 4 haneli PIN'ler aninda kirilir. PBKDF2 her denemeyi pahali hale
     * getirir; 210.000 iterasyon OWASP'in SHA-256 icin onerdigi guncel alt sinirdir.
     */
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretHasher() {}

    /** Ciktı bicimi: iterasyon:tuz(hex):karma(base64) */
    static String hash(String secret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] digest = derive(secret, salt, ITERATIONS);
        return ITERATIONS + ":" + HexFormat.of().formatHex(salt) + ":"
                + Base64.getEncoder().encodeToString(digest);
    }

    /** Zamanlama saldirisina karsi sabit sureli karsilastirma yapar. */
    static boolean verify(String secret, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = HexFormat.of().parseHex(parts[1]);
        byte[] expected = Base64.getDecoder().decode(parts[2]);
        byte[] actual = derive(secret, salt, iterations);
        return java.security.MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(String secret, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 kullanilamiyor", e);
        }
    }
}
