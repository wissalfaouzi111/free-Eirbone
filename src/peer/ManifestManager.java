package peer;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Manages saving and loading of file metadata manifests.
 * Optional utility for persisting file information.
 */
public class ManifestManager {
    /**
     * Saves the file metadata to a manifest file.
     * @param metadata the FileMetadata to save
     * @param filePath the path to the manifest file
     * @throws IOException if an I/O error occurs
     */
    public static void saveMetadata(FileMetadata metadata, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("filename=" + metadata.getFilename());
            writer.println("length=" + metadata.getLength());
            writer.println("pieceSize=" + metadata.getPieceSize());
            writer.println("pieces=" + metadata.getNumberOfPieces());
            writer.println("key=" + metadata.getKey());
        }
    }

    /**
     * Loads file metadata from a manifest file.
     * @param filePath the path to the manifest file
     * @return the loaded FileMetadata
     * @throws IOException if an I/O error occurs
     */
    public static FileMetadata loadMetadata(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        String[] lines = content.split("\n");
        String filename = null, key = null;
        long length = 0;
        int pieceSize = 0, pieces = 0;
        for (String line : lines) {
            String[] parts = line.split("=");
            if (parts.length == 2) {
                switch (parts[0]) {
                    case "filename": filename = parts[1]; break;
                    case "length": length = Long.parseLong(parts[1]); break;
                    case "pieceSize": pieceSize = Integer.parseInt(parts[1]); break;
                    case "pieces": pieces = Integer.parseInt(parts[1]); break;
                    case "key": key = parts[1]; break;
                }
            }
        }
        return new FileMetadata(filename, length, pieceSize, pieces, key);
    }
}