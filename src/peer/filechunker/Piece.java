/**
 * Represents a single piece of a file in the P2P system.
 * Each piece is 1024 bytes, padded if necessary.
 */
public class Piece {
    private int index;
    private byte[] data;
    private String md5;

    /**
     * Constructs a Piece with the given index, data, and MD5 hash.
     * @param index the piece index
     * @param data the piece data (1024 bytes)
     * @param md5 the MD5 hash of the data
     */
    public Piece(int index, byte[] data, String md5) {
        this.index = index;
        this.data = data;
        this.md5 = md5;
    }

    /**
     * Gets the piece index.
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the piece data.
     * @return the data byte array
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Gets the MD5 hash of the piece.
     * @return the MD5 hash
     */
    public String getMd5() {
        return md5;
    }

    /**
     * Gets the size of the piece data.
     * @return the size in bytes (always 1024)
     */
    public int getSize() {
        return data.length;
    }
}