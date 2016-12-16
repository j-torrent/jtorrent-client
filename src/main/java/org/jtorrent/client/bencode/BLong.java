package org.jtorrent.client.bencode;

import java.util.Objects;

public class BLong implements BObject {
    private final Long value;

    public BLong(Long value) {
        this.value = value;
    }

    public Long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BLong bLong = (BLong) o;
        return Objects.equals(value, bLong.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BLong(" + value + ')';
    }
}
