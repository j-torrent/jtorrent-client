package org.jtorrent.client.bencode;

import java.util.Collections;
import java.util.List;

public class BList implements BObject {
    private final List<BObject> value;

    public BList(List<BObject> value) {
        this.value = Collections.unmodifiableList(value);
    }

    public List<BObject> getValue() {
        return value;
    }
}
