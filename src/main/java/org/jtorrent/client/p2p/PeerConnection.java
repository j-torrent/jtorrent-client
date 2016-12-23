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

    private final Thread incomingThread;
    private final Thread outgoingThread;

    private final SocketChannel peerSocketChannel;

    private final BlockingQueue<ByteBuffer> outgoingMessages;
    private Consumer<ByteBuffer> headerConsumer = byteBuffer -> {
        LOG.info("I've got " + new String(byteBuffer.array(), StandardCharsets.ISO_8859_1));
    };
    private volatile boolean choked = true;
    private volatile boolean interested = false;
    private volatile boolean peerChoked = true;
    private volatile boolean peerInterested = false;
    private Consumer<Void> unchokeConsumer = v -> {
    };
    private Consumer<Integer> haveConsumer = index -> {
    };
    private Consumer<BitSet> bitfieldConsumer = bitSet -> {
    };
    private Consumer<RequestMessage> requestMessageConsumer = requestMessage -> {
    };
    private Consumer<PieceMessage> pieceMessageConsumer = pieceMessage -> {
        LOG.info("I've got a piece: " + Arrays.toString(pieceMessage.getBlock()));
    };
    private Consumer<IOException> exceptionConsumer = e -> {};

    public PeerConnection(Peer peer, Metainfo metainfo, PeerId myPeerId) throws IOException {
        outgoingMessages = new LinkedBlockingDeque<>();
        {
            byte[] bytes = new byte[1 + 19 + 8 + 20 + 20];
            bytes[0] = 19;
            System.arraycopy("BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 1, "BitTorrent protocol".length());
            System.arraycopy(metainfo.getInfoSHA1(), 0, bytes, 28, 20);
            System.arraycopy(myPeerId.getId().getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 48, 20);
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            LOG.info("Handshaking: " + new String(byteBuffer.array(), StandardCharsets.ISO_8859_1));
            outgoingMessages.offer(byteBuffer);
        }
        LOG.info("Connecting to " + peer.getAddress());
        peerSocketChannel = SocketChannel.open();
        peerSocketChannel.socket().connect(peer.getAddress(), 1000);

        incomingThread = new Thread(() -> {
            LOG.info("Incoming thread start!");
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(1);
                while (peerSocketChannel.read(byteBuffer) != 1) ;
                byteBuffer = ByteBuffer.allocate(byteBuffer.get(0) + 8 + 20 + 20);
                int read = 0;
                while (read < byteBuffer.capacity()) {
                    read += peerSocketChannel.read(byteBuffer);
                }
                headerConsumer.accept(byteBuffer);
            } catch (IOException e) {
                exceptionConsumer.accept(e);
            }
            ByteBuffer buffer = ByteBuffer.allocate(100000);
            try {
                while (true) {
                    ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                    lengthBuffer.order(ByteOrder.BIG_ENDIAN);
                    int read = 0;
                    while (read < lengthBuffer.capacity()) {
                        read += peerSocketChannel.read(lengthBuffer);
                    }
                    lengthBuffer.flip();
                    int size = lengthBuffer.getInt();
                    if (size == 0) {
                        continue;
                    }
                    ByteBuffer payloadBuffer = ByteBuffer.allocate(size);
                    read = 0;
                    while (read < payloadBuffer.capacity()) {
                        read += peerSocketChannel.read(payloadBuffer);
                    }
                    byte[] message = payloadBuffer.array();
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
                            ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                            bitfieldConsumer.accept(BitSet.valueOf(byteBuffer));
                            LOG.info("" + byteBuffer.capacity());
                            break;
                        }
                        case 6: {
                            ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                            int pieceIndex = byteBuffer.getInt();
                            int begin = byteBuffer.getInt();
                            int length = byteBuffer.getInt();
                            requestMessageConsumer.accept(new RequestMessage(pieceIndex, begin, length));
                            break;
                        }
                        case 7: {
                            ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(message, 1, message.length));
                            int pieceIndex = byteBuffer.getInt();
                            int begin = byteBuffer.getInt();
                            byte[] block = new byte[byteBuffer.remaining()];
                            byteBuffer.get(block);
                            pieceMessageConsumer.accept(new PieceMessage(pieceIndex, begin, block));
                            break;
                        }
                        default:
                            LOG.warn("Unknown message id: " + message[0]);
                    }
                }
            } catch (IOException e) {
                exceptionConsumer.accept(e);
            }
        });

        incomingThread.setName("incoming-thread-" + peer.getAddress().getHostName());

        outgoingThread = new Thread(() -> {
            LOG.info("Outgoing thread start!");
            while (true) {
                ByteBuffer outgoingMessage;
                try {
                    outgoingMessage = outgoingMessages.take();
                } catch (InterruptedException e) {
                    break;
                }
                try {
                    while (outgoingMessage.hasRemaining()) {
                        int w = peerSocketChannel.write(outgoingMessage);
                    }
                } catch (IOException e) {
                    exceptionConsumer.accept(e);
                }
            }
        });
        outgoingThread.setName("outcoming-thread-" + peer.getAddress().getHostName());

        incomingThread.start();
        outgoingThread.start();
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
        LOG.info("KUDAH " + byteBuffer.capacity());
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
