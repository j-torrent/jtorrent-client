package org.jtorrent.client.p2p.messages;

/**
 * @author Daniyar Itegulov
 */
public enum Type {
    KEEP_ALIVE(-1),
    CHOKE(0),
    UNCHOKE(1),
    INTERESTED(2),
    NOT_INTERESTED(3),
    HAVE(4),
    BITFIELD(5),
    REQUEST(6),
    PIECE(7),
    CANCEL(8);

    private byte id;

    Type(int id) {
        this.id = (byte) id;
    }

    public boolean equals(byte c) {
        return this.id == c;
    }

    public byte getTypeByte() {
        return this.id;
    }

    public static Type get(byte c) {
        for (Type t : Type.values()) {
            if (t.equals(c)) {
                return t;
            }
        }
        return null;
    }
}