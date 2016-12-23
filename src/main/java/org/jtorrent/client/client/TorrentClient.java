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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
        for (int i = 0; i < 5; i++) {
            Thread peerThread = new Thread(new PeerWorker());
            peerThread.setName("peer-thread-" + i);
            peerThreads.add(peerThread);
            peerThread.start();
        }
    }

    @Override
    public void close() throws InterruptedException {
        LOG.info("Closing");
        trackerFetcher.interrupt();
        chunkAggregator.interrupt();
        for (Thread thread : peerThreads) {
            thread.interrupt();
        }
        trackerFetcher.join();
        chunkAggregator.join();
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
                        v = new byte[metainfo.getPieceLength(pieceMessage.getIndex())];
                    }
                    System.arraycopy(pieceMessage.getBlock(), 0, v, pieceMessage.getBegin(), pieceMessage.getBlock().length);
                    return v;
                });
                if (allLength == metainfo.getPieceLength(pieceMessage.getIndex())) {
                    pieceBytesWritten.remove(pieceMessage.getIndex());
                    byte[] pieceSha1 = metainfo.getPieces().get(pieceMessage.getIndex()).getBytes(StandardCharsets.ISO_8859_1);
                    byte[] pieceIndBytes = pieceBytes.remove(pieceMessage.getIndex());
                    byte[] pieceRealSha1 = SHA1Digester.getInstance().digest(new String(pieceIndBytes, StandardCharsets.ISO_8859_1));
                    if (Arrays.equals(pieceSha1, pieceRealSha1)) {
                        piecesDownloaded++;
                        LOG.info("Piece " + pieceMessage.getIndex() + " is downloaded");
                        LOG.info("Already " + piecesDownloaded + "/" + metainfo.getPieces().size() + " are done");
                        LOG.info("I can download " + set.size());
                        long begin = pieceMessage.getIndex() * metainfo.getPieceLength();
                        long end = begin + metainfo.getPieceLength(pieceMessage.getIndex());
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
                                            File file = new File(tf.getPath());
                                            try {
                                                Files.createParentDirs(file);
                                            } catch (IOException e) {
                                                LOG.error("2", e);
                                            }
                                            try {
                                                RandomAccessFile raf = new RandomAccessFile(file.getName(), "rw");
                                                raf.setLength(tf.getLengthInBytes());
                                                byte[] toWrite = Arrays.copyOfRange(pieceIndBytes, (int) written, (int)(written + finish - start));
                                                raf.seek(begin);
                                                raf.write(toWrite);
                                            } catch (IOException e) {
                                                LOG.error("1", e);
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
                        pieceStatuses[pieceMessage.getIndex()].set(0);
                        LOG.info("Mda sha1 ne sovpal");
                    }
                }
            }
        }
    }

    private class PeerWorker implements Runnable {
        private final ConcurrentSkipListSet<Integer> havePieces = new ConcurrentSkipListSet<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);
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
                    latch = new CountDownLatch(1);
                    peerConnection.setPieceMessageConsumer(pieceMessage -> {
                        try {
                            pieceMessageBlockingQueue.put(pieceMessage);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        isDownloading = false;
                        latch.countDown();
                    });
                    peerConnection.setBitfieldConsumer(bitSet -> {
                        for (int i = bitSet.nextSetBit(0); i != -1 && i < metainfo.getPieces().size(); i = bitSet.nextSetBit(i + 1)) {
                            havePieces.add(i);
                            set.add(i);
                        }
                    });
                    peerConnection.setHaveConsumer(index -> {
                        havePieces.add(index);
                        set.add(index);
                        if (!isDownloading) {
                            latch.countDown();
                        }
                    });
                    peerConnection.setUnchokeConsumer(v -> {
                        if (!isDownloading) {
                            latch.countDown();
                        }
                    });
                    peerConnection.setExceptionConsumer(e -> {
                        isBad[0] = true;
                        latch.countDown();
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
                        latch.await();
                        latch = new CountDownLatch(1);
                        if (isBad[0]) {
                            break;
                        }
                        while (!havePieces.isEmpty()) {
                            int index = havePieces.first();
                            int x = pieceStatuses[index].get();
                            int size;
                            if (index == metainfo.getPieces().size() - 1) {
                                long allOtherPieces = metainfo.getPieceLength() * (metainfo.getPieces().size() - 1);
                                long allFiles = metainfo.getFiles().stream().mapToLong(TorrentFileInfo::getLengthInBytes).sum();
                                int lastSize = (int) (allFiles - allOtherPieces);
                                size = Math.min(CHUNK_SIZE, lastSize - x);
                                if (x >= lastSize) {
                                    havePieces.remove(index);
                                    continue;
                                }
                            } else {
                                size = Math.min(CHUNK_SIZE, (int) metainfo.getPieceLength() - x);
                                if (x >= metainfo.getPieceLength()) {
                                    havePieces.remove(index);
                                    continue;
                                }
                            }
                            if (pieceStatuses[index].compareAndSet(x, x + size)) {
                                isDownloading = true;
                                peerConnection.sendRequest(index, x, size);
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
