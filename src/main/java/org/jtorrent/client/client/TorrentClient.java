package org.jtorrent.client.client;

import com.google.common.io.Files;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.metainfo.TorrentFileInfo;
import org.jtorrent.client.p2p.PeerConnection;
import org.jtorrent.client.p2p.messages.PieceMessage;
import org.jtorrent.client.tracker.Peer;
import org.jtorrent.client.tracker.TrackerAnswer;
import org.jtorrent.client.tracker.TrackerConnection;
import org.jtorrent.client.util.SHA1Digester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TorrentClient.class);
    private static final int CHUNK_SIZE = 1 << 14;
    private final Metainfo metainfo;
    private final BlockingQueue<Peer> unmanagedPeers;
    private final BlockingQueue<PieceMessage> pieceMessageBlockingQueue;
    private final AtomicInteger[] pieceStatuses;
    private final PeerId myPeerId;
    private final Thread trackerFetcher;
    private final List<Thread> peerThreads;
    private final Thread chunkAggregator;
    private final ConcurrentSkipListSet<Integer> set = new ConcurrentSkipListSet<>();

    public TorrentClient(Metainfo metainfo) {
        this.metainfo = metainfo;

        unmanagedPeers = new LinkedBlockingQueue<>();
        pieceMessageBlockingQueue = new LinkedBlockingDeque<>();
        pieceStatuses = new AtomicInteger[metainfo.getPieces().size()];
        Arrays.setAll(pieceStatuses, i -> new AtomicInteger(0));
        myPeerId = PeerId.generateJTorrentRandom();
        trackerFetcher = new Thread(new TrackerFetcher());
        trackerFetcher.start();
        chunkAggregator = new Thread(new ChunkAggregator());
        chunkAggregator.start();

        peerThreads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread peerThread = new Thread(new PeerWorker());
            peerThread.setName("peer-thread-" + i);
            peerThreads.add(peerThread);
            peerThread.start();
        }
    }

    @Override
    public void close() throws InterruptedException {
        LOG.info("Closing");
        trackerFetcher.join();
        for (Thread thread : peerThreads) {
            thread.join();
        }
    }

    private class ChunkAggregator implements Runnable {
        private final Map<Integer, Integer> pieceBytesWritten = new HashMap<>();
        private final Map<Integer, byte[]> pieceBytes = new HashMap<>();
        private int piecesDownloaded = 0;

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                PieceMessage pieceMessage;
                try {
                    pieceMessage = pieceMessageBlockingQueue.take();
                } catch (InterruptedException e) {
                    break;
                }
                int allLength = pieceBytesWritten.compute(pieceMessage.getIndex(), (k, v) -> (v == null ? 0 : v) + pieceMessage.getBlock().length);
                pieceBytes.compute(pieceMessage.getIndex(), (k, v) -> {
                    if (v == null) {
                        v = new byte[(int) metainfo.getPieceLength()];
                    }
                    System.arraycopy(pieceMessage.getBlock(), 0, v, pieceMessage.getBegin(), pieceMessage.getBlock().length);
                    return v;
                });
                if (allLength == metainfo.getPieceLength()) {
                    pieceBytesWritten.remove(pieceMessage.getIndex());
                    piecesDownloaded++;
                    LOG.info("Piece " + pieceMessage.getIndex() + " is downloaded");
                    LOG.info("Already " + piecesDownloaded + "/" + metainfo.getPieces().size() + " are done");
                    LOG.info("I can download " + set.size());
                    byte[] pieceSha1 = metainfo.getPieces().get(pieceMessage.getIndex()).getBytes(StandardCharsets.ISO_8859_1);
                    byte[] pieceIndBytes = pieceBytes.remove(pieceMessage.getIndex());
                    byte[] pieceRealSha1 = SHA1Digester.getInstance().digest(new String(pieceIndBytes, StandardCharsets.ISO_8859_1));
                    if (Arrays.equals(pieceSha1, pieceRealSha1)) {
                        long begin = pieceMessage.getIndex() * metainfo.getPieceLength();
                        long end = begin + metainfo.getPieceLength();
                        long current = 0;
                        long currentI = 0;
                        for (int i = 0; i < metainfo.getFiles().size(); i++) {
                            TorrentFileInfo torrentFile = metainfo.getFiles().get(i);
                            if (current + torrentFile.getLengthInBytes() >= begin) {
                                for (int j = i; j < metainfo.getFiles().size(); j++) {
                                    TorrentFileInfo oTorrentFile = metainfo.getFiles().get(j);
                                    if (end < current + oTorrentFile.getLengthInBytes()) {
                                        long written = 0;
                                        for (int k = i; k <= j; k++) {
                                            TorrentFileInfo tf = metainfo.getFiles().get(k);
                                            long start = k == i ? begin - currentI : 0;
                                            long finish = k == j ? end - written : tf.getLengthInBytes();
                                            try {
                                                File file = new File(tf.getPath());
                                                Files.createParentDirs(file);
                                                Files.touch(file);
                                                FileOutputStream fos = new FileOutputStream(file);
                                                FileChannel ch = fos.getChannel();
                                                ch.position(start);
                                                ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOfRange(pieceIndBytes, (int) written, (int)(written + finish - start)));
                                                while (byteBuffer.hasRemaining()) {
                                                    ch.write(byteBuffer);
                                                }
                                                ch.close();
                                                fos.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            written += finish - start;
                                            currentI += tf.getLengthInBytes();
                                        }
                                        break;
                                    } else {
                                        current += oTorrentFile.getLengthInBytes();
                                    }
                                }
                                break;
                            } else {
                                currentI += torrentFile.getLengthInBytes();
                                current += torrentFile.getLengthInBytes();
                            }
                        }
                    } else {
                        LOG.info("Mda sha1 ne sovpal");
                    }
                }
            }
        }
    }

    private class PeerWorker implements Runnable {
        private ConcurrentSkipListSet<Integer> havePieces = new ConcurrentSkipListSet<>();
        private final Object lock = new Object();
        private volatile boolean isDownloading = false;

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                Peer peer;
                try {
                    peer = unmanagedPeers.take();
                } catch (InterruptedException e) {
                    return;
                }
                try (PeerConnection peerConnection = new PeerConnection(peer, metainfo, myPeerId)) {
                    final boolean[] isBad = {false};
                    isDownloading = false;
                    havePieces.clear();
                    peerConnection.setPieceMessageConsumer(pieceMessage -> {
                        try {
                            pieceMessageBlockingQueue.put(pieceMessage);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        isDownloading = false;
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    });
                    peerConnection.setBitfieldConsumer(bitSet -> {
                        LOG.info("I have bitset");
                        for (int i = bitSet.nextSetBit(0); i != -1 && i < metainfo.getPieces().size(); i = bitSet.nextSetBit(i + 1)) {
                            havePieces.add(i);
                            set.add(i);
                        }
                    });
                    peerConnection.setHaveConsumer(index -> {
                        LOG.info("I HAVE " + index);
                        havePieces.add(index);
                        set.add(index);
                        synchronized (lock) {
                            if (!isDownloading) {
                                lock.notifyAll();
                            }
                        }
                    });
                    peerConnection.setUnchokeConsumer(v -> {
                        LOG.info("I'm unchoked");
                        synchronized (lock) {
                            if (!isDownloading) {
                                lock.notifyAll();
                            }
                        }
                    });
                    peerConnection.setExceptionConsumer(e -> {
                        isBad[0] = true;
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    });
                    BitSet bitSet = new BitSet(pieceStatuses.length);
                    for (int i = 0; i < pieceStatuses.length; i++) {
                        if (pieceStatuses[i].get() >= metainfo.getPieceLength()) {
                            bitSet.set(i);
                        }
                    }
                    peerConnection.sendBitfield(bitSet);
                    peerConnection.sendInterested();
                    LOG.info("Sending interested");
                    while (true) {
                        synchronized (lock) {
                            lock.wait(10000);
                        }
                        if (isBad[0]) {
                            break;
                        }
                        while (!havePieces.isEmpty()) {
                            int index = havePieces.first();
                            int x = pieceStatuses[index].get();
                            if (x >= metainfo.getPieceLength()) {
                                havePieces.remove(index);
                                continue;
                            }
                            if (pieceStatuses[index].compareAndSet(x, x + CHUNK_SIZE)) {
                                isDownloading = true;
                                peerConnection.sendRequest(index, x, Math.min(CHUNK_SIZE, (int) metainfo.getPieceLength() - x));
                                break;
                            }
                        }
                        if (havePieces.isEmpty()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    LOG.info("Wow much interrupted");
                    return;
                } catch (IOException e) {
                    LOG.info("MDA", e);
//                    try {
//                        unmanagedPeers.put(peer);
//                    } catch (InterruptedException e1) {
//                        return;
//                    }
                }
            }
        }
    }

    private class TrackerFetcher implements Runnable {
        private TrackerConnection trackerConnection = new TrackerConnection(metainfo, myPeerId);
        private Set<Peer> peersSet = new HashSet<>();

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    LOG.info("Making request for tracker info...");
                    TrackerAnswer trackerAnswer = trackerConnection.makeInitialRequest();
                    LOG.info("Request successful");
                    int size = peersSet.size();
                    for (Peer peer : trackerAnswer.getPeers()) {
                        if (peersSet.add(peer)) {
                            unmanagedPeers.put(peer);
                        }
                    }
                    LOG.info("Added " + (peersSet.size() - size) + " new peers");
                    Thread.sleep(trackerAnswer.getInterval() * 10);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
