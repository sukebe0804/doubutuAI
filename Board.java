public class Board {
    private Piece[][] boardArray;
    private static final int ROWS = 4;
    private static final int COLS = 3;

    public Board() {
        boardArray = new Piece[ROWS][COLS];
    }

    public Piece getPiece(int row, int col) {
        if (isValidCoordinate(row, col)) {
            return boardArray[row][col];
        }
        return null;
    }

    public void placePiece(Piece piece, int row, int col) {
        if (isValidCoordinate(row, col)) {
            boardArray[row][col] = piece;
        }
    }

    public void removePiece(int row, int col) {
        if (isValidCoordinate(row, col)) {
            boardArray[row][col] = null;
        }
    }

    public boolean isValidCoordinate(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    /**
     * 指定された位置が空いているかを確認します。
     * @param row 確認する行
     * @param col 確認する列
     * @return 空いていればtrue、そうでなければfalse
     */
    public boolean isEmpty(int row, int col) {
        return isValidCoordinate(row, col) && boardArray[row][col] == null;
    }

    public void printBoard() {
        System.out.println("-------------");
        for (int i = 0; i < ROWS; i++) {
            System.out.print("|");
            for (int j = 0; j < COLS; j++) {
                Piece piece = boardArray[i][j];
                if (piece == null) {
                    System.out.print("   |"); // 空きマス
                } else {
                    System.out.print(" " + piece.getSymbol() + " |"); // 駒のシンボルを表示
                }
            }
            System.out.println("\n-------------");
        }
    }
}