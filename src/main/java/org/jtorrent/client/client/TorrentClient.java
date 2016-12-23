package org.jtorrent.client.client;

import com.google.common.io.Files;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.metainfo.TorrentFileInfo;
import org.jtorrent.client.p2p.PeerConnection;
import org.jtorrent.client.p2p.messages.InterestedMessage;
import org.jtorrent.client.p2p.messages.PieceMessage;
import org.jtorrent.client.p2p.messages.RequestMessage;
import org.jtorrent.client.tracker.Peer;
import org.jtorrent.client.tracker.TrackerAnswer;
import org.jtorrent.client.tracker.TrackerConnection;
import org.jtorrent.client.util.SHA1Digester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TorrentClient.class);

    private static final int CHUNK_SIZE = 1 << 14;
    private static final int MILLISECOND_IN_SECOND = 1000;

    private final Metainfo metainfo;
    private final int connectionTimeout;

    private final BlockingQueue<Peer> unmanagedPeers;
    private final BlockingQueue<PieceMessage> pieceMessageBlockingQueue;
    private final AtomicInteger[] pieceStatuses;
    private final PeerId myPeerId;

    private final Thread trackerFetcher;
    private final List<Thread> peerThreads;
    private final Thread chunkAggregator;

    public TorrentClient(Metainfo metainfo, int downloaders, int connectionTimeout) {
        this.metainfo = metainfo;
        this.connectionTimeout = connectionTimeout;

        unmanagedPeers = new LinkedBlockingQueue<>();
        pieceMessageBlockingQueue = new LinkedBlockingDeque<>();
        pieceStatuses = new AtomicInteger[metainfo.getPieces().size()];
        Arrays.setAll(pieceStatuses, i -> new AtomicInteger(0));
        myPeerId = PeerId.generateJTorrentRandom();
        trackerFetcher = new Thread(new TrackerFetcher());
        trackerFetcher.setName("tracker-fetcher");
        trackerFetcher.start();
        chunkAggregator = new Thread(new ChunkAggregator());
        chunkAggregator.setName("chunk-aggregator");
        chunkAggregator.start();

        peerThreads = new ArrayList<>();
        for (int i = 0; i < downloaders; i++) {
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

                int pieceIndex = pieceMessage.getIndex();
                int chunkLength = pieceMessage.getBlock().length;
                int pieceLength = metainfo.getPieceLength(pieceIndex);

                int allLength = pieceBytesWritten.compute(
                        pieceIndex,
                        (k, v) -> (v == null ? 0 : v) + chunkLength
                );
                pieceBytes.compute(pieceIndex, (k, v) -> {
                    if (v == null) {
                        v = new byte[pieceLength];
                    }
                    System.arraycopy(pieceMessage.getBlock(), 0, v, pieceMessage.getBegin(), chunkLength);
                    return v;
                });
                if (allLength == pieceLength) {
                    pieceBytesWritten.remove(pieceIndex);
                    byte[] pieceSha1 = metainfo.getPieces().get(pieceIndex).getBytes(StandardCharsets.ISO_8859_1);
                    byte[] pieceIndBytes = pieceBytes.remove(pieceIndex);
                    byte[] pieceRealSha1 = SHA1Digester.getInstance().digest(pieceIndBytes);
                    if (Arrays.equals(pieceSha1, pieceRealSha1)) {
                        piecesDownloaded++;
                        LOG.info("Piece " + pieceIndex + " is downloaded");
                        LOG.info("Already " + piecesDownloaded + "/" + metainfo.getPieces().size() + " are done");
                        int begin = (int) (pieceIndex * metainfo.getPieceLength());
                        int end = begin + pieceLength;
                        int current = 0;
                        int currentI = 0;
                        for (int i = 0; i < metainfo.getFiles().size(); i++) {
                            TorrentFileInfo torrentFile = metainfo.getFiles().get(i);
                            if (current + torrentFile.getLengthInBytes() >= begin) {
                                for (int j = i; j < metainfo.getFiles().size(); j++) {
                                    TorrentFileInfo oTorrentFile = metainfo.getFiles().get(j);
                                    if (end < current + oTorrentFile.getLengthInBytes()) {
                                        int written = 0;
                                        for (int k = i; k <= j; k++) {
                                            TorrentFileInfo tf = metainfo.getFiles().get(k);
                                            int start = k == i ? begin - currentI : 0;
                                            int finish = k == j ? end - written : (int) tf.getLengthInBytes();
                                            try {
                                                File file = new File(tf.getPath());
                                                Files.createParentDirs(file);
                                                RandomAccessFile raf = new RandomAccessFile(file.getAbsolutePath(), "rw");
                                                raf.setLength(tf.getLengthInBytes());
                                                byte[] toWrite = Arrays.copyOfRange(pieceIndBytes, written, written + finish - start);
                                                raf.seek(begin);
                                                raf.write(toWrite);
                                            } catch (IOException e) {
                                                LOG.error("Couldn't write file", e);
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
                        pieceStatuses[pieceIndex].set(0);
                        LOG.warn("Invalid sha1 of piece");
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
                try (PeerConnection peerConnection = new PeerConnection(peer, metainfo, myPeerId, connectionTimeout)) {
                    final boolean[] isBad = {false};
                    isDownloading = false;
                    havePieces.clear();
                    latch = new CountDownLatch(1);
                    peerConnection.setPieceMessageConsumer(pieceMessage -> {
                        try {
                            pieceMessageBlockingQueue.put(pieceMessage);
                        } catch (InterruptedException ignored) {
                        }
                        isDownloading = false;
                        latch.countDown();
                    });
                    peerConnection.setBitfieldConsumer(bitfieldMessage -> {
                        BitSet bitSet = bitfieldMessage.getBitSet();
                        for (int i = bitSet.nextSetBit(0); i != -1; i = bitSet.nextSetBit(i + 1)) {
                            havePieces.add(i);
                        }
                    });
                    peerConnection.setHaveConsumer(haveMessage -> {
                        havePieces.add(haveMessage.getIndex());
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
                    peerConnection.send(new InterestedMessage());
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
                            int size = Math.min(CHUNK_SIZE, metainfo.getPieceLength(index) - x);
                            if (x >= metainfo.getPieceLength(index)) {
                                havePieces.remove(index);
                                continue;
                            }
                            if (pieceStatuses[index].compareAndSet(x, x + size)) {
                                isDownloading = true;
                                peerConnection.send(RequestMessage.of(index, x, size));
                                break;
                            }
                        }
                        if (havePieces.isEmpty()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (IOException e) {
                    LOG.info("Bad peer", e);
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
                    Thread.sleep(trackerAnswer.getInterval() * MILLISECOND_IN_SECOND);
                } catch (IOException e) {
                    LOG.info("Couldn't make a tracker request", e);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
