package org.jtorrent.client.metainfo;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.Objects;

public class PeerId {
    public static final int PEER_ID_LENGTH = 20;
    public static final String JTORRENT_PREFIX = "-JB-0001-";

    private final String id;

    public PeerId(String id) {
        if (id.length() != PEER_ID_LENGTH) {
            throw new IllegalArgumentException("Malformed peer id");
        }
        this.id = id;
    }

    public static PeerId generateJTorrentRandom() {
        return new PeerId(JTORRENT_PREFIX + RandomStringUtils.randomAlphanumeric(PEER_ID_LENGTH - JTORRENT_PREFIX.length()));
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerId peerId = (PeerId) o;
        return Objects.equals(id, peerId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
