package org.jtorrent.client;

import org.jtorrent.client.bencode.BObject;
import org.jtorrent.client.bencode.BencodeParser;
import org.jtorrent.client.client.TorrentClient;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.MetainfoParser;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.tracker.TrackerConnection;
import org.jtorrent.client.tracker.TrackerConnectionBuilder;

import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        ClassLoader classLoader = Main.class.getClassLoader();
        BencodeParser bencodeParser = BencodeParser.getInstance();
        BObject bObject = bencodeParser.parse(new FileInputStream(classLoader.getResource("dt.torrent").getFile()));
        Metainfo metainfo = MetainfoParser.getInstance().parse(bObject);
        PeerId myPeerId = PeerId.generateJTorrentRandom();
        TrackerConnection trackerConnection = new TrackerConnectionBuilder()
                .setCompact(false)
                .setDownloaded(0)
                .setUploaded(0)
                .setPort(6881)
                .setMetainfo(metainfo)
                .setMyPeerId(myPeerId)
                .build();
        try (TorrentClient torrentClient = new TorrentClient(metainfo, 5, 5000, trackerConnection, myPeerId)) {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
