package org.jtorrent.client.bencode;

public class BString implements BObject {
    private final String value;

    public BString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
