import java.util.ArrayList;
import java.util.List;

public class Hiyoko extends Piece {
    public Hiyoko(PlayerType owner) {
        super(owner);
    }

    @Override
    public List<int[]> getPossibleMoves(int currentRow, int currentCol, Board board) {
        List<int[]> moves = new ArrayList<>();
        // 先手は行が増加方向、後手は行が減少方向
        int direction = (owner == PlayerType.PLAYER1) ? 1 : -1;

        if (isPromoted()) {
            // にわとりの動き(今回は, 将棋の「金」と同じ動き方に設定)
            int[][] deltas = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1}, // 上下左右
                {-1, -1}, {-1, 1} // 斜め
            };
            for (int[] delta : deltas) {
                int newRow = currentRow + delta[0];
                int newCol = currentCol + delta[1];
                if (board.isValidCoordinate(newRow, newCol)) {
                    Piece targetPiece = board.getPiece(newRow, newCol);
                    if (targetPiece == null || targetPiece.getOwner() != this.getOwner()) {
                        moves.add(new int[]{newRow, newCol});
                    }
                }
            }
        } else {
            // ひよこの動き (前方に1マス)
            int newRow = currentRow + direction;
            int newCol = currentCol;
            if (board.isValidCoordinate(newRow, newCol)) {
                Piece targetPiece = board.getPiece(newRow, newCol);
                if (targetPiece == null || targetPiece.getOwner() != this.getOwner()) {
                    moves.add(new int[]{newRow, newCol});
                }
            }
        }
        return moves;
    }

    @Override
    public String getSymbol() {
        // 先手は日本語、後手はカタカナ、成りは共通
        if (owner == PlayerType.PLAYER1) {
            return isPromoted() ? "鶏" : "ひ"; // にわとり
        } else {
            return isPromoted() ? "鶏" : "ヒ"; // ニワトリ
        }
    }
}