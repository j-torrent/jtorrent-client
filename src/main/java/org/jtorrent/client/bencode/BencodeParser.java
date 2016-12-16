package org.jtorrent.client.bencode;

import org.jtorrent.client.util.PeekableAsciiInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BencodeParser {
    private static final BencodeParser INSTANCE = new BencodeParser();

    private BencodeParser() {
    }

    public static BencodeParser getInstance() {
        return INSTANCE;
    }

    public BObject parse(InputStream is) throws IOException {
        PeekableAsciiInputStream asciiInputStream = new PeekableAsciiInputStream(is);
        return parse(asciiInputStream);
    }

    private BObject parse(PeekableAsciiInputStream asciiInputStream) throws IOException {
        char c = (char) asciiInputStream.peek();
        switch (c) {
            case 'd':
                assertThat(asciiInputStream.read() == 'd');
                List<BObject> keys = new ArrayList<>();
                List<BObject> values = new ArrayList<>();
                while (asciiInputStream.peek() != 'e') {
                    BObject key = parse(asciiInputStream);
                    BObject value = parse(asciiInputStream);
                    keys.add(key);
                    values.add(value);
                }
                assertThat(asciiInputStream.read() == 'e');
                return new BDictionary(keys, values);
            case 'l':
                assertThat(asciiInputStream.read() == 'l');
                List<BObject> list = new ArrayList<>();
                while (asciiInputStream.peek() != 'e') {
                    list.add(parse(asciiInputStream));
                }
                assertThat(asciiInputStream.read() == 'e');
                return new BList(list);
            case 'i':
                assertThat(asciiInputStream.read() == 'i');
                BObject result = new BLong(asciiInputStream.readLong());
                assertThat(asciiInputStream.read() == 'e');
                return result;
            default:
                long length = asciiInputStream.readLong();
                assertThat(asciiInputStream.read() == ':');
                return new BString(asciiInputStream.readString((int) length));
        }
    }
}
