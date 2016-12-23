package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class InterestedMessage extends PeerMessage {
    private final static int BASE_SIZE = 1;
    private final static ByteBuffer DATA = ByteBuffer.allocate(MESSAGE_LENGTH_SIZE + BASE_SIZE);
    static {
        DATA.putInt(BASE_SIZE);
        DATA.put(Type.INTERESTED.getTypeByte());
        DATA.rewind();
    }

    public InterestedMessage() {
        super(Type.INTERESTED, DATA);
    }
}
