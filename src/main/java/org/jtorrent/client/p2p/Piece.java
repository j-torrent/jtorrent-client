package org.jtorrent.client.p2p;

import org.jtorrent.client.tracker.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

/**
 * @author Daniyar Itegulov
 */
public class Piece implements Comparable<Piece> {

    private static final Logger logger = LoggerFactory.getLogger(Piece.class);
    private final int index;
    private final long offset;
    private final long length;
    private final byte[] hash;
    private final boolean seeder;

    private volatile boolean valid;
    private int seen;
    private ByteBuffer data;

    public Piece(int index, long offset,
                 long length, byte[] hash, boolean seeder) {
        this.index = index;
        this.offset = offset;
        this.length = length;
        this.hash = hash;
        this.seeder = seeder;

        // Piece is considered invalid until first check.
        this.valid = false;

        // Piece start unseen
        this.seen = 0;

        this.data = null;
    }

    public boolean isValid() {
        return this.valid;
    }

    public int getIndex() {
        return this.index;
    }

    public long size() {
        return this.length;
    }

    public boolean available() {
        return this.seen > 0;
    }

    public void seenAt(Peer peer) {
        this.seen++;
    }

    public void noLongerAt(Peer peer) {
        this.seen--;
    }

    public synchronized boolean validate() throws IOException {
        if (this.seeder) {
            logger.trace("Skipping validation of {} (seeder mode).", this);
            this.valid = true;
            return true;
        }

        logger.trace("Validating {}...", this);
        this.valid = false;

        ByteBuffer buffer = this._read(0, this.length);
        byte[] data = new byte[(int)this.length];
        buffer.get(data);

        return this.isValid();
    }

    private ByteBuffer _read(long offset, long length) throws IOException {
        if (offset + length > this.length) {
            throw new IllegalArgumentException("Piece#" + this.index +
                    " overrun (" + offset + " + " + length + " > " +
                    this.length + ") !");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int)length);
        buffer.rewind();
        return buffer;
    }

    public ByteBuffer read(long offset, int length)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (!this.valid) {
            throw new IllegalStateException("Attempting to read an " +
                    "known-to-be invalid piece!");
        }

        return this._read(offset, length);
    }

    public synchronized void record(ByteBuffer block, int offset)
            throws IOException {
        if (this.data == null || offset == 0) {
            this.data = ByteBuffer.allocate((int)this.length);
        }

        int pos = block.position();
        this.data.position(offset);
        this.data.put(block);
        block.position(pos);

        if (block.remaining() + offset == this.length) {
            this.data.rewind();
            logger.trace("Recording {}...", this);
            this.data = null;
        }
    }

    public String toString() {
        return String.format("piece#%4d%s",
                this.index,
                this.isValid() ? "+" : "-");
    }

    public int compareTo(@Nonnull Piece other) {
        if (this.seen != other.seen) {
            return this.seen < other.seen ? -1 : 1;
        }
        return this.index == other.index ? 0 :
                (this.index < other.index ? -1 : 1);
    }

    public static class CallableHasher implements Callable<Piece> {

        private final Piece piece;

        public CallableHasher(Piece piece) {
            this.piece = piece;
        }

        @Override
        public Piece call() throws IOException {
            this.piece.validate();
            return this.piece;
        }
    }
}
