package org.jtorrent.client.bencode;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class BencodeParserTest {
    @Test
    public void trueTest() throws Exception {
        assertEquals(BencodeParser.getInstance().parse(IOUtils.toInputStream("d1:ai2ee", StandardCharsets.ISO_8859_1)),
                new BDictionary(Collections.singletonList(new BString("a")), Collections.singletonList(new BLong(2L))));
    }
}