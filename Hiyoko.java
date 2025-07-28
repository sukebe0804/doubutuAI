// Hiyoko.java

import java.util.ArrayList;
import java.util.List;

public class Hiyoko extends Piece {

    private boolean isPromoted = false; // 成っているかどうかの状態を保持

    public Hiyoko(PlayerType owner) {
        super(owner); // PieceクラスのコンストラクタはPlayerTypeのみを受け取る
        this.isPromoted = false; // 初期状態では成っていない
    }

    @Override
    public List<int[]> getPossibleMoves(int currentRow, int currentCol, Board board) {
        List<int[]> moves = new ArrayList<>();

        if (isPromoted) { // にわとりの動き
            // にわとりはライオンと同じ動き（全方向1マス）
            int[][] directions = {
                {-1, -1}, {-1, 0}, {-1, 1}, // 上方向
                { 0, -1},          { 0, 1}, // 左右
                { 1, -1}, { 1, 0}, { 1, 1}  // 下方向
            };

            for (int[] d : directions) {
                int newRow = currentRow + d[0];
                int newCol = currentCol + d[1];

                if (isValidMove(newRow, newCol, board)) {
                    Piece targetPiece = board.getPiece(newRow, newCol);
                    if (targetPiece == null || targetPiece.getOwner() != this.getOwner()) {
                        moves.add(new int[]{newRow, newCol});
                    }
                }
            }
        } else { // ひよこの動き
            // ひよこは前方に1マス進める
            int direction = (this.getOwner() == PlayerType.PLAYER1) ? -1 : 1; // PLAYER1は上、PLAYER2は下

            int newRow = currentRow + direction;
            int newCol = currentCol;

            if (isValidMove(newRow, newCol, board)) {
                Piece targetPiece = board.getPiece(newRow, newCol);
                if (targetPiece == null || targetPiece.getOwner() != this.getOwner()) {
                    moves.add(new int[]{newRow, newCol});
                }
            }
        }
        return moves;
    }

    // 駒が盤面の有効な範囲内にあるかチェックするヘルパーメソッド
    private boolean isValidMove(int r, int c, Board board) {
        return r >= 0 && r < Board.ROWS && c >= 0 && c < Board.COLS;
    }

    // Pieceクラスの抽象メソッドを実装
    @Override
    public String getSymbol() {
        return isPromoted ? "にわとり" : "ひよこ";
    }

    // 成るメソッド
    @Override
    public void promote() {
        this.isPromoted = true;
    }

    // 成った駒を元に戻すメソッド (Pieceクラスにdemote()がないため@Overrideは付けない)
    public void demote() {
        this.isPromoted = false;
    }

    // 成っているかどうかの状態を取得するメソッド
    public boolean isPromoted() {
        return isPromoted;
    }

    @Override
    public Piece clone() {
        Hiyoko clonedHiyoko = new Hiyoko(this.getOwner());
        clonedHiyoko.isPromoted = this.isPromoted; // 成り状態をクローンする
        return clonedHiyoko;
    }
}
