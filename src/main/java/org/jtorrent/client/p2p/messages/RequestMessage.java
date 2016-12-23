package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

public class RequestMessage extends PeerMessage {
    private static final int BASE_SIZE = 13;

    private final int index;
    private final int begin;
    private final int length;

    private RequestMessage(ByteBuffer buffer, int index, int begin, int length) {
        super(Type.REQUEST, buffer);
        this.index = index;
        this.begin = begin;
        this.length = length;
    }

    public int getLength() {
        return length;
    }

    public int getBegin() {
        return begin;
    }

    public int getIndex() {
        return index;
    }

    public static RequestMessage of(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + BASE_SIZE);
        buffer.putInt(RequestMessage.BASE_SIZE);
        buffer.put(Type.REQUEST.getTypeByte());
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return new RequestMessage(buffer, index, begin, length);
    }
}
