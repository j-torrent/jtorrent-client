package org.jtorrent.client.client.strategy;

import org.jtorrent.client.p2p.Piece;

import java.util.BitSet;
import java.util.SortedSet;

/**
 * @author Daniyar Itegulov
 */
public class RequestStrategySequential implements RequestStrategy {
    @Override
    public Piece choosePiece(SortedSet<Piece> rarest, BitSet interesting, Piece[] pieces) {
        for (Piece p : pieces) {
            if (interesting.get(p.getIndex())) return p;
        }
        return null;
    }
}
