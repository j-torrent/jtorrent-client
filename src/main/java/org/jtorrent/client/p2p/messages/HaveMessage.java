package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class HaveMessage extends PeerMessage {
    private final static byte BASE_SIZE = 5;
    private final int piece;

    private HaveMessage(ByteBuffer byteBuffer, int piece) {
        super(Type.HAVE, byteBuffer);
        this.piece = piece;
    }

    public int getPiece() {
        return piece;
    }

    public static HaveMessage of(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + HaveMessage.BASE_SIZE);
        buffer.put(HaveMessage.BASE_SIZE);
        buffer.put(Type.HAVE.getTypeByte());
        buffer.putInt(pieceIndex);
        return new HaveMessage(buffer, pieceIndex);
    }
}
