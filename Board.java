import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Board implements Cloneable {
    private Piece[][] boardArray;
    public static final int ROWS = 4; // private -> public に変更
    public static final int COLS = 3; // private -> public に変更

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
                    System.out.print(" " + piece.getSymbol() + " |");
                }
            }
            System.out.println("\n  ----------------");
        }
    }
    
    @Override
    public Board clone() {
        try {
            Board cloned = (Board) super.clone();
            cloned.boardArray = new Piece[ROWS][COLS]; // ROWS, COLS を使用
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
            throw new AssertionError("Board clone failed", e);
        }
    }
}