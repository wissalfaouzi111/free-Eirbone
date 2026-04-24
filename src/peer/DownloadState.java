package peer;
import java.io.IOException;
import java.util.BitSet;

public class DownloadState {
    FileInfo file;
    PieceStorage storage;

    public DownloadState(FileInfo file) throws IOException {
        this.file = file;
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