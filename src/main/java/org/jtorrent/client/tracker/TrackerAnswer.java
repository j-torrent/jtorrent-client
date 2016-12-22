package org.jtorrent.client.tracker;

import java.util.Collections;
import java.util.List;

public class TrackerAnswer {
    private final List<Peer> peers;
    private final long interval;

    public TrackerAnswer(List<Peer> peers, long interval) {
        this.peers = Collections.unmodifiableList(peers);
        this.interval = interval;
    }

    public List<Peer> getPeers() {
        return peers;
    }

    public long getInterval() {
        return interval;
    }
}
