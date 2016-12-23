package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class ChokeMessage extends PeerMessage {
    private final static byte BASE_SIZE = 1;
    private final static ByteBuffer DATA = ByteBuffer.allocate(MESSAGE_LENGTH_SIZE + BASE_SIZE);
    static {
        DATA.put(BASE_SIZE);
        DATA.put(Type.CHOKE.getTypeByte());
        DATA.rewind();
    }

    public ChokeMessage() {
        super(Type.CHOKE, DATA);
    }
}
