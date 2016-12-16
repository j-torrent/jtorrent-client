package org.jtorrent.client.metainfo;

/**
 * Created by Aleksei Latyshev on 16.12.2016.
 */

public class TorrentFileInfo {
    private final long lengthInBytes;
    private final String path;

    public TorrentFileInfo(long lengthInBytes, String path) {
        this.lengthInBytes = lengthInBytes;
        this.path = path;
    }

    public long getLengthInBytes() {
        return lengthInBytes;
    }

    public String getPath() {
        return path;
    }
}