import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomPlayer extends Player {

    private Random random;

    public RandomPlayer(String name) {
        super(name);
        this.random = new Random();
    }

    @Override
    public int[] chooseMove(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>(); // すべての合法手を格納するリスト
        PlayerType myPlayerType = this.getPlayerType();

        // 1. 駒の移動に関する合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = game.getBoard().getPiece(r, c);
                // 自分の駒であれば
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, game.getBoard());
                    for (int[] move : movesForPiece) {
                        // ★修正箇所★ 移動が王手にならないかチェック
                        if (game.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                            allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }

        // 2. 手駒を打つ合法手を収集
        List<Piece> captured = this.getCapturedPieces();
        for (int i = 0; i < captured.size(); i++) {
            Piece pieceToDrop = captured.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 空いているマスであれば打てる AND その手が王手にならないかチェック
                    if (game.getBoard().isEmpty(r, c) && game.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
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

    @Override
    public RandomPlayer clone() {
        // Player抽象クラスのclone()メソッドを呼び出すことで、
        // capturedPiecesのディープコピーも自動的に行われます。
        RandomPlayer cloned = (RandomPlayer) super.clone();
        // Randomクラスはスレッドセーフではないため、新しいインスタンスを作成することが推奨されます。
        cloned.random = new Random(); 
        return cloned;
    }
}