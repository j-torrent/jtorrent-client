package org.jtorrent.client.p2p;

import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.p2p.messages.*;
import org.jtorrent.client.tracker.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
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
    private Consumer<KeepAliveMessage> keepAliveMessageConsumer = keepAliveMessage -> {
    };
    private Consumer<UnchokeMessage> unchokeConsumer = v -> {
    };
    private Consumer<HaveMessage> haveConsumer = index -> {
    };
    private Consumer<BitfieldMessage> bitfieldConsumer = bitSet -> {
    };
    private Consumer<RequestMessage> requestMessageConsumer = requestMessage -> {
    };
    private Consumer<PieceMessage> pieceMessageConsumer = pieceMessage -> {
    };
    private Consumer<CancelMessage> cancelConsumer = cancelMessage -> {
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

        incomingThread = new Thread(new IncomingRunnable());
        incomingThread.setName("incoming-thread-" + peer.getAddress().getHostName());

        outgoingThread = new Thread(new OutgoingRunnable());
        outgoingThread.setName("outgoing-thread-" + peer.getAddress().getHostName());

        incomingThread.start();
        outgoingThread.start();
    }

    public void setHaveConsumer(Consumer<HaveMessage> haveConsumer) {
        this.haveConsumer = haveConsumer;
    }

    public void setBitfieldConsumer(Consumer<BitfieldMessage> bitfieldConsumer) {
        this.bitfieldConsumer = bitfieldConsumer;
    }

    public void setRequestMessageConsumer(Consumer<RequestMessage> requestMessageConsumer) {
        this.requestMessageConsumer = requestMessageConsumer;
    }

    public void setPieceMessageConsumer(Consumer<PieceMessage> pieceMessageConsumer) {
        this.pieceMessageConsumer = pieceMessageConsumer;
    }

    public void setUnchokeConsumer(Consumer<UnchokeMessage> unchokeConsumer) {
        this.unchokeConsumer = unchokeConsumer;
    }

    public void setExceptionConsumer(Consumer<IOException> exceptionConsumer) {
        this.exceptionConsumer = exceptionConsumer;
    }

    public void send(PeerMessage peerMessage) {
        try {
            outgoingMessages.put(peerMessage.getData());
        } catch (InterruptedException ignored) {
            // Ignore, our send queue will only block if it contains
            // MAX_INTEGER messages, in which case we're already in big
            // trouble, and we'd have to be interrupted, too.
        }
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
        private void processMessage(PeerMessage message) {
            if (message instanceof KeepAliveMessage) {
                keepAliveMessageConsumer.accept((KeepAliveMessage) message);
            } else if (message instanceof ChokeMessage) {
                peerChoked = true;
            } else if (message instanceof UnchokeMessage) {
                peerChoked = false;
                unchokeConsumer.accept((UnchokeMessage) message);
            } else if (message instanceof InterestedMessage) {
                peerInterested = true;
            } else if (message instanceof NotInterestedMessage) {
                peerInterested = false;
            } else if (message instanceof HaveMessage) {
                haveConsumer.accept((HaveMessage) message);
            } else if (message instanceof BitfieldMessage) {
                bitfieldConsumer.accept((BitfieldMessage) message);
            } else if (message instanceof RequestMessage) {
                requestMessageConsumer.accept((RequestMessage) message);
            } else if (message instanceof PieceMessage) {
                pieceMessageConsumer.accept((PieceMessage) message);
            } else if (message instanceof CancelMessage) {
                cancelConsumer.accept((CancelMessage) message);
            } else {
                throw new IllegalStateException("Java can't into ADT: there should be no other messages");
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
                    ByteBuffer buffer = ByteBuffer.allocate(4 + size);
                    buffer.putInt(size);
                    if (size != 0) {
                        buffer.put(readPayload(size));
                    }
                    buffer.rewind();
                    PeerMessage message = MessageParser.getInstance().parse(buffer);
                    processMessage(message);
                }
            } catch (IOException e) {
                exceptionConsumer.accept(e);
            }
        }
    }
}
