import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomPlayer extends Player {

    private Random random;

    public RandomPlayer(String name) {
        super(name);
        this.random = new Random();
    }

    /**
     * ランダムな合法手（移動または手駒を打つ）を選択するメソッド
     * @param game 現在のゲーム状態
     * @return 選択された手の情報 (int[4]配列: {fromRow, fromCol, toRow, toCol} または { -1, capturedPieceIndex, dropRow, dropCol } )
     */
    public int[] chooseRandomMove(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>(); // すべての合法手を格納するリスト

        // 1. 駒の移動に関する合法手を収集
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Piece piece = game.getBoard().getPiece(r, c);
                // 自分の駒であれば
                if (piece != null && piece.getOwner() == this.getPlayerType()) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, game.getBoard());
                    for (int[] move : movesForPiece) {
                        // 移動元の座標と移動先の座標をセットで追加
                        allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                    }
                }
            }
        }

        // 2. 手駒を打つ合法手を収集
        List<Piece> captured = this.getCapturedPieces();
        for (int i = 0; i < captured.size(); i++) {
            Piece pieceToDrop = captured.get(i);
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 3; c++) {
                    // 空いているマスであれば打てる
                    if (game.getBoard().isEmpty(r, c)) {
                        // 手駒を打つ手は、特別な形式でリストに追加
                        // { -1, 手駒リストのインデックス, 落とす行, 落とす列 }
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }

        if (allPossibleMoves.isEmpty()) {
            return null; // 動かせる手も打てる手駒もない
        }

        // 全ての合法手の中からランダムに一つを選ぶ
        int randomIndex = random.nextInt(allPossibleMoves.size());
        return allPossibleMoves.get(randomIndex);
    }
}