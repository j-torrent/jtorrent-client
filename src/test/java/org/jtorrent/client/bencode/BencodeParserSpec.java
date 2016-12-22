package org.jtorrent.client.bencode;

import org.jtorrent.client.client.TorrentClient;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.MetainfoParser;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.p2p.PeerConnection;
import org.jtorrent.client.tracker.Peer;
import org.jtorrent.client.tracker.TrackerConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BencodeParserSpec {
    public static void main(String[] args) throws IOException, InterruptedException {
        ClassLoader classLoader = BencodeParserSpec.class.getClassLoader();
        BencodeParser bencodeParser = BencodeParser.getInstance();
        BObject bObject = bencodeParser.parse(new FileInputStream(classLoader.getResource("dt.torrent").getFile()));
        Metainfo metainfo = MetainfoParser.getInstance().parse(bObject);
        try (TorrentClient torrentClient = new TorrentClient(metainfo)) {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        List<Thread> peerConnectionList = new ArrayList<>();
//        for (Peer peer : trackerConnection.makeInitialRequest().getPeers().subList(3, 4)) {
//            peerConnectionList.add(new Thread(() -> {
//                try {
//                    PeerConnection peerConnection = new PeerConnection(peer, metainfo, peerId);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }));
//            peerConnectionList.get(peerConnectionList.size() - 1).start();
//        }
//        Thread.sleep(10000);
    }
}
