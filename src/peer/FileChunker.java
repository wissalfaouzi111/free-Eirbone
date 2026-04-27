import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for splitting files into pieces and managing file metadata.
 * Handles file chunking for the P2P file sharing system.
 */
public class FileChunker {
    private static final int PIECE_SIZE = 1024;
    private List<Piece> pieces;
    private FileMetadata metadata;

    /**
     * Constructs a FileChunker for the given file, splitting it into pieces.
     * @param file the file to chunk
     * @throws IOException if an I/O error occurs 
     */
    public FileChunker(File file) throws IOException {
        this.pieces = splitFile(file);
        long length = file.length();
        int numberOfPieces = pieces.size();
        String key = HashUtils.md5(file);
        this.metadata = new FileMetadata(file.getName(), length, PIECE_SIZE, numberOfPieces, key);
    }

    /**
     * Splits the file into pieces of 1024 bytes each, padding the last piece if necessary.
     * @param file the file to split
     * @return a list of Piece objects
     * @throws IOException if an I/O error occurs
     */
    public List<Piece> splitFile(File file) throws IOException {
        List<Piece> pieceList = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[PIECE_SIZE];
            int index = 0;
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] pieceData = new byte[PIECE_SIZE];
                System.arraycopy(buffer, 0, pieceData, 0, bytesRead);
                // Pad with zeros if necessary
                for (int i = bytesRead; i < PIECE_SIZE; i++) {
                    pieceData[i] = 0;
                }
                String md5 = HashUtils.md5(pieceData);
                Piece piece = new Piece(index, pieceData, md5);
                pieceList.add(piece);
                index++;
            }
        }
        return pieceList;
    }

    /**
     * Gets the piece at the specified index.
     * @param index the piece index
     * @return the Piece object
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public Piece getPiece(int index) {
        if (index < 0 || index >= pieces.size()) {
            throw new IndexOutOfBoundsException("Invalid piece index");
        }
        return pieces.get(index);
    }

    /**
     * Gets the total number of pieces.
     * @return the number of pieces
     */
    public int getNumberOfPieces() {
        return pieces.size();
    }

    /**
     * Gets the file metadata.
     * @return the FileMetadata object
     */
    public FileMetadata getMetadata() {
        return metadata;
    }
}