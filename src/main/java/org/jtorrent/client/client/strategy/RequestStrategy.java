package org.jtorrent.client.client.strategy;

import org.jtorrent.client.p2p.Piece;

import java.util.BitSet;
import java.util.SortedSet;

/**
 * @author Daniyar Itegulov
 */
public interface RequestStrategy {
    Piece choosePiece(SortedSet<Piece> rarest, BitSet interesting, Piece[] pieces);
}
