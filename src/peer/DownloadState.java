import java.io.File;
import java.io.IOException;
import java.util.BitSet;

public class DownloadState {
    FileInfo file;
    File localFile; // chemin réel sur disque
    PieceStorage storage;

    public DownloadState(FileInfo file, File localFile) throws IOException {
        this.file = file;
        this.localFile = localFile;
        this.storage = new PieceStorage(file);
    }

    public void writePiece(int index, byte[] data) throws IOException {
        storage.writePiece(index, data);
    }

    public byte[] readPiece(int index) throws IOException {
        return storage.readPiece(index);
    }

    public void markPiece(int i) {
        storage.markPiece(i);
    }

    public boolean hasPiece(int index) {
        return storage.hasPiece(index);
    }

    public boolean isComplete() {
        return storage.isComplete();
    }

    public BitSet getBufferMap() {
        return storage.getBufferMap();
    }

    public PieceStorage getStorage() {
        return storage;
    }
}