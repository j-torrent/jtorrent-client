package org.jtorrent.client.bencode;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

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

    public static BDictionary castOrFailure(BObject object) {
        if (object instanceof BDictionary) {
            return (BDictionary) object;
        } else {
            throw new IllegalArgumentException("Cannot cast to BDictionary");
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
        List<BObject> list = new ArrayList<>();
        for (Map.Entry<BObject, BObject> entry: value.entrySet()) {
            list.add(entry.getKey());
            list.add(entry.getValue());
        }
        return "d" + StringUtils.join(list, "") + "e";
    }
}
