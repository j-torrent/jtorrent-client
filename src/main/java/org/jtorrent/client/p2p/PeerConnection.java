package org.jtorrent.client.p2p;

import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
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

    private final Peer peer;
    private final Metainfo metainfo;
    private final PeerId myPeerId;

    private final Thread incomingThread;
    private final Thread outgoingThread;

    private final SocketChannel peerSocketChannel;

    private final BlockingQueue<ByteBuffer> outgoingMessages;
    private Consumer<ByteBuffer> headerConsumer = byteBuffer -> {
        LOG.info("I've got " + new String(byteBuffer.array(), StandardCharsets.ISO_8859_1));
    };

    public PeerConnection(Peer peer, Metainfo metainfo, PeerId myPeerId) throws IOException {
        this.peer = peer;
        this.metainfo = metainfo;
        this.myPeerId = myPeerId;

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
        peerSocketChannel.connect(peer.getAddress());

        incomingThread = new Thread(() -> {
            LOG.info("Incoming thread start!");
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(1);
                while (peerSocketChannel.read(byteBuffer) != 1);
                byteBuffer = ByteBuffer.allocate(byteBuffer.get(0) + 8 + 20 + 20);
                int read = 0;
                while (read < byteBuffer.capacity()) {
                    read += peerSocketChannel.read(byteBuffer);
                }
                headerConsumer.accept(byteBuffer);
            } catch (IOException e) {
                LOG.error("Something strange happened", e);
            }
        });

        outgoingThread = new Thread(() -> {
            LOG.info("Outgoing thread start!");
            while (true) {
                ByteBuffer outgoingMessage;
                try {
                    outgoingMessage = outgoingMessages.take();
                } catch (InterruptedException e) {
                    break;
                }
                LOG.info("Sending: " + outgoingMessage);
                try {
                    while (outgoingMessage.hasRemaining()) {
                        int w = peerSocketChannel.write(outgoingMessage);
                        LOG.info("Only " + outgoingMessage + " Mda " + w);
                        if (w == 0) {
                            Thread.sleep(100);
                        }
                    }
                } catch (IOException e) {
                    LOG.error("No internet to write outgoing message", e);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        incomingThread.start();
        outgoingThread.start();
    }

    public void setHeaderConsumer(Consumer<ByteBuffer> headerConsumer) {
        this.headerConsumer = headerConsumer;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        incomingThread.interrupt();
        outgoingThread.interrupt();
        outgoingThread.join();
        incomingThread.join();
        peerSocketChannel.close();
    }
}
