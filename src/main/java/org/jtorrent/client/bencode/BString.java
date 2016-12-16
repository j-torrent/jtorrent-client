package org.jtorrent.client.bencode;

import org.jtorrent.client.util.SHA1Digester;

import java.security.MessageDigest;
import java.util.Objects;

public class BString implements BObject {
    private final String value;


    public BString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BString castOrFailure(BObject object) {
        if (object instanceof BString) {
            return (BString) object;
        } else {
            throw new IllegalArgumentException("Cannot cast to BString");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BString bString = (BString) o;
        return Objects.equals(value, bString.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.length() + ":" + value;
    }
}
