import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Board implements Cloneable {
    private Piece[][] boardArray;
    public static final int ROWS = 4;
    public static final int COLS = 3;

    // ANSIカラーコード
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[31m"; // プレイヤー1（先手）
    public static final String ANSI_RED = "\u001B[32m";   // プレイヤー2（後手）

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

    public boolean isEmpty(int row, int col) {
        return isValidCoordinate(row, col) && boardArray[row][col] == null;
    }

    public void printBoard() {
        System.out.println("     0    1    2");
        System.out.println("  ----------------");
        for (int i = 0; i < ROWS; i++) {
            System.out.print(i + " |");
            for (int j = 0; j < COLS; j++) {
                Piece piece = boardArray[i][j];
                if (piece == null) {
                    System.out.print("    |");
                } else {
                    String color = (piece.getOwner() == PlayerType.PLAYER1) ? ANSI_GREEN : ANSI_RED;
                    String symbol = piece.isPromoted() ? piece.getSymbol() : piece.getSymbol();
                    
                    if (piece.getOwner() == PlayerType.PLAYER2) {
                        System.out.print(" " + color + reverseString(symbol) + ANSI_RESET + " |");
                    } else {
                        System.out.print(" " + color + symbol + ANSI_RESET + " |");
                    }
                }
            }
            System.out.println("\n  ----------------");
        }
    }

    private String reverseString(String s) {
        if (s == null) {
            return null;
        }
        return new StringBuilder(s).reverse().toString();
    }
    
    @Override
    public Board clone() {
        try {
            Board cloned = (Board) super.clone();
            cloned.boardArray = new Piece[ROWS][COLS];
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    Piece originalPiece = this.boardArray[r][c];
                    if (originalPiece != null) {
                        cloned.boardArray[r][c] = originalPiece.clone();
                    } else {
                        cloned.boardArray[r][c] = null;
                    }
                }
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}