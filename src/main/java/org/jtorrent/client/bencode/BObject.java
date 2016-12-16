package org.jtorrent.client.bencode;

import org.jtorrent.client.util.SHA1Digester;

public interface BObject {
    default byte[] calculateSHA1() {
        return SHA1Digester.getInstance().digest(toString());
    }
}
