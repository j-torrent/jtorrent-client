package org.jtorrent.client.bencode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BList implements BObject {
    private final List<BObject> value;

    public BList(List<BObject> value) {
        this.value = Collections.unmodifiableList(value);
    }

    public List<BObject> getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BList bList = (BList) o;
        return Objects.equals(value, bList.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BList(" + value + ')';
    }
}