package org.jtorrent.client.bencode;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class BDictionary implements BObject {
    private final List<BObject> keys;
    private final List<BObject> values;
    private final Map<BObject, BObject> value;

    public BDictionary(List<BObject> keys, List<BObject> values) {
        this.keys = Collections.unmodifiableList(keys);
        this.values = Collections.unmodifiableList(values);
        if (keys.size() != values.size()) {
            throw new IllegalArgumentException("There should be as many keys as values");
        }
        Map<BObject, BObject> tempMap = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            tempMap.put(keys.get(i), values.get(i));
        }
        this.value = Collections.unmodifiableMap(tempMap);
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
        for (int i = 0; i < keys.size(); i++) {
            list.add(keys.get(i));
            list.add(values.get(i));
        }
        return "d" + StringUtils.join(list, "") + "e";
    }
}
