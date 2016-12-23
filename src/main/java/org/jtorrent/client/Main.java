package org.jtorrent.client;

import org.jtorrent.client.bencode.BObject;
import org.jtorrent.client.bencode.BencodeParser;
import org.jtorrent.client.client.TorrentClient;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.MetainfoParser;

import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        ClassLoader classLoader = Main.class.getClassLoader();
        BencodeParser bencodeParser = BencodeParser.getInstance();
        BObject bObject = bencodeParser.parse(new FileInputStream(classLoader.getResource("dt.torrent").getFile()));
        Metainfo metainfo = MetainfoParser.getInstance().parse(bObject);
        try (TorrentClient torrentClient = new TorrentClient(metainfo, 5, 5000)) {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
