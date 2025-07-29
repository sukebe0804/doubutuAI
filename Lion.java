import java.util.ArrayList;
import java.util.List;

public class Lion extends Piece implements Cloneable {

    public Lion(PlayerType owner) {
        super(owner);
    }

    @Override
    public List<int[]> getPossibleMoves(int currentRow, int currentCol, Board board) {
        List<int[]> moves = new ArrayList<>();
        // 全方向の移動 (デルタ座標)
        int[][] deltas = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, // 上下左右
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1} // 斜め
        };

        for (int[] delta : deltas) {
            int newRow = currentRow + delta[0];
            int newCol = currentCol + delta[1];

            // 盤の範囲内かチェック
            if (board.isValidCoordinate(newRow, newCol)) {
                Piece targetPiece = board.getPiece(newRow, newCol);
                // 移動先に駒がない、または相手の駒であれば移動可能
                if (targetPiece == null || targetPiece.getOwner() != this.getOwner()) {
                    moves.add(new int[]{newRow, newCol});
                }
            }
        }
        return moves;
    }

    @Override
    public String getSymbol() {
        // プレイヤー1 (先手) は日本語、プレイヤー2 (後手) はカタカナ
        return (owner == PlayerType.PLAYER1) ? "獅" : "ラ";
    }
    //clone()実装
    @Override
    public Lion clone() {
        return (Lion) super.clone(); // フィールドが不変ならこれで十分
    }
}