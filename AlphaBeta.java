// AlphaBeta.java の簡素化バージョン

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
public class AlphaBeta extends Player {
    // 駒の基本価値 (シンプルな値に)
    private static final int LION_VALUE = 100000;
    private static final int ELEPHANT_VALUE = 800;
    private static final int GIRAFFE_VALUE = 800;
    private static final int CHICK_VALUE = 200;
    private static final int HEN_VALUE = 400;
    
    // 評価パラメータ (必要最小限に)
    private static final int CAPTURE_BONUS = 2500;
    private static final int SAFETY_BONUS = 200;
    private static final int MAX_DEPTH = 4;

    public AlphaBeta(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
	List<int[]> allMoves = getAllPossibleMoves(game);
	List<int[]> legalMoves = new ArrayList<>();
	
	boolean inCheck = game.isKingInCheck(getPlayerType());
	
	for (int[] move : allMoves) {
	    Game simulatedGame = game.clone();
	    applyMove(simulatedGame, move);
	    if (!simulatedGame.isKingInCheck(getPlayerType())) {
		legalMoves.add(move);
	    }
	}

	// 王手中なら、王手を回避できる手だけを使う
	List<int[]> moves = inCheck ? legalMoves : allMoves;

        int[] bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        
        game.setSilentMode(true);
        for (int[] move : moves) {
            Game newGame = game.clone();
            applyMove(newGame, move);
            
            // 評価はシンプルに1つのメソッドで行う
            int moveValue = evaluateMove(newGame, move);
            moveValue += alphaBeta(newGame, MAX_DEPTH-1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (moveValue > bestValue) {
                bestValue = moveValue;
                bestMove = move;
            }
        }
        game.setSilentMode(false);
        return bestMove;
    }

    // 主要な評価を1つのメソッドに統合
    private int evaluateMove(Game game, int[] move) {
        int score = 0;
        Board board = game.getBoard();
        
        // 1. 駒取り評価
        if (isCaptureMove(game, move)) {
	    Piece target = board.getPiece(move[2], move[3]);
	    Piece attacker = board.getPiece(move[0], move[1]); // 自分の駒

	    int targetValue = getPieceValue(target);
	    int attackerValue = getPieceValue(attacker);

	    int tradeGain = targetValue - attackerValue;
	    score += tradeGain; // 良い交換ならプラス、悪い交換ならマイナス評価

	    if (!isUnderAttack(board, move[2], move[3], getPlayerType())) {
		score += CAPTURE_BONUS; // 安全ならボーナスも加算
	    }
	}

        
        // 2. ライオン関連評価
        if (move[0] != -1 && isLion(board.getPiece(move[2], move[3]))) {
            score += evaluateLionSafety(board, move[2], move[3]);
        }
        return score;
    }

    // ライオン安全性評価を簡潔に
    private int evaluateLionMove(Board board, int fromRow, int fromCol, 
				 int toRow, int toCol, PlayerType player) {
	// 安全でない移動は即座に却下
	if (isUnderAttack(board, toRow, toCol, player)) {
	    return -50000;
	}
    
	// 敵ライオンに近づきすぎないかチェック
	int[] enemyLionPos = findLionPosition(board, getOpponentPlayer(player));
	if (enemyLionPos != null) {
	    int dist = Math.abs(toRow - enemyLionPos[0]) + Math.abs(toCol - enemyLionPos[1]);
	    if (dist <= 1) return -20000;
	}
    
	// 基本の安全移動ボーナスを大幅削減 (500→50)
	return 50;
    }

    // alphaBetaメソッドは変更なし (必要最小限の実装)
    private int alphaBeta(Game game, int depth, int alpha, int beta, boolean isMaximizingPlayer) {
        if (depth == 0 || game.isGameOver() != null) {
            return evaluateBoard(game.getBoard(), isMaximizingPlayer);
        }

        List<int[]> moves = getAllPossibleMoves(game);
        
        if (isMaximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                int eval = alphaBeta(newGame, depth-1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                int eval = alphaBeta(newGame, depth-1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    // 盤面評価をシンプルに
    private int evaluateBoard(Board board, boolean isMaximizingPlayer) {
        PlayerType player = isMaximizingPlayer ? getPlayerType() : getOpponentPlayer(getPlayerType());
        int score = 0;
        
        // 駒の価値合計
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    score += (piece.getOwner() == player) ? getPieceValue(piece) : -getPieceValue(piece);
                }
            }
        }
        
        // ライオン安全性チェック
        int[] lionPos = findLionPosition(board, player);
        if (lionPos != null) {
            score += evaluateLionSafety(board, lionPos[0], lionPos[1]);
        }
        
        return score;
    }
    private int evaluateLionSafety(Board board, int row, int col) {
        if (isUnderAttack(board, row, col, getPlayerType())) {
            return -50000; // 危険な位置には最大ペナルティ
        }
        return SAFETY_BONUS; // 安全な位置にはボーナス
    }

    // 以下は簡素化したヘルパーメソッド群
    private boolean isUnderAttack(Board board, int row, int col, PlayerType owner) {
        PlayerType opponent = getOpponentPlayer(owner);
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponent) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        if (move[0] == row && move[1] == col) return true;
                    }
                }
            }
        }
        return false;
    }

    private int[] findLionPosition(Board board, PlayerType player) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && isLion(piece) && piece.getOwner() == player) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    // 駒タイプ判定 (簡潔に)
    private boolean isLion(Piece piece) { return piece.getSymbol().matches("獅|ラ"); }
    private boolean isElephant(Piece piece) { return piece.getSymbol().matches("象|ゾ"); }
    private boolean isGiraffe(Piece piece) { return piece.getSymbol().matches("麒|キ"); }
    private boolean isChick(Piece piece) { return piece.getSymbol().matches("ひ|ヒ") && !piece.isPromoted(); }
    private boolean isHen(Piece piece) { return piece.getSymbol().matches("ニ|鶏") || (piece.getSymbol().matches("ひ|ヒ") && piece.isPromoted()); }

    private boolean isCaptureMove(Game game, int[] move) {
	Board board = game.getBoard();
	Piece target = board.getPiece(move[2], move[3]);
	return target != null && target.getOwner() != getPlayerType();
    }
    private int getPieceValue(Piece piece) {
	if (isLion(piece)) return LION_VALUE;
	if (isElephant(piece)) return ELEPHANT_VALUE;
	if (isGiraffe(piece)) return GIRAFFE_VALUE;
	if (isChick(piece)) return CHICK_VALUE;
	if (isHen(piece)) return HEN_VALUE;
	return 0;
    }


    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> moves = new ArrayList<>();
        Board board = game.getBoard();
        PlayerType current = getPlayerType();
        
        // 盤上の駒の移動
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == current) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        moves.add(new int[]{r, c, move[0], move[1]});
                    }
                }
            }
        }
        
        // 手駒を打つ手
        List<Piece> captured = getCapturedPieces();
        for (int i = 0; i < captured.size(); i++) {
            Piece piece = captured.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) {
                        moves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        
        return moves;
    }

    private void applyMove(Game game, int[] move) {
        if (move[0] == -1) {
            game.performDrop(getCapturedPieces().get(move[1]), move[2], move[3]);
        } else {
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }

    private PlayerType getOpponentPlayer(PlayerType player) {
        return player == PlayerType.PLAYER1 ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
    }
}
