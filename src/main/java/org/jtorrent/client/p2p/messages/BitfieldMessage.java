package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * @author Daniyar Itegulov
 */
public class BitfieldMessage extends PeerMessage {
    private static final int BASE_SIZE = 1;

    private final BitSet bitSet;

    private BitfieldMessage(ByteBuffer data, BitSet bitSet) {
        super(Type.BITFIELD, data);
        this.bitSet = bitSet;
    }

    public static BitfieldMessage of(BitSet bitSet) {
        byte[] bitfieldBuffer = new byte[(bitSet.size() + 8 - 1) / 8];
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            bitfieldBuffer[i / 8] |= 1 << (7 - (i % 8));
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(
                PeerMessage.MESSAGE_LENGTH_SIZE + BitfieldMessage.BASE_SIZE + bitfieldBuffer.length);
        buffer.putInt(BitfieldMessage.BASE_SIZE + bitfieldBuffer.length);
        buffer.put(Type.BITFIELD.getTypeByte());
        buffer.put(ByteBuffer.wrap(bitfieldBuffer));
        return new BitfieldMessage(buffer, bitSet);
    }
}
