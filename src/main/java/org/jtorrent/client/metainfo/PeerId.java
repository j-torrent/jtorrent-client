package org.jtorrent.client.metainfo;

import org.apache.commons.lang3.RandomStringUtils;

public class PeerId {
    public static final int PEER_ID_LENGTH = 20;

    private final String id;

    public PeerId(String id) {
        if (id.length() != PEER_ID_LENGTH) {
            throw new IllegalArgumentException("Malformed peer id");
        }
        this.id = id;
    }

    public static PeerId generateRandom() {
        return new PeerId(RandomStringUtils.randomAlphanumeric(PEER_ID_LENGTH));
    }

    public String getId() {
        return id;
    }
}
