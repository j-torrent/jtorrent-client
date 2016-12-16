package org.jtorrent.client.bencode;

public class BInteger implements BObject {
    private final Integer value;

    public BInteger(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
