package org.jtorrent.client.bencode;

import java.io.IOException;
import java.io.InputStream;

public class BencodeParser {
    private static final BencodeParser INSTANCE = new BencodeParser();

    private BencodeParser() {
    }

    public static BencodeParser getInstance() {
        return INSTANCE;
    }

    public BObject parse(InputStream is) throws IOException {
        return null;
    }
}
