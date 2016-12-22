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
    public static final String EVENT = "event";
    public static final String UPLOADED = "uploaded";
    public static final String DOWNLOADED = "downloaded";
    public static final String LEFT = "left";
    public static final String COMPACT = "compact";
    public static final String NO_PEER_ID = "no_peer_id";
    public static final String PORT = "port";
    public static final String PEER_ID = "peer_id";
    public static final String INFO_HASH = "info_hash";

    public static final String STARTED_EVENT = "started";
    public static final String INITIAL_UPLOADED = "0";
    public static final String INITIAL_DOWNLOADED = "0";
    public static final String IS_COMPACT = "0";
    public static final String IS_PEER_NOT_NEEDED = "0";
    public static final String DEFAULT_PORT = "6881";

    private final Metainfo metainfo;
    private final PeerId selfPeerId;

    private final HttpClient client = HttpClientBuilder.create().build();
    private final TrackerAnswerParser trackerAnswerParser = TrackerAnswerParser.getInstance();
    private final BencodeParser bencodeParser = BencodeParser.getInstance();

    public TrackerConnection(Metainfo metainfo, PeerId selfPeerId) {
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
                    .addParameter(PORT, DEFAULT_PORT)
                    .addParameter(EVENT, STARTED_EVENT)
                    .addParameter(UPLOADED, INITIAL_UPLOADED)
                    .addParameter(DOWNLOADED, INITIAL_DOWNLOADED)
                    .addParameter(LEFT, String.valueOf(filesSize))
                    .addParameter(COMPACT, IS_COMPACT)
                    .addParameter(NO_PEER_ID, IS_PEER_NOT_NEEDED);
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
