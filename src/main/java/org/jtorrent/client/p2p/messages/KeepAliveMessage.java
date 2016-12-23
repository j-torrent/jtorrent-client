package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class KeepAliveMessage extends PeerMessage {
    private final static byte BASE_SIZE = 0;
    private final static ByteBuffer DATA = ByteBuffer.allocate(MESSAGE_LENGTH_SIZE + BASE_SIZE);
    static {
        DATA.put(BASE_SIZE);
        DATA.rewind();
    }

    public KeepAliveMessage() {
        super(Type.KEEP_ALIVE, DATA);
    }
}
