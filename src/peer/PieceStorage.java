import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * PieceStorage gestiona el archivo parcial de descarga en disco.
 * Usa RandomAccessFile para leer y escribir piezas a su offset correcto.
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

    private void createDirectories() throws IOException {
        File incomplete = new File(INCOMPLETE_DIR);
        if (!incomplete.exists() && !incomplete.mkdirs()) {
            throw new IOException("No se pudo crear el directorio: " + INCOMPLETE_DIR);
        }

        File complete = new File(COMPLETE_DIR);
        if (!complete.exists() && !complete.mkdirs()) {
            throw new IOException("No se pudo crear el directorio: " + COMPLETE_DIR);
        }
    }

    /**
     * Escribe una pieza en el archivo parcial y marca la pieza como disponible.
     */
    public synchronized void writePiece(int index, byte[] data) throws IOException {
        if (index < 0 || index >= totalPieces) {
            throw new IllegalArgumentException("Índice de pieza fuera de rango: " + index);
        }
        if (data.length > pieceSize) {
            throw new IOException("El tamaño de datos de la pieza excede " + pieceSize + " bytes.");
        }

        System.out.println("[PieceStorage] Writing piece " + index);
        raf.seek((long) index * pieceSize);
        byte[] buffer = new byte[pieceSize];
        System.arraycopy(data, 0, buffer, 0, data.length);
        raf.write(buffer);
        bufferMap.set(index);
    }

    /**
     * Lee una pieza desde el archivo parcial si está disponible.
     */
    public synchronized byte[] readPiece(int index) throws IOException {
        if (!hasPiece(index)) {
            throw new IOException("La pieza " + index + " no está disponible.");
        }
        if (index < 0 || index >= totalPieces) {
            throw new IllegalArgumentException("Índice de pieza fuera de rango: " + index);
        }

        raf.seek((long) index * pieceSize);
        byte[] piece = new byte[pieceSize];
        int read = raf.read(piece);
        if (read != pieceSize) {
            throw new IOException("Lectura incompleta de pieza " + index + ", bytes leídos: " + read);
        }
        return piece;
    }

    public synchronized boolean hasPiece(int index) {
        if (index < 0 || index >= totalPieces) {
            return false;
        }
        return bufferMap.get(index);
    }

    public synchronized boolean isComplete() {
        return bufferMap.cardinality() == totalPieces;
    }

    public synchronized List<Integer> getAvailablePieces() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < totalPieces; i++) {
            if (bufferMap.get(i)) {
                available.add(i);
            }
        }
        return available;
    }

    public synchronized BitSet getBufferMap() {
        return (BitSet) bufferMap.clone();
    }

    /**
     * Mueve el archivo parcial a la carpeta de archivos completos.
     */
    public synchronized void finalizeDownload(String finalFilename) throws IOException {
        if (!isComplete()) {
            throw new IOException("No se puede finalizar descarga: el archivo está incompleto.");
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
                throw new IOException("No se pudo eliminar el archivo temporal: " + partFile.getPath());
            }
        }

        this.raf = new RandomAccessFile(completeFile, "r");
    }

    public synchronized void markPiece(int index) {
        if (index >= 0 && index < totalPieces) {
            bufferMap.set(index);
        }
    }

    public synchronized void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }
}
