package org.jtorrent.client.p2p.messages;

import java.util.Arrays;

public class PieceMessage {
    private final int index;
    private final int begin;
    private final byte[] block;

    public PieceMessage(int index, int begin, byte[] block) {
        this.index = index;
        this.begin = begin;
        this.block = Arrays.copyOf(block, block.length);
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
}
