package org.jtorrent.client.bencode;

import org.jtorrent.client.util.SHA1Digester;

import java.nio.charset.StandardCharsets;

public interface BObject {
    default byte[] calculateSHA1() {
        return SHA1Digester.getInstance().digest(toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
