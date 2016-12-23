package org.jtorrent.client.p2p;

import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.p2p.messages.PieceMessage;
import org.jtorrent.client.p2p.messages.RequestMessage;
import org.jtorrent.client.tracker.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public class PeerConnection implements AutoCloseable {
    private final static Logger LOG = LoggerFactory.getLogger(PeerConnection.class);

    private static final int EXTRA_BYTES_SIZE = 8;
    private static final int INFO_HASH_SIZE = 20;
    private static final int PEER_ID_SIZE = 20;
    private static final String BIT_TORRENT_PROTOCOL = "BitTorrent protocol";

    private final Thread incomingThread;
    private final Thread outgoingThread;

    private final SocketChannel peerSocketChannel;
    private final BlockingQueue<ByteBuffer> outgoingMessages;

    private volatile boolean choked = true;
    private volatile boolean interested = false;
    private volatile boolean peerChoked = true;
    private volatile boolean peerInterested = false;
    private Consumer<ByteBuffer> headerConsumer = byteBuffer -> {
    };
    private Consumer<Void> unchokeConsumer = v -> {
    };
    private Consumer<Integer> haveConsumer = index -> {
    };
    private Consumer<BitSet> bitfieldConsumer = bitSet -> {
    };
    private Consumer<RequestMessage> requestMessageConsumer = requestMessage -> {
    };
    private Consumer<PieceMessage> pieceMessageConsumer = pieceMessage -> {
    };
    private Consumer<IOException> exceptionConsumer = e -> {
    };

    public PeerConnection(Peer peer, Metainfo metainfo, PeerId myPeerId, int connectionTimeout) throws IOException {
        outgoingMessages = new LinkedBlockingDeque<>();
        {
            byte[] bytes = new byte[1 + BIT_TORRENT_PROTOCOL.length() +
                    EXTRA_BYTES_SIZE + INFO_HASH_SIZE + PEER_ID_SIZE];
            bytes[0] = (byte) BIT_TORRENT_PROTOCOL.length();
            byte[] bitTorrentBytes = BIT_TORRENT_PROTOCOL.getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(bitTorrentBytes, 0, bytes, 1, bitTorrentBytes.length);
            // Extra bytes writing is omitted because they are zeros in general
            System.arraycopy(
                    metainfo.getInfoSHA1(),
                    0,
                    bytes,
                    1 + BIT_TORRENT_PROTOCOL.length() + EXTRA_BYTES_SIZE,
                    INFO_HASH_SIZE
            );
            byte[] peerIdBytes = myPeerId.getId().getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(
                    peerIdBytes,
                    0,
                    bytes,
                    1 + BIT_TORRENT_PROTOCOL.length() + EXTRA_BYTES_SIZE + INFO_HASH_SIZE,
                    PEER_ID_SIZE
            );
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            LOG.debug("Handshaking: " + new String(byteBuffer.array(), StandardCharsets.ISO_8859_1));
            try {
                outgoingMessages.put(byteBuffer);
            } catch (InterruptedException ignored) {
                // This can't happen in general course of work
            }
        }
        LOG.debug("Connecting to " + peer.getAddress());
        peerSocketChannel = SocketChannel.open();
        peerSocketChannel.socket().connect(peer.getAddress(), connectionTimeout);

        incomingThread = new Thread(new IncomingRunnable(metainfo));
        incomingThread.setName("incoming-thread-" + peer.getAddress().getHostName());

        outgoingThread = new Thread(new OutgoingRunnable());
        outgoingThread.setName("outgoing-thread-" + peer.getAddress().getHostName());

        incomingThread.start();
        outgoingThread.start();
    }

    private class OutgoingRunnable implements Runnable {
        @Override
        public void run() {
            LOG.info("Outgoing thread start!");
            while (!Thread.interrupted()) {
                ByteBuffer outgoingMessage;
                try {
                    outgoingMessage = outgoingMessages.take();
                } catch (InterruptedException e) {
                    break;
                }
                try {
                    while (outgoingMessage.hasRemaining()) {
                        peerSocketChannel.write(outgoingMessage);
                    }
                } catch (IOException e) {
                    exceptionConsumer.accept(e);
                }
            }
        }
    }

    private class IncomingRunnable implements Runnable {
        private final Metainfo metainfo;

        private IncomingRunnable(Metainfo metainfo) {
            this.metainfo = metainfo;
        }

        private void processMessage(byte[] message) {
            switch (message[0]) {
                case 0:
                    peerChoked = true;
                    break;
                case 1:
                    peerChoked = false;
                    unchokeConsumer.accept(null);
                    break;
                case 2:
                    peerInterested = true;
                    break;
                case 3:
                    peerInterested = false;
                    break;
                case 4: {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                    int pieceIndex = byteBuffer.getInt();
                    haveConsumer.accept(pieceIndex);
                    break;
                }
                case 5: {
                    BitSet bitSet = new BitSet(metainfo.getPieces().size());
                    for (int i = 1; i < message.length; i++) {
                        for (int j = 0; j < 8; j++) {
                            if (((message[i] >> j) & 1) == 1) {
                                bitSet.set((i - 1) * 8 + (7 - j));
                            }
                        }
                    }
                    bitfieldConsumer.accept(bitSet);
                    break;
                }
                case 6: {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                    int pieceIndex = byteBuffer.getInt();
                    int begin = byteBuffer.getInt();
                    int length = byteBuffer.getInt();
                    requestMessageConsumer.accept(RequestMessage.of(pieceIndex, begin, length));
                    break;
                }
                case 7: {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                    int pieceIndex = byteBuffer.getInt();
                    int begin = byteBuffer.getInt();
                    byte[] block = new byte[byteBuffer.remaining()];
                    byteBuffer.get(block);
                    pieceMessageConsumer.accept(PieceMessage.of(pieceIndex, begin, block));
                    break;
                }
                default:
                    LOG.warn("Unknown message id: " + message[0]);
            }
        }

        private ByteBuffer readAll(SocketChannel channel, int size) throws IOException {
            ByteBuffer dst = ByteBuffer.allocate(size);
            int read = 0;
            while (read < dst.capacity()) {
                read += channel.read(dst);
            }
            return dst;
        }

        private int readLength() throws IOException {
            ByteBuffer lengthBuffer = readAll(peerSocketChannel, 4);
            lengthBuffer.flip();
            return lengthBuffer.getInt();
        }

        private byte[] readPayload(int size) throws IOException {
            ByteBuffer payloadBuffer = readAll(peerSocketChannel, size);
            return payloadBuffer.array();
        }

        private ByteBuffer readHeader() throws IOException {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1);
            while (peerSocketChannel.read(byteBuffer) != 1) ;
            return readAll(peerSocketChannel, byteBuffer.get(0) +
                    EXTRA_BYTES_SIZE + INFO_HASH_SIZE + PEER_ID_SIZE);
        }

        @Override
        public void run() {
            LOG.info("Incoming thread started");
            try {
                // Reading header
                ByteBuffer byteBuffer = readHeader();
                headerConsumer.accept(byteBuffer);
                // Continually read messages
                while (!Thread.interrupted()) {
                    int size = readLength();
                    if (size == 0) {
                        continue; // Just keep-alive message
                    }
                    byte[] message = readPayload(size);
                    processMessage(message);
                }
            } catch (IOException e) {
                exceptionConsumer.accept(e);
            }
        }
    }

    public void setHaveConsumer(Consumer<Integer> haveConsumer) {
        this.haveConsumer = haveConsumer;
    }

    public void setBitfieldConsumer(Consumer<BitSet> bitfieldConsumer) {
        this.bitfieldConsumer = bitfieldConsumer;
    }

    public void setRequestMessageConsumer(Consumer<RequestMessage> requestMessageConsumer) {
        this.requestMessageConsumer = requestMessageConsumer;
    }

    public void setPieceMessageConsumer(Consumer<PieceMessage> pieceMessageConsumer) {
        this.pieceMessageConsumer = pieceMessageConsumer;
    }

    public void setUnchokeConsumer(Consumer<Void> unchokeConsumer) {
        this.unchokeConsumer = unchokeConsumer;
    }

    public void setExceptionConsumer(Consumer<IOException> exceptionConsumer) {
        this.exceptionConsumer = exceptionConsumer;
    }

    private void send(byte messageId, ByteBuffer payloadBuffer) throws IOException {
        byte[] payload = new byte[payloadBuffer.remaining()];
        payloadBuffer.get(payload);
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 1 + payload.length);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.putInt(1 + payload.length);
        byteBuffer.put(messageId);
        byteBuffer.put(payload);
        outgoingMessages.offer(ByteBuffer.wrap(byteBuffer.array()));
    }

    public void sendChoke() throws IOException {
        choked = false;
        send((byte) 0, ByteBuffer.allocate(0));
    }

    public void sendUnchoke() throws IOException {
        choked = true;
        send((byte) 1, ByteBuffer.allocate(0));
    }

    public void sendInterested() throws IOException {
        interested = true;
        send((byte) 2, ByteBuffer.allocate(0));
    }

    public void sendNotInterested() throws IOException {
        interested = false;
        send((byte) 3, ByteBuffer.allocate(0));
    }

    public void sendHave(int pieceIndex) throws IOException {
        send((byte) 4, buildByteBuffer(pieceIndex));
    }

    public void sendBitfield(BitSet bitSet) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitSet.size() / 8);
        byteBuffer.put(bitSet.toByteArray());
//        send((byte) 5, byteBuffer);
    }

    public void sendRequest(int index, int begin, int length) throws IOException {
        send((byte) 6, buildByteBuffer(index, begin, length));
    }

    public void sendPiece(int index, int begin, byte[] block) {
        // TODO: implement
        throw new UnsupportedOperationException();
//        send((byte) 7, buildByteBuffer(index, begin, block));
    }

    public void sendCancel(int index, int begin, int length) throws IOException {
        send((byte) 8, buildByteBuffer(index, begin, length));
    }

    public void sendPort(int port) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        byteBuffer.putShort((short) port);
        send((byte) 9, buildByteBuffer(port));
    }

    private static ByteBuffer buildByteBuffer(Integer... ints) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 * ints.length);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        for (Integer i : ints) {
            byteBuffer.putInt(i);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    public void setHeaderConsumer(Consumer<ByteBuffer> headerConsumer) {
        this.headerConsumer = headerConsumer;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        LOG.info("Closing");
        incomingThread.interrupt();
        outgoingThread.interrupt();
        outgoingThread.join();
        incomingThread.join();
        peerSocketChannel.close();
    }
}
