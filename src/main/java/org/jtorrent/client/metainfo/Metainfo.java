package org.jtorrent.client.metainfo;

import java.util.Collections;
import java.util.List;

/**
 * Created by Aleksei Latyshev on 16.12.2016.
 */

public class Metainfo {
    private final String announce;
    private final byte[] infoSHA1;
    private final long pieceLength;
    private final List<byte[]> pieces;
    private final List<TorrentFileInfo> files;

    public Metainfo(String announce, byte[] infoSHA1, long pieceLength, List<byte[]> pieces, List<TorrentFileInfo> files) {
        this.announce = announce;
        this.infoSHA1 = infoSHA1;
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

    public List<byte[]> getPieces() {
        return pieces;
    }

    public List<TorrentFileInfo> getFiles() {
        return files;
    }

    public byte[] getInfoSHA1() {
        return infoSHA1;
    }

    public int getPieceLength(int index) {
        if (index == pieces.size() - 1) {
            long allOtherPieces = getPieceLength() * (getPieces().size() - 1);
            long allFiles = getFiles().stream().mapToLong(TorrentFileInfo::getLengthInBytes).sum();
            return (int) (allFiles - allOtherPieces);
        } else {
            return (int) pieceLength;
        }
    }

    public long getFilesLength() {
        return files.stream().mapToLong(TorrentFileInfo::getLengthInBytes).sum();
    }
}