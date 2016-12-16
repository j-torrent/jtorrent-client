package org.jtorrent.client.bencode;

import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.MetainfoParser;
import org.jtorrent.client.tracker.TrackerConnection;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class BencodeParserSpec {
    public static void main(String[] args) throws IOException {
        ClassLoader classLoader = BencodeParserSpec.class.getClassLoader();
        BencodeParser bencodeParser = BencodeParser.getInstance();
        BObject bObject = bencodeParser.parse(new FileInputStream(classLoader.getResource("ubuntu-16.04.1.torrent").getFile()));
        Metainfo metainfo = MetainfoParser.getInstance().parse(bObject);
        TrackerConnection trackerConnection = new TrackerConnection(metainfo);
        trackerConnection.requestPeers();
    }
}
