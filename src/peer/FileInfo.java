
class FileInfo
{

    String filename;
    long length;
    int pieceSize;
    String key;

    public FileInfo(String name, long length, int pieceSize, String key){
        this.filename = name;
        this.length = length;
        this.pieceSize = pieceSize;
        this.key = key;
    }
    
    public String getFilename() {
        return filename;
    }

    public long getLength() {
        return length;
    }

    public int getPieceSize() {
        return pieceSize;
    }

    public String getKey() {
        return key;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setLength(long length){
        this.length = length;
    }

    public void setPieceSize(int pieceSize){
        this.pieceSize = pieceSize;
    }

    public void setKey(String key){
        this.key = key;
    }

    public int totalPieces() {
        return (int) ((length + pieceSize - 1) / pieceSize);
    }

}
    
