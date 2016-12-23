package org.jtorrent.client.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA1Digester {
    private static final SHA1Digester INSTANCE = new SHA1Digester();

    private final MessageDigest sha1Md;

    private SHA1Digester() {
        try {
            sha1Md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Couldn't find SHA-1 algorithm instance", e);
        }
    }

    public byte[] digest(byte[] bytes) {
        return sha1Md.digest(bytes);
    }

    public static SHA1Digester getInstance() {
        return INSTANCE;
    }
}
