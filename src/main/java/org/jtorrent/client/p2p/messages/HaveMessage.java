package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class HaveMessage extends PeerMessage {
    private final static int BASE_SIZE = 5;
    private final int piece;

    private HaveMessage(ByteBuffer byteBuffer, int piece) {
        super(Type.HAVE, byteBuffer);
        this.piece = piece;
    }

    public int getIndex() {
        return piece;
    }

    public static HaveMessage of(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + HaveMessage.BASE_SIZE);
        buffer.putInt(HaveMessage.BASE_SIZE);
        buffer.put(Type.HAVE.getTypeByte());
        buffer.putInt(pieceIndex);
        return new HaveMessage(buffer, pieceIndex);
    }
}
