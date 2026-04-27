import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * PieceStorage manages the partial download file on disk.
 * It uses RandomAccessFile to read and write pieces at their correct offsets.
 * Supports downloading files in arbitrary piece order and serving pieces even if the file is incomplete.
 */
public class PieceStorage {
    private static final String INCOMPLETE_DIR = "data/incomplete";
    private static final String COMPLETE_DIR = "data/complete";
    private final FileInfo fileInfo;
    private final File partFile;
    private RandomAccessFile raf;
    private final BitSet bufferMap;
    private final int pieceSize;
    private final int totalPieces;
    private final long totalLength;

    /**
     * Creates a new PieceStorage for the given file.
     * Initializes the partial file with the correct total length.
     * @param fileInfo the file metadata
     * @throws IOException if directories cannot be created or file cannot be initialized
     */
    public PieceStorage(FileInfo fileInfo) throws IOException {
        this.fileInfo = fileInfo;
        this.pieceSize = fileInfo.getPieceSize();
        this.totalPieces = fileInfo.totalPieces();
        this.totalLength = fileInfo.getLength();
        this.bufferMap = new BitSet(totalPieces);

        createDirectories();
        this.partFile = new File(INCOMPLETE_DIR, fileInfo.getKey() + ".part");
        this.raf = new RandomAccessFile(partFile, "rw");
        this.raf.setLength(totalLength);
    }

    /**
     * Creates the necessary directories if they do not exist.
     * @throws IOException if directories cannot be created
     */
    private void createDirectories() throws IOException {
        File incomplete = new File(INCOMPLETE_DIR);
        if (!incomplete.exists() && !incomplete.mkdirs()) {
            throw new IOException("Could not create directory: " + INCOMPLETE_DIR);
        }

        File complete = new File(COMPLETE_DIR);
        if (!complete.exists() && !complete.mkdirs()) {
            throw new IOException("Could not create directory: " + COMPLETE_DIR);
        }
    }

    /**
     * Writes a piece to the partial file at the correct offset and marks it as available.
     * @param index the piece index
     * @param data the piece data (must be <= expected size for the piece)
     * @throws IOException if write fails
     * @throws IllegalArgumentException if index is out of range
     */
    public synchronized void writePiece(int index, byte[] data) throws IOException {
        if (index < 0 || index >= totalPieces) {
            throw new IllegalArgumentException("Piece index out of range: " + index);
        }
        int expectedSize = (index == totalPieces - 1) ? (int)(totalLength % pieceSize) : pieceSize;
        if (expectedSize == 0) expectedSize = pieceSize;
        if (data.length > expectedSize) {
            throw new IOException("Piece data size exceeds " + expectedSize + " bytes.");
        }

        System.out.println("[PieceStorage] Writing piece " + index);
        raf.seek((long) index * pieceSize);
        byte[] buffer = new byte[expectedSize];
        System.arraycopy(data, 0, buffer, 0, data.length);
        raf.write(buffer);
        bufferMap.set(index);
    }

    /**
     * Reads a piece from the partial file if it is available.
     * @param index the piece index
     * @return the piece data
     * @throws IOException if piece is not available or read fails
     * @throws IllegalArgumentException if index is out of range
     */
    public synchronized byte[] readPiece(int index) throws IOException {
        if (!hasPiece(index)) {
            throw new IOException("Piece " + index + " is not available.");
        }
        if (index < 0 || index >= totalPieces) {
            throw new IllegalArgumentException("Piece index out of range: " + index);
        }

        int expectedSize = (index == totalPieces - 1) ? (int)(totalLength % pieceSize) : pieceSize;
        if (expectedSize == 0) expectedSize = pieceSize; // Si divisible, última es pieceSize

        raf.seek((long) index * pieceSize);
        byte[] piece = new byte[expectedSize];
        int read = raf.read(piece);
        if (read != expectedSize) {
            throw new IOException("Incomplete read of piece " + index + ", bytes read: " + read + ", expected: " + expectedSize);
        }
        return piece;
    }

    /**
     * Checks if a piece is available.
     * @param index the piece index
     * @return true if the piece is available
     */
    public synchronized boolean hasPiece(int index) {
        if (index < 0 || index >= totalPieces) {
            return false;
        }
        return bufferMap.get(index);
    }

    /**
     * Checks if the download is complete (all pieces are available).
     * @return true if all pieces are downloaded
     */
    public synchronized boolean isComplete() {
        return bufferMap.cardinality() == totalPieces;
    }

    /**
     * Returns a list of available piece indices.
     * @return list of piece indices that are available
     */
    public synchronized List<Integer> getAvailablePieces() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < totalPieces; i++) {
            if (bufferMap.get(i)) {
                available.add(i);
            }
        }
        return available;
    }

    /**
     * Returns a copy of the buffer map (BitSet).
     * @return the buffer map
     */
    public synchronized BitSet getBufferMap() {
        return (BitSet) bufferMap.clone();
    }

    /**
     * Finalizes the download by moving the partial file to the complete directory.
     * @param finalFilename the final filename
     * @throws IOException if download is incomplete or move fails
     */
    public synchronized void finalizeDownload(String finalFilename) throws IOException {
        if (!isComplete()) {
            throw new IOException("Cannot finalize download: file is incomplete.");
        }

        close();
        File completeFile = new File(COMPLETE_DIR, finalFilename);
        if (!partFile.renameTo(completeFile)) {
            try (FileInputStream in = new FileInputStream(partFile);
                 FileOutputStream out = new FileOutputStream(completeFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            if (!partFile.delete()) {
                throw new IOException("Could not delete temporary file: " + partFile.getPath());
            }
        }

        this.raf = new RandomAccessFile(completeFile, "r");
    }

    /**
     * Marks a piece as available without writing data (useful for resuming downloads).
     * @param index the piece index
     */
    public synchronized void markPiece(int index) {
        if (index >= 0 && index < totalPieces) {
            bufferMap.set(index);
        }
    }

    /**
     * Closes the RandomAccessFile.
     * @throws IOException if close fails
     */
    public synchronized void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }
}
