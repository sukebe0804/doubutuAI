import java.util.ArrayList;
import java.util.List;

public class AlphaBeta extends Player {
    private static final int MAX_DEPTH = 3; // 探索の深さ
    private static final int WIN_SCORE = 10000; // 勝利時のスコア

    public AlphaBeta(String name) {
        super(name);
    }

    // Playerクラスの抽象メソッドを実装
    @Override
    public int[] chooseMove(Game game) {
        return chooseBestMove(game);
    }

    public int[] chooseBestMove(Game game) {
        game.setSilentMode(true); // 探索中は出力をオフ
        
        List<int[]> allPossibleMoves = getAllPossibleMoves(game);
        if (allPossibleMoves.isEmpty()) {
            return null; // 動かせる手も打てる手駒もない
        } 

        int[] bestMove = null;
        int bestValue = Integer.MIN_VALUE;
            
        // 各合法手について評価
        for (int[] move : allPossibleMoves) {
            Game newGame = game.clone(); // ゲーム状態をコピー
            applyMove(newGame, move); // 手を適用
            
            // アルファベータ探索
            int value = alphaBeta(newGame, MAX_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
        }
        
        game.setSilentMode(false);
        
        return bestMove;
    }

    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        
        // 1. 駒の移動に関する合法手を収集
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Piece piece = game.getBoard().getPiece(r, c);
                if (piece != null && piece.getOwner() == this.getPlayerType()) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, game.getBoard());
                    for (int[] move : movesForPiece) {
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
                    if (game.getBoard().isEmpty(r, c)) {
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        
        return allPossibleMoves;
    }

    private void applyMove(Game game, int[] move) {
        if (move[0] == -1) { // 手駒を打つ場合
            Piece piece = this.getCapturedPieces().get(move[1]);
            game.performDrop(piece, move[2], move[3]);
        } else { // 駒を移動する場合
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }

    private int alphaBeta(Game game, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (depth == 0) {
            return evaluate(game);
        }

        if (maximizingPlayer) {
            int value = Integer.MIN_VALUE;
            for (int[] move : getAllPossibleMoves(game)) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                value = Math.max(value, alphaBeta(newGame, depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, value);
                if (alpha >= beta) {
                    break; // βカット
                }
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (int[] move : getAllPossibleMoves(game)) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                value = Math.min(value, alphaBeta(newGame, depth - 1, alpha, beta, true));
                beta = Math.min(beta, value);
                if (alpha >= beta) {
                    break; // αカット
                }
            }
            return value;
        }
    }

    private int evaluate(Game game) {
	// 1. 勝敗判定（既存のロジック）
	PlayerType winner = game.isGameOver();
	if (winner == this.getPlayerType()) return WIN_SCORE;
	if (winner == getOpponentPlayerType()) return -WIN_SCORE;

	int score = 0;
	Board board = game.getBoard();

	// 2. 駒の基本価値と位置評価
	for (int r = 0; r < 4; r++) {
	    for (int c = 0; c < 3; c++) {
		Piece piece = board.getPiece(r, c);
		if (piece == null) continue;

		int pieceValue = getPieceValue(piece);
		if (piece.getOwner() == this.getPlayerType()) {
		    score += pieceValue;
		    // 位置ボーナス（例：ライオンが敵陣なら+500）
		    if (piece instanceof Lion && isEnemyTerritory(r, piece.getOwner())) {
			score += 500;
		    }
		} else {
		    score -= pieceValue;
		}
	    }
	}

	// 3. 手駒の評価（駒の種類ごと）
	for (Piece captured : this.getCapturedPieces()) {
	    score += getCapturedPieceValue(captured);
	}

	// 4. 王手の評価
	if (game.isKingInCheck(this.getPlayerType())) score += 200;
	if (game.isKingInCheck(getOpponentPlayerType())) score -= 200;

	return score;
    }

// 駒の基本価値（改善版）
    private int getPieceValue(Piece piece) {
	switch (piece.getSymbol()) {
        case "ラ": return 10000; // ライオン
        case "ゾ": return 200;   // ぞう
        case "キ": return 200;   // きりん
        case "ひ": return 80;    // ひよこ
        case "ヒ": return 400;   // にわとり
        default: return 0;
	}
    }

// 手駒の価値
    private int getCapturedPieceValue(Piece piece) {
	switch (piece.getSymbol()) {
        case "ゾ": case "キ": return 200;
        case "ひ": return 50;
        case "ヒ": return 300;
        default: return 0;
	}
    }

    private PlayerType getOpponentPlayerType() {
        return (this.getPlayerType() == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
    }
}
