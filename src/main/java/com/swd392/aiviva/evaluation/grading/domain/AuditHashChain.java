package com.swd392.aiviva.evaluation.grading.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AuditHashChain {
    private AuditHashChain() { }

    public static String hash(String previousHash, String canonicalEvent) {
        if (canonicalEvent == null) throw new IllegalArgumentException("Canonical audit event is required");
        String input = (previousHash == null ? "" : previousHash) + "\n" + canonicalEvent;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
