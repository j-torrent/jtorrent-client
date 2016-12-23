package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

public class PieceMessage extends PeerMessage {
    public static final int BASE_SIZE = 9;

    private final int index;
    private final int begin;
    private final byte[] block;

    private PieceMessage(ByteBuffer buffer, int index, int begin, byte[] block) {
        super(Type.PIECE, buffer);
        this.index = index;
        this.begin = begin;
        this.block = block;
    }

    public int getIndex() {
        return index;
    }

    public int getBegin() {
        return begin;
    }

    public byte[] getBlock() {
        return block;
    }

    public static PieceMessage of(int index, int begin, byte[] block) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + BASE_SIZE + block.length);
        buffer.putInt(PieceMessage.BASE_SIZE + block.length);
        buffer.put(Type.PIECE.getTypeByte());
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(block);
        return new PieceMessage(buffer, index, begin, block);
    }
}
