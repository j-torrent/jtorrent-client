package org.jtorrent.client.tracker;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jtorrent.client.bencode.BencodeParser;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.metainfo.TorrentFileInfo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class TrackerConnection {
    private static final String EVENT = "event";
    private static final String UPLOADED = "uploaded";
    private static final String DOWNLOADED = "downloaded";
    private static final String LEFT = "left";
    private static final String COMPACT = "compact";
    private static final String NO_PEER_ID = "no_peer_id";
    private static final String PORT = "port";
    private static final String PEER_ID = "peer_id";
    private static final String INFO_HASH = "info_hash";

    private final boolean isCompact;
    private final boolean isPeerNotNeeded;
    private final int port;
    private final int downloaded;
    private final int uploaded;
    private final Event event;

    private final Metainfo metainfo;
    private final PeerId selfPeerId;

    private final HttpClient client = HttpClientBuilder.create().build();
    private final TrackerAnswerParser trackerAnswerParser = TrackerAnswerParser.getInstance();
    private final BencodeParser bencodeParser = BencodeParser.getInstance();

    TrackerConnection(boolean isCompact, boolean isPeerNotNeeded, int port,
                      int downloaded, int uploaded, Event event,
                      Metainfo metainfo, PeerId selfPeerId) {
        this.isCompact = isCompact;
        this.isPeerNotNeeded = isPeerNotNeeded;
        this.port = port;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
        this.event = event;
        this.metainfo = metainfo;
        this.selfPeerId = selfPeerId;
    }

    public TrackerAnswer makeInitialRequest() throws IOException {
        try {
            long filesSize = metainfo.getFiles().stream().map(TorrentFileInfo::getLengthInBytes).mapToLong(i -> i).sum();
            URIBuilder builder = new URIBuilder(metainfo.getAnnounce());
            builder.setCharset(StandardCharsets.ISO_8859_1);
            builder.addParameter(INFO_HASH, new String(metainfo.getInfoSHA1(), StandardCharsets.ISO_8859_1))
                    .addParameter(PEER_ID, selfPeerId.getId())
                    .addParameter(PORT, String.valueOf(port))
                    .addParameter(EVENT, event.name)
                    .addParameter(UPLOADED, String.valueOf(uploaded))
                    .addParameter(DOWNLOADED, String.valueOf(downloaded))
                    .addParameter(LEFT, String.valueOf(filesSize))
                    .addParameter(COMPACT, isCompact ? "1" : "0")
                    .addParameter(NO_PEER_ID, isPeerNotNeeded ? "1" : "0");
            HttpGet request = new HttpGet(builder.build());
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                return trackerAnswerParser.parse(bencodeParser.parse(response.getEntity().getContent()));
            } else {
                throw new IllegalStateException("Invalid response from tracker");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid metainfo announce", e);
        }
    }
}
