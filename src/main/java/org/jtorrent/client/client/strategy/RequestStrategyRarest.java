package org.jtorrent.client.client.strategy;

import org.jtorrent.client.p2p.Piece;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.SortedSet;

/**
 * @author Daniyar Itegulov
 */
public class RequestStrategyRarest implements RequestStrategy {
    private static final int RAREST_PIECE_JITTER = 42;

    private Random random = new Random();

    @Override
    public Piece choosePiece(SortedSet<Piece> rarest, BitSet interesting, Piece[] pieces) {
        ArrayList<Piece> choice = new ArrayList<>(RAREST_PIECE_JITTER);
        for (Piece piece : rarest) {
            if (interesting.get(piece.getIndex())) {
                choice.add(piece);
                if (choice.size() >= RAREST_PIECE_JITTER) {
                    break;
                }
            }
        }

        if (choice.size() == 0) return null;

        return choice.get(random.nextInt(Math.min(choice.size(), RAREST_PIECE_JITTER)));
    }
}
