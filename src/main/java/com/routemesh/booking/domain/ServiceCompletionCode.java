package com.routemesh.booking.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/** Booking-scoped service completion code. Only an HMAC bound to the booking ID is persisted. */
public final class ServiceCompletionCode {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public ServiceCompletionCode(String key) {
        if (key == null || key.length() < 16) throw new IllegalStateException("routemesh.security.otp-key must be at least 16 characters");
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public static String newCode() { return String.format("%06d", RANDOM.nextInt(1_000_000)); }

    public String hash(UUID bookingId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("service-completion:" + bookingId + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matches(UUID bookingId, String code, String storedHash) {
        if (code == null || !code.matches("\\d{6}") || storedHash == null) return false;
        return MessageDigest.isEqual(hash(bookingId, code).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
