package peer;
import java.io.File;
import java.io.IOException;

/**
 * Simple test program for the file chunking functionality.
 * Loads a file, splits it into pieces, and prints information about each piece.
 */
public class Test {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Test <file_path>");
            return;
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println("File does not exist");
            return;
        }
        try {
            FileChunker chunker = new FileChunker(file);
            System.out.println("Pieces: " + chunker.getNumberOfPieces());
            for (int i = 0; i < chunker.getNumberOfPieces(); i++) {
                Piece piece = chunker.getPiece(i);
                System.out.println("Piece " + piece.getIndex() + " MD5: " + piece.getMd5() + " Size:" + piece.getSize());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}