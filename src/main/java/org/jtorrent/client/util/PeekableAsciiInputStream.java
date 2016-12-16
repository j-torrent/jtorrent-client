package org.jtorrent.client.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class PeekableAsciiInputStream extends InputStream {
    private final InputStream is;
    private Optional<Integer> peekedChar;

    public PeekableAsciiInputStream(InputStream is) {
        this.is = is;
        this.peekedChar = Optional.empty();
    }

    @Override
    public int read() throws IOException {
        if (peekedChar.isPresent()) {
            int result = peekedChar.get();
            peekedChar = Optional.empty();
            return result;
        } else {
            return is.read();
        }
    }

    public int peek() throws IOException {
        if (peekedChar.isPresent()) {
            return peekedChar.get();
        } else {
            int readed = is.read();
            if (readed != -1) {
                peekedChar = Optional.of(readed);
                return readed;
            } else {
                peekedChar = Optional.empty();
                return -1;
            }
        }
    }

    public long readLong() throws IOException {
        int readed;
        long result = 0;
        // TODO: support negative numbers
        while ((readed = peek()) != -1 && Character.isDigit(readed)) {
            assertThat(read() == readed);
            result = result * 10 + (readed - '0');
        }
        return result;
    }

    public String readString(int length) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        for (long i = 0; i < length; i++) {
            int readed = read();
            if (readed == -1) {
                throw new EOFException("Unexpected end of file");
            } else {
                byteBuffer.put((byte) readed);
            }
        }
        return new String(byteBuffer.array(), StandardCharsets.ISO_8859_1);
    }
}
