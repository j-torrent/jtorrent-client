package org.jtorrent.client.bencode;

import java.util.Collections;
import java.util.Map;

public class BDictionary implements BObject {
    private final Map<BObject, BObject> value;

    public BDictionary(Map<BObject, BObject> value) {
        this.value = Collections.unmodifiableMap(value);
    }

    public Map<BObject, BObject> getValue() {
        return value;
    }
}
