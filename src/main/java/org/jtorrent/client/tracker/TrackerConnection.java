package org.jtorrent.client.tracker;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jtorrent.client.metainfo.Metainfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

public class TrackerConnection {
    private final Metainfo metainfo;
    private HttpClient client = HttpClientBuilder.create().build();


    public TrackerConnection(Metainfo metainfo) {
        this.metainfo = metainfo;
    }

    public void requestPeers() throws IOException {
        try {
            URIBuilder builder = new URIBuilder(metainfo.getAnnounce());
            builder.addParameter("info_hash", new String(metainfo.getInfoSHA1(), "ISO-8859-1"))
                    .addParameter("peer_id", "-TO0042-0ab8e8a31019")
                    .addParameter("port", "6881")
                    .addParameter("event", "started")
                    .addParameter("uploaded", "0")
                    .addParameter("downloaded", "0")
                    .addParameter("left", "1028128768")
                    .addParameter("no_peer_id", "0");
            System.out.println(builder.build());
            HttpGet request = new HttpGet(builder.build());
            HttpResponse response = client.execute(request);
            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            System.out.println(result);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid metainfo announce", e);
        }
    }
}
