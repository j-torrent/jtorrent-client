package org.jtorrent.client.bencode;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class BDictionary implements BObject {
    private final Map<BObject, BObject> value;

    public BDictionary(Map<BObject, BObject> value) {
        this.value = Collections.unmodifiableMap(value);
    }

    public Map<BObject, BObject> getValue() {
        return value;
    }

    public BObject getOrFailure(BObject key) {
        if (value.containsKey(key)) {
            return value.get(key);
        } else {
            throw new IllegalArgumentException(String.format("Cannot find %s in dictionary", key));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BDictionary that = (BDictionary) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BDictionary(" + value + ')';
    }
}
