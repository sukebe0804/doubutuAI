import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AI_gj extends Player {

    private static final int MAX_DEPTH = 5; // 探索の深さ
    private static final int WIN_SCORE = 100000; // 勝利時の評価点
    private static final int LOSE_SCORE = -100000; // 敗北時の評価点

    // 駒の価値を設定（調整済み）
    private static final int HIYOKO_VALUE = 80;
    private static final int KIRIN_VALUE = 200;
    private static final int ZOU_VALUE = 200;
    private static final int LION_VALUE = 10000;
    private static final int NIWATORI_VALUE = 150; // 変更: 100 -> 150

    // 盤上の位置ごとの価値 (調整済み)
    // 盤面サイズ: 4x3
    // { {A1, A2, A3}, {B1, B2, B3}, {C1, C2, C3}, {D1, D2, D3} }
    // A列 (敵陣) D列 (自陣)
    // PLAYER1 (先手) 視点
    private static final int[][] BOARD_POSITION_VALUES_PLAYER1 = {
        {10, 25, 10}, // 変更: {10, 15, 10} -> {10, 25, 10} (最奥中央の価値を高く)
        { 5,  7,  5},
        { 2,  3,  2},
        { 1,  1,  1}  // 自陣
    };

    // PLAYER2 (後手) 視点 (PLAYER1の盤面を上下反転、調整済み)
    private static final int[][] BOARD_POSITION_VALUES_PLAYER2 = {
        { 1,  1,  1},  // 自陣
        { 2,  3,  2},
        { 5,  7,  5},
        {10, 25, 10}   // 変更: {10, 15, 10} -> {10, 25, 10} (最奥中央の価値を高く)
    };

    // 中央支配のボーナス（盤面の中央付近のマス）
    private static final int CENTER_CONTROL_BONUS = 2; // 駒一つあたり
    // 中央のマス (例: r=1,2)
    private static final int[][] CENTRAL_SQUARES = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 0}, {2, 1}, {2, 2}
    };

    // トライ関連の評価定数（調整済み）
    private static final int TRIAL_BONUS = 2000;  // 変更: 1500 -> 2000 (勝利時の評価点ボーナス)
    private static final int TRIAL_THREAT_BONUS = 750; // 変更: 500 -> 750 (トライ位置近くにいる場合のボーナス)

    // 危険性評価パラメータ
    private static final double DANGER_PENALTY_RATIO = 1.5;

    // ライオン接近ペナルティ
    private static final int LION_PROXIMITY_PENALTY = 100; // 調整可能

    // Randomオブジェクト
    private Random rand;


    public AI_gj(String name, PlayerType type) {
        super(name);
        this.setPlayerType(type);
        this.rand = new Random(); // コンストラクタで初期化
    }

    public AI_gj(String name) {
        super(name);
        this.rand = new Random(); // コンストラクタで初期化
    }


    @Override
    public int[] chooseMove(Game game) {
	Board currentBoard = game.getBoard().clone();
	List<Piece> myCapturedPieces = new ArrayList<>(this.getCapturedPieces());
    
	// 初手判定ロジック
	boolean isFirstTurn = myCapturedPieces.isEmpty();
	if (this.getPlayerType() == PlayerType.PLAYER1) {
	    isFirstTurn = isFirstTurn && game.getPlayerB().getCapturedPieces().isEmpty();
	} else {
	    isFirstTurn = isFirstTurn && game.getPlayerA().getCapturedPieces().isEmpty();
	}

	if (isFirstTurn) {
	    System.out.println(this.getName() + ": 初手なのでランダムな手を選択します。");
        
	    List<int[]> allPossibleFirstMoves = generateAllLegalMoves(currentBoard, myCapturedPieces, this.getPlayerType());
        
	    if (!allPossibleFirstMoves.isEmpty()) {
		int[] randomMove = allPossibleFirstMoves.get(rand.nextInt(allPossibleFirstMoves.size()));
		int moveType = randomMove[0];
		if (moveType == 0) { // 駒の移動
		    return new int[]{randomMove[1], randomMove[2], randomMove[3], randomMove[4]};
		} else { // 手駒を打つ
		    return new int[]{-1, randomMove[5], randomMove[3], randomMove[4]};
		}
	    } else {
		System.err.println(this.getName() + " は初手の合法手を見つけられませんでした。");
		return null;
	    }
	}
	// 初手判定ロジックここまで

	// 2手目以降の通常処理 (ミニマックス探索)
	List<Piece> opponentCapturedPieces = new ArrayList<>();

	if (this.getPlayerType() == PlayerType.PLAYER1) {
	    opponentCapturedPieces.addAll(game.getPlayerB().getCapturedPieces());
	} else {
	    opponentCapturedPieces.addAll(game.getPlayerA().getCapturedPieces());
	}

	List<int[]> internalAllPossibleMoves = new ArrayList<>();
	// Pieceの移動の合法手生成
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece piece = currentBoard.getPiece(r, c);
		if (piece != null && piece.getOwner() == this.getPlayerType()) {
		    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, currentBoard);
		    for (int[] move : movesForPiece) {
			Board tempBoard = currentBoard.clone();
			Piece pieceToMove = tempBoard.getPiece(r, c);
			Piece capturedInSim = tempBoard.getPiece(move[0], move[1]);

			tempBoard.removePiece(r, c);
			tempBoard.placePiece(pieceToMove, move[0], move[1]);
                    
			if (pieceToMove instanceof Hiyoko) {
			    Hiyoko hiyoko = (Hiyoko) pieceToMove;
			    if (!hiyoko.isPromoted() &&
				((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
				 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
				hiyoko.promote();
			    }
			}

			if (!isPlayerInCheckInternal(tempBoard, this.getPlayerType())) {
			    internalAllPossibleMoves.add(new int[]{0, r, c, move[0], move[1], -1});
			}

			// ボード戻す
			tempBoard.removePiece(move[0], move[1]);
			tempBoard.placePiece(pieceToMove, r, c);
			if (capturedInSim != null) {
			    tempBoard.placePiece(capturedInSim, move[0], move[1]);
			}
			if (pieceToMove instanceof Hiyoko && ((Hiyoko) pieceToMove).isPromoted() &&
			    ((pieceToMove.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
			     (pieceToMove.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
			    ((Hiyoko) pieceToMove).demote();
			}
		    }
		}
	    }
	}

	// 手駒を打つ合法手生成
	for (int i = 0; i < myCapturedPieces.size(); i++) {
	    Piece pieceToDrop = myCapturedPieces.get(i);
	    for (int r = 0; r < Board.ROWS; r++) {
		for (int c = 0; c < Board.COLS; c++) {
		    if (currentBoard.isEmpty(r, c)) {
			Board tempBoard = currentBoard.clone();
			tempBoard.placePiece(pieceToDrop, r, c);

			if (!isPlayerInCheckInternal(tempBoard, this.getPlayerType())) {
			    internalAllPossibleMoves.add(new int[]{1, -1, -1, r, c, i});
			}
			tempBoard.removePiece(r, c);
		    }
		}
	    }
	}

	if (internalAllPossibleMoves.isEmpty()) {
	    System.err.println(this.getName() + " は合法手を見つけられませんでした。投了します。");
	    return null;
	}

	// 駒の評価順にソート
	sortMoves(internalAllPossibleMoves, currentBoard, this.getPlayerType());

	List<int[]> bestMoves = new ArrayList<>();
	int bestScore = LOSE_SCORE - 1;
	int[] bestInternalMove = null;

	int alpha = LOSE_SCORE - 1;
	int beta = WIN_SCORE + 1;

	for (int[] move : internalAllPossibleMoves) {
	    Board nextBoard = currentBoard.clone();
	    List<Piece> nextMyCaptured = new ArrayList<>(myCapturedPieces);
	    List<Piece> nextOpponentCaptured = new ArrayList<>(opponentCapturedPieces);

	    Piece capturedPiece = applyMove(nextBoard, nextMyCaptured, nextOpponentCaptured, this.getPlayerType(), move);
	    if (capturedPiece != null) {
		capturedPiece.setOwner(this.getPlayerType());
		if (capturedPiece instanceof Hiyoko) {
		    ((Hiyoko) capturedPiece).demote();
		}
		nextMyCaptured.add(capturedPiece);
	    }

	    PlayerType nextPlayerType = (this.getPlayerType() == PlayerType.PLAYER1)
                ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

	    int score = minimax(nextBoard, nextOpponentCaptured, nextMyCaptured, nextPlayerType, MAX_DEPTH - 1, alpha, beta);

	    if (score > bestScore) {
		bestScore = score;
		bestMoves.clear();
		bestMoves.add(move);
	    } else if (score == bestScore) {
		bestMoves.add(move);
	    }
	    alpha = Math.max(alpha, score);
	}

	if (!bestMoves.isEmpty()) {
	    bestInternalMove = bestMoves.get(rand.nextInt(bestMoves.size()));
	} else {
	    System.err.println(this.getName() + " が最適な手を見つけられませんでした。最初の合法手を返します。");
	    bestInternalMove = internalAllPossibleMoves.get(0);
	}

	if (bestInternalMove != null) {
	    int moveType = bestInternalMove[0];
	    if (moveType == 0) {
		return new int[]{bestInternalMove[1], bestInternalMove[2], bestInternalMove[3], bestInternalMove[4]};
	    } else {
		return new int[]{-1, bestInternalMove[5], bestInternalMove[3], bestInternalMove[4]};
	    }
	}
	return null;
    }

    /**
     * ミニマックスアルゴリズムの実装
     * @param board 現在の盤面
     * @param currentPlayerCapturedPieces 現在の手番のプレイヤーの手駒
     * @param opponentCapturedPieces 相手の手番のプレイヤーの手駒
     * @param currentPlayerType 現在の手番のプレイヤータイプ
     * @param depth 探索の残り深さ
     * @param alpha アルファ値
     * @param beta ベータ値
     * @return 評価点
     */
    private int minimax(Board board, List<Piece> currentPlayerCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType, int depth, int alpha, int beta) {
        if (depth == 0 || isTerminal(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType)) {
            return evaluate(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType);
        }

        List<int[]> allPossibleMoves = new ArrayList<>();
        // 駒の移動の合法手生成
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == currentPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        Board tempBoard = board.clone();
                        Piece pieceToMove = tempBoard.getPiece(r, c);
                        Piece capturedInSim = tempBoard.getPiece(move[0], move[1]);

                        tempBoard.removePiece(r, c);
                        tempBoard.placePiece(pieceToMove, move[0], move[1]);
                        
                        if (pieceToMove instanceof Hiyoko) {
                            Hiyoko hiyoko = (Hiyoko) pieceToMove;
                            if (!hiyoko.isPromoted() &&
                                ((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                                 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                                hiyoko.promote();
                            }
                        }

                        if (!isPlayerInCheckInternal(tempBoard, currentPlayerType)) {
                            allPossibleMoves.add(new int[]{0, r, c, move[0], move[1], -1});
                        }
                        
                        tempBoard.removePiece(move[0], move[1]);
                        tempBoard.placePiece(pieceToMove, r, c);
                        if(capturedInSim != null) {
                            tempBoard.placePiece(capturedInSim, move[0], move[1]);
                        }
                        if (pieceToMove instanceof Hiyoko && ((Hiyoko)pieceToMove).isPromoted() &&
                            ((pieceToMove.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                             (pieceToMove.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                            ((Hiyoko)pieceToMove).demote();
                        }
                    }
                }
            }
        }
        for (int i = 0; i < currentPlayerCapturedPieces.size(); i++) {
            Piece pieceToDrop = currentPlayerCapturedPieces.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) {
                        Board tempBoard = board.clone();
                        tempBoard.placePiece(pieceToDrop, r, c);

                        if (!isPlayerInCheckInternal(tempBoard, currentPlayerType)) {
                            allPossibleMoves.add(new int[]{1, -1, -1, r, c, i});
                        }
                        tempBoard.removePiece(r, c);
                    }
                }
            }
        }

        if (allPossibleMoves.isEmpty()) {
            return (currentPlayerType == this.getPlayerType()) ? LOSE_SCORE : WIN_SCORE;
        }

        sortMoves(allPossibleMoves, board, currentPlayerType);

        if (currentPlayerType == this.getPlayerType()) {
            int maxEval = LOSE_SCORE - 1;
            for (int[] move : allPossibleMoves) {
                Board nextBoard = board.clone();
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType);
                    if (captured instanceof Hiyoko) {
                        ((Hiyoko) captured).demote();
                    }
                    nextMaxPlayerCaptured.add(captured);
                }
                
                int eval = minimax(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = WIN_SCORE + 1;
            for (int[] move : allPossibleMoves) {
                Board nextBoard = board.clone();
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType);
                    if (captured instanceof Hiyoko) {
                        ((Hiyoko) captured).demote();
                    }
                    nextMinPlayerCaptured.add(captured);
                }
                
                int eval = minimax(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private Piece applyMove(Board board, List<Piece> myCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType, int[] move) {
        int moveType = move[0];
        int fromR = move[1];
        int fromC = move[2];
        int toR = move[3];
        int toC = move[4];
        int capturedIdx = move[5];

        Piece capturedPiece = null;

        if (moveType == 0) {
            Piece pieceToMove = board.getPiece(fromR, fromC);
            capturedPiece = board.getPiece(toR, toC);
            
            board.removePiece(fromR, fromC);
            board.placePiece(pieceToMove, toR, toC);
            
            if (pieceToMove instanceof Hiyoko) {
                Hiyoko hiyoko = (Hiyoko) pieceToMove;
                if (!hiyoko.isPromoted() &&
                    ((hiyoko.getOwner() == PlayerType.PLAYER1 && toR == 0) ||
                     (hiyoko.getOwner() == PlayerType.PLAYER2 && toR == Board.ROWS - 1))) {
                    hiyoko.promote();
                }
            }
            return capturedPiece;
        } else if (moveType == 1) {
            Piece pieceToDrop = myCapturedPieces.remove(capturedIdx);
            board.placePiece(pieceToDrop, toR, toC);
            return null;
        }
        return null;
    }

    private boolean isPlayerInCheckInternal(Board board, PlayerType playerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) {
            return false; 
        }

        PlayerType opponentType = (playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponentType) {
                    List<int[]> possibleAttacks = piece.getPossibleMoves(r, c, board);
                    for (int[] attackMove : possibleAttacks) {
                        if (attackMove[0] == lionPos[0] && attackMove[1] == lionPos[1]) {
                            return true;
                        }
                    }
                }
            }
            }
        return false;
    }


    private boolean isTerminal(Board board, List<Piece> currentPlayerCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType) {
        if (findLion(board, currentPlayerType) == null) {
            return true;
        }
        if (findLion(board, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1) == null) {
            return true;
        }
        
        if (isTrialWin(board, currentPlayerType)) {
            return true;
        }
        if (isTrialWin(board, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1)) {
            return true;
        }
        
        return false;
    }

    private int[] findLion(Board board, PlayerType playerType) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece instanceof Lion && piece.getOwner() == playerType) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private boolean isTrialWin(Board board, PlayerType playerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) return false;

        if (playerType == PlayerType.PLAYER1) {
            return lionPos[0] == 0;
        } else {
            return lionPos[0] == Board.ROWS - 1;
        }
    }

    private boolean isSquareAttackedBy(Board board, int targetR, int targetC, PlayerType attackingPlayerType) {
    for (int r = 0; r < Board.ROWS; r++) {
        for (int c = 0; c < Board.COLS; c++) {
            Piece piece = board.getPiece(r, c);
            if (piece != null && piece.getOwner() == attackingPlayerType) {
                List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                for (int[] move : possibleMoves) {
                    if (move[0] == targetR && move[1] == targetC) {
                        return true;
                    }
                }
            }
        }
    }
    return false;
}
    
    /**
     * 盤面の評価関数
     * @param board 盤面
     * @param currentPlayerCapturedPieces 現在の手番のプレイヤーの手駒
     * @param opponentCapturedPieces 相手の手番のプレイヤーの手駒
     * @param evaluatePlayerType 評価対象のプレイヤータイプ
     * @return 評価点
     */
    
       private int evaluate(Board board, List<Piece> currentPlayerCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType evaluatePlayerType) {
        int[] myLionPos = findLion(board, evaluatePlayerType);
        int[] opponentLionPos = findLion(board, (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1);

        if (myLionPos == null) {
            return LOSE_SCORE;
        }
        if (opponentLionPos == null) {
            return WIN_SCORE;
        }
        
        if (isTrialWin(board, evaluatePlayerType)) {
            return WIN_SCORE;
        }
        if (isTrialWin(board, (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1)) {
            return LOSE_SCORE;
        }

        int score = 0;
        PlayerType opponentTypeForEval = (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionValue = (evaluatePlayerType == PlayerType.PLAYER1) ? BOARD_POSITION_VALUES_PLAYER1[r][c] : BOARD_POSITION_VALUES_PLAYER2[r][c];

                    int dangerPenalty = 0;
                    if (isSquareAttackedBy(board, r, c, opponentTypeForEval)) {
                        dangerPenalty = (int) (pieceValue * DANGER_PENALTY_RATIO);
                        if (piece instanceof Lion) {
                            dangerPenalty = 0;
                        }
                    }

                    int lionProximityPenalty = 0;
                    if (piece.getOwner() == evaluatePlayerType && opponentLionPos != null) {
                        int distance = Math.abs(r - opponentLionPos[0]) + Math.abs(c - opponentLionPos[1]);
                        if (distance <= 2) {
                            lionProximityPenalty = LION_PROXIMITY_PENALTY * (3 - distance);
                        }
                    }
                    
                    if (piece.getOwner() == evaluatePlayerType) {
                        score += pieceValue + positionValue - dangerPenalty - lionProximityPenalty;
                    } else {
                        score -= (pieceValue + positionValue) - dangerPenalty + lionProximityPenalty;
                    }
                }
            }
        }

        for (Piece piece : currentPlayerCapturedPieces) {
            score += getPieceValue(piece) / 2;
        }
        for (Piece piece : opponentCapturedPieces) {
            score -= getPieceValue(piece) / 2;
        }

        int myMobility = 0;
        int opponentMobility = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    List<int[]> moves = piece.getPossibleMoves(r, c, board);
                    if (piece.getOwner() == evaluatePlayerType) {
                        myMobility += moves.size();
                    } else {
                        opponentMobility += moves.size();
                    }
                }
            }
        }
        score += myMobility;
        score -= opponentMobility;

        if (isPlayerInCheckInternal(board, evaluatePlayerType)) {
            score -= 1000;
        }
        if (isPlayerInCheckInternal(board, opponentTypeForEval)) {
            score += 500;
        }

        if (myLionPos != null) {
            int safeSquares = 0;
            int dangerousSquares = 0;
            int[][] kingMoves = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1},           {0, 1},
                {1, -1}, {1, 0}, {1, 1}
            };

            for (int[] delta : kingMoves) {
                int r = myLionPos[0] + delta[0];
                int c = myLionPos[1] + delta[1];

                if (board.isValidCoordinate(r, c)) {
                    Piece pieceAtSquare = board.getPiece(r, c);
                    if (pieceAtSquare != null && pieceAtSquare.getOwner() == evaluatePlayerType) {
                        safeSquares++;
                    } else {
                        if (isSquareAttackedBy(board, r, c, opponentTypeForEval)) {
                            dangerousSquares++;
                        }
                    }
                }
            }
            score += safeSquares * 10;
            score -= dangerousSquares * 20;
        }

        for (int[] centralCoord : CENTRAL_SQUARES) {
            int r = centralCoord[0];
            int c = centralCoord[1];
            Piece piece = board.getPiece(r, c);
            if (piece != null) {
                if (piece.getOwner() == evaluatePlayerType) {
                    score += CENTER_CONTROL_BONUS * 2;
                } else {
                    score -= CENTER_CONTROL_BONUS * 2;
                }
            }
        }

        if (myLionPos != null) {
            if (evaluatePlayerType == PlayerType.PLAYER1) {
                int distToGoal = myLionPos[0];
                score += TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            } else {
                int distToGoal = Board.ROWS - 1 - myLionPos[0];
                score += TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            }
        }
        if (opponentLionPos != null) {
            if (opponentTypeForEval == PlayerType.PLAYER1) {
                int distToGoal = opponentLionPos[0];
                score -= TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            } else {
                int distToGoal = Board.ROWS - 1 - opponentLionPos[0];
                score -= TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            }
        }
        
        return score;
    }

    
    
    private int getPieceValue(Piece piece) {
        if (piece instanceof Hiyoko) {
            if (((Hiyoko) piece).isPromoted()) {
                return NIWATORI_VALUE;
            } else {
                return HIYOKO_VALUE;
            }
        } else if (piece instanceof Kirin) {
            return KIRIN_VALUE;
        } else if (piece instanceof Zou) {
            return ZOU_VALUE;
        } else if (piece instanceof Lion) {
            return LION_VALUE;
        }
        return 0;
    }

    private void sortMoves(List<int[]> moves, Board board, PlayerType playerType) {
        Collections.sort(moves, (move1, move2) -> {
            int score1 = 0;
            int score2 = 0;

            if (move1[0] == 0) {
                Piece target1 = board.getPiece(move1[3], move1[4]);
                if (target1 != null && target1.getOwner() != playerType) {
                    score1 += getPieceValue(target1);
                }
            }

            if (move2[0] == 0) {
                Piece target2 = board.getPiece(move2[3], move2[4]);
                if (target2 != null && target2.getOwner() != playerType) {
                    score2 += getPieceValue(target2);
                }
            }
            
            return Integer.compare(score2, score1);
        });
    }

    /**
     * 指定されたプレイヤーの全ての合法手を生成するヘルパーメソッド
     * @param board 盤面
     * @param capturedPieces プレイヤーの手駒リスト
     * @param playerType プレイヤータイプ
     * @return 全ての合法手のリスト
     */
    private List<int[]> generateAllLegalMoves(Board board, List<Piece> capturedPieces, PlayerType playerType) {
        List<int[]> allLegalMoves = new ArrayList<>();

        // 駒の移動の合法手生成
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        // 移動先の盤面をシミュレーション
                        Board tempBoard = board.clone();
                        Piece pieceToMove = tempBoard.getPiece(r, c);
                        Piece capturedInSim = tempBoard.getPiece(move[0], move[1]);

                        tempBoard.removePiece(r, c); // 元の場所から駒を削除
                        tempBoard.placePiece(pieceToMove, move[0], move[1]); // 新しい場所に配置
                        
                        // 成り判定
                        if (pieceToMove instanceof Hiyoko) {
                            Hiyoko hiyoko = (Hiyoko) pieceToMove;
                            if (!hiyoko.isPromoted() &&
                                ((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                                 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                                hiyoko.promote();
                            }
                        }

                        // 王手になっていないかチェック（合法手かどうかの判定）
                        if (!isPlayerInCheckInternal(tempBoard, playerType)) {
                            allLegalMoves.add(new int[]{0, r, c, move[0], move[1], -1}); // 移動: {0, fromR, fromC, toR, toC, -1}
                        }
                        
                        // シミュレーション後のボードを元の状態に戻す
                        tempBoard.removePiece(move[0], move[1]);
                        tempBoard.placePiece(pieceToMove, r, c);
                        if(capturedInSim != null) { // 捕獲された駒があれば元に戻す
                            tempBoard.placePiece(capturedInSim, move[0], move[1]);
                        }
                        // 成りを一時的に行った場合は元に戻す
                        if (pieceToMove instanceof Hiyoko && ((Hiyoko)pieceToMove).isPromoted() &&
                            ((pieceToMove.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                             (pieceToMove.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                            ((Hiyoko)pieceToMove).demote();
                        }
                    }
                }
            }
        }

        // 手駒を打つ処理の合法手生成
        for (int i = 0; i < capturedPieces.size(); i++) {
            Piece pieceToDrop = capturedPieces.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) { // 空いているマスにしか打てない
                        Board tempBoard = board.clone();
                        tempBoard.placePiece(pieceToDrop, r, c); // 一時的に駒を配置

                        // 王手になっていないかチェック（合法手かどうかの判定）
                        if (!isPlayerInCheckInternal(tempBoard, playerType)) {
                            allLegalMoves.add(new int[]{1, -1, -1, r, c, i}); // 打つ手: {1, -1, -1, dropR, dropC, capturedPieceIndex}
                        }
                        tempBoard.removePiece(r, c); // 配置した駒を元に戻す
                    }
                }
            }
        }
        return allLegalMoves;
    }
}
