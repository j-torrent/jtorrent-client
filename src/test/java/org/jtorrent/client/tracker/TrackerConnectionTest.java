package org.jtorrent.client.tracker;

import com.xebialabs.restito.server.StubServer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.jtorrent.client.ConsumerE;
import org.jtorrent.client.metainfo.Metainfo;
import org.jtorrent.client.metainfo.PeerId;
import org.jtorrent.client.metainfo.TorrentFileInfo;
import org.jtorrent.client.util.SHA1Digester;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.bytesContent;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.method;

/**
 * @author Daniyar Itegulov
 */
public class TrackerConnectionTest {
    private static int PORT = 8080;

    private void withStubServer(int port, ConsumerE<StubServer, Exception> callback) throws Exception {
        StubServer stubServer = null;
        try {
            stubServer = new StubServer(port).run();
            callback.accept(stubServer);
        } finally {
            if (stubServer != null) {
                stubServer.stop();
            }
        }
    }

    @Test
    public void shouldPassVerification() throws Exception {
        withStubServer(PORT, s -> {
            String host = new String(InetAddress.getByName("127.0.0.1").getAddress(), StandardCharsets.ISO_8859_1);
            int port = 6882;
            String portString = new String(new byte[]{(byte) (port / 256), (byte) (port % 256)}, StandardCharsets.ISO_8859_1);
            String peer = host + portString;
            Assert.assertEquals(peer.length(), 6);
            byte[] trackerResponse = ("d5:peers6:" + peer + "8:intervali1800ee").getBytes(StandardCharsets.ISO_8859_1);
            whenHttp(s)
                    .match(method(Method.GET).startsWithUri("/tracker"))
                    .then(status(HttpStatus.OK_200), bytesContent(trackerResponse));
            byte[] bytes = "Abacaba test exm".getBytes(StandardCharsets.ISO_8859_1);
            Assert.assertEquals(bytes.length, 16);
            SHA1Digester sha1Digester = SHA1Digester.getInstance();
            Metainfo metainfo = new Metainfo(
                    "http://localhost/tracker",
                    new byte[20],
                    8,
                    Arrays.asList(
                            sha1Digester.digest(Arrays.copyOfRange(bytes, 0, 8)),
                            sha1Digester.digest(Arrays.copyOfRange(bytes, 8, 16))
                    ),
                    Collections.singletonList(new TorrentFileInfo(16, "example.txt"))
            );
            PeerId peerId = PeerId.generateJTorrentRandom();
            TrackerConnection connection = new TrackerConnectionBuilder()
                    .setMetainfo(metainfo)
                    .setMyPeerId(peerId)
                    .setTrackerPort(PORT)
                    .build();
            TrackerAnswer trackerAnswer = connection.makeInitialRequest();
            Assert.assertEquals(1800, trackerAnswer.getInterval());
            Assert.assertEquals(
                    Collections.singletonList(
                            new Peer(Optional.empty(), new InetSocketAddress(InetAddress.getByAddress(host.getBytes(StandardCharsets.ISO_8859_1)), port))
                    ),
                    trackerAnswer.getPeers()
            );
        });
    }
}
