
class FileInfo
{

    String Filename;
    long length;
    int Piece_size;
    String key;


    public FileInfo(String name, long length, int pieceSize, String key){
        this.Filename = name;
        this.length = length;
        this.Piece_size = pieceSize;
        this.key = key;
    }
    
    public void setFilename(String filename) {
        this.Filename = filename;
    }

    public void setLength(int length){
        this.length = length;
    }

    public void setPieceSize(int PieceSize){
        this.Piece_size = PieceSize;
    }

    public void setKey(String key){
        this.key = key;
    }

    public int totalPieces() {
        return (int) ((length + pieceSize - 1) / pieceSize);
    }

}
    
