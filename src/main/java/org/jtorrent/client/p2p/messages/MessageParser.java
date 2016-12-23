package org.jtorrent.client.p2p.messages;

import java.nio.ByteBuffer;

/**
 * @author Daniyar Itegulov
 */
public class MessageParser {
    private static final MessageParser INSTANCE = new MessageParser();

    private MessageParser() {
    }

    public PeerMessage parse(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length == 0) {
            return new KeepAliveMessage();
        } else if (length != buffer.remaining()) {
            throw new IllegalArgumentException("Message size did not match announced size!");
        }

        Type type = Type.get(buffer.get());
        if (type == null) {
            throw new IllegalArgumentException("Unknown message ID!");
        }

        switch (type) {
            case CHOKE:
                return new ChokeMessage();
            case UNCHOKE:
                return new UnchokeMessage();
            case INTERESTED:
                return new InterestedMessage();
            case NOT_INTERESTED:
                return new NotInterestedMessage();
            case HAVE:
                return HaveMessage.of(buffer.getInt());
            case BITFIELD:
                return BitfieldMessage.parse(buffer.slice());
            case REQUEST:
                return RequestMessage.parse(buffer.slice());
            case PIECE:
                return PieceMessage.parse(buffer.slice());
            case CANCEL:
                return CancelMessage.parse(buffer.slice());
            default:
                throw new IllegalStateException("Java can't into ADT");
        }
    }

    public static MessageParser getInstance() {
        return INSTANCE;
    }
}
