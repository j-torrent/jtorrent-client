package org.jtorrent.client.tracker;

import org.jtorrent.client.bencode.BDictionary;
import org.jtorrent.client.bencode.BLong;
import org.jtorrent.client.bencode.BObject;
import org.jtorrent.client.bencode.BString;
import org.jtorrent.client.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TrackerAnswerParser {
    private final static TrackerAnswerParser INSTANCE = new TrackerAnswerParser();
    private final static BString INTERVAL = new BString("interval");
    private final static BString PEERS = new BString("peers");

    private TrackerAnswerParser() {
    }

    public TrackerAnswer parse(BObject bObject) {
        BDictionary trackerAnswerDict = BDictionary.castOrFailure(bObject);
        long interval = BLong.castOrFailure(trackerAnswerDict.getOrFailure(INTERVAL)).getValue();
        String peersString = BString.castOrFailure(trackerAnswerDict.getOrFailure(PEERS)).getValue();
        byte[] peerBytes = peersString.getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> ipWithPorts = Utils.splitBy(peerBytes, 6);
        List<Peer> peers = new ArrayList<>();
        for (byte[] bytes : ipWithPorts) {
            byte[] ipBytes = Arrays.copyOfRange(bytes, 0, 4);
            int port = Byte.toUnsignedInt(bytes[4]) * 255 + Byte.toUnsignedInt(bytes[5]);
            try {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
                peers.add(new Peer(Optional.empty(), address));
            } catch (UnknownHostException e) {
                throw new IllegalStateException(e);
            }
        }
        return new TrackerAnswer(peers, interval);
    }

    public static TrackerAnswerParser getInstance() {
        return INSTANCE;
    }
}
