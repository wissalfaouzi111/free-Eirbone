/**
 * Describes a file shared in the P2P network.
 * Contains metadata used for announcing files to the tracker.
 */
public class FileMetadata {
    private String filename;
    private long length;
    private int pieceSize;
    private int numberOfPieces;
    private String key;

    /**
     * Constructs FileMetadata with the given parameters.
     * @param filename the file name
     * @param length the file length in bytes
     * @param pieceSize the size of each piece (1024 bytes)
     * @param numberOfPieces the total number of pieces
     * @param key the MD5 key of the file
     */
    public FileMetadata(String filename, long length, int pieceSize, int numberOfPieces, String key) {
        this.filename = filename;
        this.length = length;
        this.pieceSize = pieceSize;
        this.numberOfPieces = numberOfPieces;
        this.key = key;
    }

    /**
     * Gets the file name.
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Gets the file length.
     * @return the length in bytes
     */
    public long getLength() {
        return length;
    }

    /**
     * Gets the piece size.
     * @return the piece size
     */
    public int getPieceSize() {
        return pieceSize;
    }

    /**
     * Gets the number of pieces.
     * @return the number of pieces
     */
    public int getNumberOfPieces() {
        return numberOfPieces;
    }

    /**
     * Gets the file key (MD5 hash).
     * @return the key
     */
    public String getKey() {
        return key;
    }
}