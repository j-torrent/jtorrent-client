package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class UnchokeMessage extends PeerMessage {
    private final static int BASE_SIZE = 1;
    private final static ByteBuffer DATA = ByteBuffer.allocate(MESSAGE_LENGTH_SIZE + BASE_SIZE);
    static {
        DATA.putInt(BASE_SIZE);
        DATA.put(Type.UNCHOKE.getTypeByte());
        DATA.rewind();
    }

    public UnchokeMessage() {
        super(Type.UNCHOKE, DATA);
    }
}
