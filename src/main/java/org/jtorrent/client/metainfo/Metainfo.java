package org.jtorrent.client.metainfo;

import java.util.Collections;
import java.util.List;

/**
 * Created by Aleksei Latyshev on 16.12.2016.
 */

public class Metainfo {
    private final String announce;
    private final long pieceLength;
    private final List<String> pieces;
    private final List<TorrentFileInfo> files;

    public Metainfo(String announce, long pieceLength, List<String> pieces, List<TorrentFileInfo> files) {
        this.announce = announce;
        this.pieceLength = pieceLength;
        this.pieces = Collections.unmodifiableList(pieces);
        this.files = Collections.unmodifiableList(files);
    }

    public String getAnnounce() {
        return announce;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public List<String> getPieces() {
        return pieces;
    }

    public List<TorrentFileInfo> getFiles() {
        return files;
    }
}