import java.util.ArrayList;
import java.util.List;

public class AlphaBeta extends Player {
    // 駒の基本価値
    private static final int LION_VALUE = 50000;
    private static final int ELEPHANT_VALUE = 800;
    private static final int GIRAFFE_VALUE = 800;
    private static final int CHICK_VALUE = 200;
    private static final int HEN_VALUE = 400;
    
    // 特別評価
    private static final int TRIAL_BONUS = 5000;
    private static final int CAPTURE_BONUS = 600;
    private static final int MOBILITY_BONUS = 50;
    private static final int SAFETY_BONUS = 100;
    private static final int RESTRICTION_BONUS = 30;
    private static final int MAX_DEPTH = 4;

    public AlphaBeta(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        List<int[]> moves = getAllPossibleMoves(game);
        if (moves.isEmpty()) return null;

        int[] bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        game.setSilentMode(true);

        for (int[] move : moves) {
            Game newGame = game.clone();
            applyMove(newGame, move);
            
            int moveValue = alphaBeta(newGame, MAX_DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (isTrialMove(newGame, move)) moveValue += TRIAL_BONUS;
            if (isCaptureMove(newGame, move)) moveValue += CAPTURE_BONUS;
            
            if (move[0] != -1) {
                Piece movedPiece = newGame.getBoard().getPiece(move[2], move[3]);
                if (!isUnderAttack(newGame.getBoard(), move[2], move[3], movedPiece.getOwner())) {
                    moveValue += SAFETY_BONUS;
                }
            }
            
            if (moveValue > bestValue) {
                bestValue = moveValue;
                bestMove = move;
            }
        }
        
        game.setSilentMode(false);
        return bestMove;
    }

    private int alphaBeta(Game game, int depth, int alpha, int beta, boolean isMaximizingPlayer) {
        if (depth == 0 || game.isGameOver() != null) {
            return evaluate(game, isMaximizingPlayer);
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

    private int evaluate(Game game, boolean isMaximizingPlayer) {
	int score = 0;
	Board board = game.getBoard();
	PlayerType player = isMaximizingPlayer ? getPlayerType() : getOpponentPlayer(getPlayerType());

	// ライオン安全性評価（最優先）
	score += evaluateLionSafety(board, player);
    
	// 駒数に基づく動的評価
	score += evaluateMaterial(board, player);
	score += evaluatePositionalAdvantage(board, player); // トライ評価含む
    
	// 副次的な評価要素
	score += evaluateMobility(board, player) * 0.5; // 比重を下げる
	score += evaluateRestrictOpponentMobility(board, player) * 0.5;
    
	return score;
    }

    // 駒の基本価値評価（修正版）
    private int evaluateMaterial(Board board, PlayerType player) {
	int total = 0;
	PlayerType opponent = (player == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
	int opponentPieceCount = countPieces(board, opponent);
    
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece piece = board.getPiece(r, c);
		if (piece != null) {
		    int value = getPieceValue(piece);
                
		    // 相手の駒が少ない場合、捕獲をより重視
		    if (piece.getOwner() == opponent && opponentPieceCount <= 3) {
			value *= 1.5;
		    }
                
		    if (isUnderAttack(board, r, c, piece.getOwner())) {
			value *= 0.7;
		    }
		    total += (piece.getOwner() == player) ? value : -value;
		}
	    }
	}
	return (int)(total * 0.3);
    }

    // ライオン安全性評価（新規追加）
    private int evaluateLionSafety(Board board, PlayerType player) {
        int score = 0;
        int[] myLionPos = findLionPosition(board, player);
        PlayerType opponent = (player == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        
        if (myLionPos != null) {
            if (isUnderAttack(board, myLionPos[0], myLionPos[1], player)) {
                score -= 5000;
            } else {
                score += 2000;
            }
        }
        
        int[] opponentLionPos = findLionPosition(board, opponent);
        if (opponentLionPos != null && canCaptureLion(board, player, opponentLionPos[0], opponentLionPos[1])) {
            score += 10000;
        }
        
        return score;
    }
    // 移動可能な手の評価（追加）
    private int evaluateMobility(Board board, PlayerType player) {
        int mobility = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    mobility += piece.getPossibleMoves(r, c, board).size() * MOBILITY_BONUS;
                }
            }
        }
        return mobility;
    }

