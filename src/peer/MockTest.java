package peer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MockTest {

    public static void main(String[] args) {
        try {
            // 1. Select file
            File file;
            if (args.length > 0) {
                file = new File(args[0]);
            } else {
                file = new File("test.dat"); // default file
            }

            System.out.println("Testing file: " + file.getAbsolutePath());

            if (!file.exists()) {
                System.out.println("File not found: " + file.getAbsolutePath());
                return;
            }

            // 2. Create FileChunker with the file
            FileChunker chunker = new FileChunker(file);

            // 3. Get pieces from chunker
            List<Piece> pieces = new ArrayList<>();
            for (int i = 0; i < chunker.getNumberOfPieces(); i++) {
                pieces.add(chunker.getPiece(i));
            }

            // 4. Get metadata from chunker
            FileMetadata metadata = chunker.getMetadata();

            // 5. Print file info
            System.out.println("\n===== FILE INFO =====");
            System.out.println("Filename: " + metadata.getFilename());
            System.out.println("Size: " + metadata.getLength());
            System.out.println("Piece size: " + metadata.getPieceSize());
            System.out.println("Number of pieces: " + metadata.getNumberOfPieces());
            System.out.println("File key (MD5): " + metadata.getKey());

            // 6. Print pieces info
            System.out.println("\n===== PIECES =====");
            for (Piece p : pieces) {
                System.out.println(
                        "Piece " + p.getIndex()
                        + " | size=" + p.getData().length
                        + " | md5=" + p.getMd5()
                );
            }

            // 7. Send ANNOUNCE message using mock network
            IReseau reseau = new MockReseau();
            String announceMessage = String.format(
                    "announce listen 2222 seed [%s %d %d %s]",
                    metadata.getFilename(),
                    metadata.getLength(),
                    metadata.getPieceSize(),
                    metadata.getKey()
            );

            System.out.println("\n===== MOCK NETWORK =====");
            reseau.envoyerMessage(announceMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}