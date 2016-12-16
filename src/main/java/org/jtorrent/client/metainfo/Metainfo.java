package org.jtorrent.client.metainfo;

import java.util.List;
import java.util.Optional;

/**
 * Created by Aleksei Latyshev on 16.12.2016.
 */

public class Metainfo {
    private String announce;
    private int pieceLength;
    private List<String> pieces;
    private List<TorrentFileInfo> files;
}