package org.jtorrent.client.tracker;

import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;

/**
 * @author Daniyar Itegulov
 */
public class TrackerConnectionBuilder {
    public static final int DEFAULT_PORT = 6881;

    private boolean isCompact = false;
    private boolean isPeerNotNeeded = false;
    private int port = DEFAULT_PORT;
    private int downloaded = 0;
    private int uploaded = 0;
    private Event event = Event.STARTED;
    private int trackerPort = 80;

    private Metainfo metainfo;
    private PeerId myPeerId;

    public TrackerConnectionBuilder setCompact(boolean compact) {
        isCompact = compact;
        return this;
    }

    public TrackerConnectionBuilder setPeerNotNeeded(boolean peerNotNeeded) {
        isPeerNotNeeded = peerNotNeeded;
        return this;
    }

    public TrackerConnectionBuilder setPort(int port) {
        this.port = port;
        return this;
    }

    public TrackerConnectionBuilder setDownloaded(int downloaded) {
        this.downloaded = downloaded;
        return this;
    }

    public TrackerConnectionBuilder setUploaded(int uploaded) {
        this.uploaded = uploaded;
        return this;
    }

    public TrackerConnectionBuilder setEvent(Event event) {
        this.event = event;
        return this;
    }

    public TrackerConnectionBuilder setMetainfo(Metainfo metainfo) {
        this.metainfo = metainfo;
        return this;
    }

    public TrackerConnectionBuilder setMyPeerId(PeerId myPeerId) {
        this.myPeerId = myPeerId;
        return this;
    }

    public TrackerConnectionBuilder setTrackerPort(int trackerPort) {
        this.trackerPort = trackerPort;
        return this;
    }

    public TrackerConnection build() {
        if (metainfo == null) {
            throw new IllegalArgumentException("Metainfo should be set");
        }
        if (myPeerId == null) {
            throw new IllegalArgumentException("Peer id should be set");
        }
        return new TrackerConnection(isCompact, isPeerNotNeeded, port,
                downloaded, uploaded, event, metainfo, myPeerId, trackerPort);
    }
}
