package org.jtorrent.client.bencode;

import org.apache.commons.lang3.StringUtils;
import org.jtorrent.client.util.SHA1Digester;

import java.nio.charset.StandardCharsets;
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

    public static BList castOrFailure(BObject object) {
        if (object instanceof BList) {
            return (BList) object;
        } else {
            throw new IllegalArgumentException("Cannot cast to BList");
        }
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
        return "l" + StringUtils.join(value, "") + "e";
    }

    @Override
    public byte[] calculateSHA1() {
        return SHA1Digester.getInstance().digest(toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
