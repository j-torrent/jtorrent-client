package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class CancelMessage extends PeerMessage {
    private static final int BASE_SIZE = 13;

    private int piece;
    private int offset;
    private int length;

    private CancelMessage(ByteBuffer buffer, int piece, int offset, int length) {
        super(Type.CANCEL, buffer);
        this.piece = piece;
        this.offset = offset;
        this.length = length;
    }

    public int getPiece() {
        return this.piece;
    }

    public int getOffset() {
        return this.offset;
    }

    public int getLength() {
        return this.length;
    }

    public static CancelMessage craft(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + BASE_SIZE);
        buffer.putInt(CancelMessage.BASE_SIZE);
        buffer.put(Type.CANCEL.getTypeByte());
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return new CancelMessage(buffer, index, begin, length);
    }
}
