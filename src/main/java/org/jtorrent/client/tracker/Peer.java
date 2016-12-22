package org.jtorrent.client.tracker;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import org.jtorrent.client.metainfo.PeerId;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

public class Peer {
    @NotNull
    private final Optional<PeerId> id;
    @NotNull
    private final InetSocketAddress address;

    public Peer(@NotNull Optional<PeerId> id, @NotNull InetSocketAddress address) {
        this.id = id;
        this.address = address;
    }

    @NotNull
    public Optional<PeerId> getId() {
        return id;
    }

    @NotNull
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return Objects.equals(id, peer.id) &&
                Objects.equals(address, peer.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, address);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "id='" + id + '\'' +
                ", address=" + address +
                '}';
    }
}
