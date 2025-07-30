import java.util.ArrayList;
import java.util.List;

public class GameState implements Cloneable {
    private Board board;
    private PlayerType currentPlayer;

    public GameState(Game game) {
        // GameからBoardと現在プレイヤーをコピー（必要に応じてGameクラスにコピー用メソッド追加してください）
        this.board = game.getBoard().clone();
        this.currentPlayer = game.getCurrentPlayerType();
    }

    // コピー用コンストラクタ
    public GameState(Board board, PlayerType currentPlayer) {
        this.board = board;
        this.currentPlayer = currentPlayer;
    }

    public Board getBoard() {
        return board;
    }

    public PlayerType getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * 指定プレイヤーの全合法手を返す
     * 返り値は {fromRow, fromCol, toRow, toCol} または 打ち駒なら {-1, -1, toRow, toCol} とする想定
     */
    public List<int[]> getAllLegalMoves(PlayerType player) {
        List<int[]> moves = new ArrayList<>();

        // 盤上の駒の移動可能手を列挙
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    List<int[]> pieceMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] dest : pieceMoves) {
                        moves.add(new int[]{r, c, dest[0], dest[1]});
                    }
                }
            }
        }

        // 打ち駒の合法手もここで追加する必要あり（capturedPiecesの扱い）

        return moves;
    }

    /**
     * 指定の手を盤面に反映する
     * moveは {fromRow, fromCol, toRow, toCol} または 打ち駒は {-1, -1, toRow, toCol} と想定
     */
    public void makeMove(int[] move) {
        int fromRow = move[0];
        int fromCol = move[1];
        int toRow = move[2];
        int toCol = move[3];

        if (fromRow == -1 && fromCol == -1) {
            // 打ち駒の処理（省略）
            // capturedPiecesから該当駒を除去し、boardに配置
            return;
        }

        Piece movingPiece = board.getPiece(fromRow, fromCol);
        if (movingPiece == null) return;

        // 移動先の駒があれば取得（取る）
        Piece captured = board.getPiece(toRow, toCol);
        if (captured != null) {
            // 持ち駒に加えるなどの処理（省略）
        }

        // 駒を移動
        board.removePiece(fromRow, fromCol);
        board.placePiece(movingPiece, toRow, toCol);

        // 成りの判定・処理（省略）

        // プレイヤー交代
        currentPlayer = currentPlayer.opponent();
    }

    /**
     * ゲーム終了判定（相手のライオンが取られた等）
     * @return ゲーム終了ならtrue
     */
    public boolean isGameOver() {
        boolean player1LionAlive = false;
        boolean player2LionAlive = false;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p instanceof Lion) {
                    if (p.getOwner() == PlayerType.PLAYER1) player1LionAlive = true;
                    if (p.getOwner() == PlayerType.PLAYER2) player2LionAlive = true;
                }
            }
        }
        return !(player1LionAlive && player2LionAlive);
    }

    @Override
    public GameState clone() {
        try {
            GameState cloned = (GameState) super.clone();
            cloned.board = this.board.clone();
            // currentPlayerはenumなので浅いコピーでOK
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public List<Piece> getPieces() {
	List<Piece> pieces = new ArrayList<>();
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece p = board.getPiece(r, c);
		if (p != null) {
		    pieces.add(p);
		}
	    }
	}
	return pieces;
    }
}
