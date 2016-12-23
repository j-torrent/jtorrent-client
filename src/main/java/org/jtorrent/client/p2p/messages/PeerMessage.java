package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class PeerMessage {
    public static final int MESSAGE_LENGTH_SIZE = 1;

    private final Type type;
    private final ByteBuffer data;

    public PeerMessage(Type type, ByteBuffer data) {
        this.type = type;
        this.data = data;
    }
}
