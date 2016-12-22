package org.jtorrent.client.p2p.messages;

public class RequestMessage {
    private final int index;
    private final int begin;
    private final int length;

    public RequestMessage(int index, int begin, int length) {
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
}