    // 相手の移動制限評価（追加）
    private int evaluateRestrictOpponentMobility(Board board, PlayerType player) {
        int score = 0;
        PlayerType opponent = (player == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int r = 0; r < board.ROWS; r++) {
            for (int c = 0; c < board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponent) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    int originalMobility = possibleMoves.size();
                    int restrictedMobility = countRestrictedMoves(board, r, c, opponent);
                    score += (originalMobility - restrictedMobility) * RESTRICTION_BONUS;
                }
            }
        }
        return score;
    }

    // 制限された移動数をカウント（追加）
    private int countRestrictedMoves(Board board, int enemyRow, int enemyCol, PlayerType enemy) {
        Piece enemyPiece = board.getPiece(enemyRow, enemyCol);
        List<int[]> originalMoves = enemyPiece.getPossibleMoves(enemyRow, enemyCol, board);
        
        int restrictedCount = 0;
        for (int[] move : originalMoves) {
            int toRow = move[0], toCol = move[1];
            if (board.getPiece(toRow, toCol) != null && 
                board.getPiece(toRow, toCol).getOwner() != enemy) {
                restrictedCount++;
            }
        }
        return restrictedCount;
    }
    private int evaluatePositionalAdvantage(Board board, PlayerType player) {
	int score = 0;
	PlayerType opponent = (player == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
    
	// 駒数のカウント
	int myPieceCount = countPieces(board, player);
	int opponentPieceCount = countPieces(board, opponent);
	boolean isAdvantage = (myPieceCount > opponentPieceCount);

	// 自ライオンのトライ評価（有利状況でのみ高評価）
	int[] myLionPos = findLionPosition(board, player);
	if (myLionPos != null) {
	    int trialRow = (player == PlayerType.PLAYER1) ? board.ROWS - 1 : 0;
	    int distance = Math.abs(myLionPos[0] - trialRow);
        
	    // 基本評価（距離に反比例）
	    int trialScore = (board.ROWS - distance) * 50;
        
	    // 有利状況でのみトライを積極評価
	    if (isAdvantage) {
		trialScore *= 2; // 有利時に倍増
	    } else {
		trialScore *= 0.3; // 不利時は減衰
	    }
	    score += trialScore;
	}

	// 相手ライオンのトライ阻止（常に重要）
	int[] opponentLionPos = findLionPosition(board, opponent);
	if (opponentLionPos != null) {
	    int opponentTrialRow = (opponent == PlayerType.PLAYER1) ? board.ROWS - 1 : 0;
	    int opponentDistance = Math.abs(opponentLionPos[0] - opponentTrialRow);
	    score -= (board.ROWS - opponentDistance) * 100; // 相手トライは常に阻止
	}

	// 中央支配評価（変更なし）
	for (int r = 0; r < board.ROWS; r++) {
	    for (int c = 0; c < board.COLS; c++) {
		Piece piece = board.getPiece(r, c);
		if (piece != null && piece.getOwner() == player) {
		    if (isElephant(piece) || isGiraffe(piece)) {
			if (Math.abs(c - board.COLS / 2) <= 1) {
			    score += 30;
			}
		    }
		}
	    }
	}
    
	return score;
    }

    // 駒数をカウントするヘルパーメソッド（新規追加）
    private int countPieces(Board board, PlayerType player) {
	int count = 0;
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece piece = board.getPiece(r, c);
		if (piece != null && piece.getOwner() == player) {
		    count++;
		}
	    }
	}
	// 手駒も含めてカウント
	if (player == getPlayerType()) {
	    count += getCapturedPieces().size();
	}
	return count;
    }

    private boolean isUnderAttack(Board board, int row, int col, PlayerType owner) {
        PlayerType opponent = (owner == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece enemyPiece = board.getPiece(r, c);
                if (enemyPiece != null && enemyPiece.getOwner() == opponent) {
                    for (int[] move : enemyPiece.getPossibleMoves(r, c, board)) {
                        if (move[0] == row && move[1] == col) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean canCaptureLion(Board board, PlayerType player, int lionRow, int lionCol) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        if (move[0] == lionRow && move[1] == lionCol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isLion(Piece piece) {
        return piece.getSymbol().matches("獅|ラ");
    }
    
    private boolean isElephant(Piece piece) {
        return piece.getSymbol().matches("象|ゾ");
    }
    
    private boolean isGiraffe(Piece piece) {
        return piece.getSymbol().matches("麒|キ");
    }
    
    private boolean isChick(Piece piece) {
        return piece.getSymbol().matches("ひ|ヒ") && !piece.isPromoted();
    }
    
    private boolean isHen(Piece piece) {
        return piece.getSymbol().matches("ニ|鶏") || 
              (piece.getSymbol().matches("ひ|ヒ") && piece.isPromoted());
    }

    private int getPieceValue(Piece piece) {
        if (isLion(piece)) return LION_VALUE;
        if (isElephant(piece)) return ELEPHANT_VALUE;
        if (isGiraffe(piece)) return GIRAFFE_VALUE;
        if (isChick(piece)) return CHICK_VALUE;
        if (isHen(piece)) return HEN_VALUE;
        return 0;
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

    private boolean isTrialMove(Game game, int[] move) {
        if (move[0] == -1) return false;
        Piece movedPiece = game.getBoard().getPiece(move[2], move[3]);
        return isLion(movedPiece) && 
               ((getPlayerType() == PlayerType.PLAYER1 && move[2] == 3) || 
                (getPlayerType() == PlayerType.PLAYER2 && move[2] == 0));
    }

    private boolean isCaptureMove(Game game, int[] move) {
        if (move[0] == -1) return false;
        Piece target = game.getBoard().getPiece(move[2], move[3]);
        return target != null && target.getOwner() != getPlayerType();
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
