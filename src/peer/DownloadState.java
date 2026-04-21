import java.io.File;
import java.util.BitSet;

public class DownloadState {
    FileInfo file;
    File localFile; //chemin réel sur disque
    BitSet localBufferMap;

    public DownloadState(FileInfo file, File localFile) {
        this.file = file;
        this.localFile = localFile;
        this.localBufferMap = new BitSet(file.totalPieces());
    }

    public void markPiece(int i) {
        localBufferMap.set(i);
    }

    public boolean hasPiece(int index) {
        return localBufferMap.get(index);
    }

    public boolean isComplete() {
        return localBufferMap.cardinality() == file.totalPieces();
    }
}