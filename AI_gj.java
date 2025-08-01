import java.util.*;

public class AI_gj extends Player { // クラス名を AI_gj_Improved から AI_gj に変更
    // 定数: gj2.javaから導入
    private static final int MAX_DEPTH = 4; // 探索の深さ (AI_gjの動的深度調整とは異なる)
    private static final int WIN_SCORE = 100000; // 勝利時の評価点
    private static final int LOSE_SCORE = -100000; // 敗北時の評価点

    // 駒の価値を設定(AI_gjとgj2の調整を統合)
    private static final int HIYOKO_VALUE = 80;
    private static final int KIRIN_VALUE = 200;
    private static final int ZOU_VALUE = 200;
    private static final int LION_VALUE = 10000;
    private static final int NIWATORI_VALUE = 100; // にわとりの価値を上げる

    // 盤上の位置ごとの価値 (例: 中央を高く、端を低く) - gj2.javaから導入
    // 盤面サイズ: 4x3
    // { {A1, A2, A3}, {B1, B2, B3}, {C1, C2, C3}, {D1, D2, D3} }
    // A列 (敵陣) D列 (自陣)
    // PLAYER1 (先手) 視点
    private static final int[][] BOARD_POSITION_VALUES_PLAYER1 = {
        {10, 15, 10}, // 最奥 (敵陣): 中央を少し高く
        { 5,  7,  5},
        { 2,  3,  2},
        { 1,  1,  1}  // 自陣
    };

    // PLAYER2 (後手) 視点 (PLAYER1の盤面を上下反転)
    private static final int[][] BOARD_POSITION_VALUES_PLAYER2 = {
        { 1,  1,  1},  // 自陣
        { 2,  3,  2},
        { 5,  7,  5},
        {10, 15, 10}   // 最奥 (敵陣): 中央を少し高く
    };

    // 中央支配のボーナス（盤面の中央付近のマス）- gj2.javaから導入
    private static final int CENTER_CONTROL_BONUS = 2; // 駒一つあたり
    // 中央のマス (例: r=1,2)
    private static final int[][] CENTRAL_SQUARES = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 0}, {2, 1}, {2, 2}
    };

    // トライ関連の評価定数 (AlphaBeta.javaを参考に) - gj2.javaから導入
    private static final int TRIAL_BONUS = 1500;  // トライ成功時のボーナスを非常に高く設定
    private static final int TRIAL_THREAT_BONUS = 500; // トライ位置近くにいる場合のボーナス

    // 危険性評価パラメータ（必要に応じて調整）- gj2.javaから導入
    private static final double DANGER_PENALTY_RATIO = 1.5;

    // ライオン接近ペナルティ（AlphaBeta.javaから導入）- gj2.javaから導入
    private static final int LION_PROXIMITY_PENALTY = 100; // 調整可能

    // rand 変数を宣言・初期化 - gj2.javaから導入
    private static final Random rand = new Random();

    public AI_gj(String name) { // コンストラクタ名も AI_gj に変更
        super(name);
    }

    // gj2.javaのchooseMoveをベースに、AI_gjの動的深度調整も考慮しつつ統合
    @Override
    public int[] chooseMove(Game game) {
        this.setPlayerType(game.getCurrentPlayer().getPlayerType());
        Board currentBoard = game.getBoard().clone();
        List<Piece> myCapturedPieces = new ArrayList<>(this.getCapturedPieces());

        // internalAllPossibleMovesは、AI_gjの内部で使う6要素の形式で保持する
        List<int[]> internalAllPossibleMoves = generateAllLegalMoves(currentBoard, myCapturedPieces, this.playerType);

        // 初手判定ロジック (gj2.javaから導入)
        boolean isFirstTurn = myCapturedPieces.isEmpty() &&
                              (this.playerType == PlayerType.PLAYER1 ? game.getPlayerB().getCapturedPieces().isEmpty() : game.getPlayerA().getCapturedPieces().isEmpty());

        if (isFirstTurn && !internalAllPossibleMoves.isEmpty()) {
            System.out.println(this.getName() + ": 初手なのでランダムな手を選択します。");
            int[] randomMove = internalAllPossibleMoves.get(rand.nextInt(internalAllPossibleMoves.size()));
            int moveType = randomMove[0];
            if (moveType == 0) { // 移動
                return new int[]{randomMove[1], randomMove[2], randomMove[3], randomMove[4]};
            } else { // 打つ手
                return new int[]{-1, randomMove[5], randomMove[3], randomMove[4]};
            }
        }

        if (internalAllPossibleMoves.isEmpty()) {
            System.err.println(this.getName() + " は合法手を見つけられませんでした。投了します。");
            return null;
        }

        sortMoves(internalAllPossibleMoves, currentBoard, this.playerType);

        int bestScore = LOSE_SCORE - 1;
        int[] bestInternalMove = null;

        long timeLimit = System.nanoTime() + 1_000_000_000L; // 1秒
        int baseDepth = 1; // AI_gjの動的深度調整を再導入

	if (isPlayerInCheckInternal(currentBoard, this.playerType)) {
            baseDepth = 3;
        }

        if (internalAllPossibleMoves.size() <= 4) {
            baseDepth = Math.max(baseDepth, 3);
        }

        int evalScore = evaluate(currentBoard, myCapturedPieces,
            (this.playerType == PlayerType.PLAYER1 ? game.getPlayerB().getCapturedPieces()
                                                   : game.getPlayerA().getCapturedPieces()),
            this.playerType);
        if (evalScore < -1000) {
            baseDepth = Math.max(baseDepth, 4);
        }

        int depth = baseDepth;

	
        // 動的深度調整 (AI_gjのロジック)
        while (System.nanoTime() < timeLimit) {
            int currentBestValue = Integer.MIN_VALUE;
            int[] currentBest = null;
            int alpha = LOSE_SCORE - 1; // 深度ごとにalpha, betaをリセット
            int beta = WIN_SCORE + 1;

            for (int[] move : internalAllPossibleMoves) {
                if (System.nanoTime() >= timeLimit) break; // 時間切れチェック

                Board nextBoard = currentBoard.clone();
                List<Piece> nextMyCaptured = new ArrayList<>(myCapturedPieces);
                List<Piece> nextOpponentCaptured;
                if (this.playerType == PlayerType.PLAYER1) {
                    nextOpponentCaptured = new ArrayList<>(game.getPlayerB().getCapturedPieces());
                } else {
                    nextOpponentCaptured = new ArrayList<>(game.getPlayerA().getCapturedPieces());
                }

                Piece capturedPiece = applyMove(nextBoard, nextMyCaptured, nextOpponentCaptured, this.playerType, move);
                if (capturedPiece != null) {
                    capturedPiece.setOwner(this.playerType);
                    // Hiyokoのdemote()呼び出しを削除
                    nextMyCaptured.add(capturedPiece);
                }

                PlayerType nextPlayerType = (this.playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
                int value = minimax(nextBoard, nextOpponentCaptured, nextMyCaptured, nextPlayerType, depth - 1, alpha, beta, timeLimit);

                if (value > currentBestValue) {
                    currentBestValue = value;
                    currentBest = move;
                }
                alpha = Math.max(alpha, value);
            }

            if (System.nanoTime() < timeLimit && currentBest != null) {
                bestInternalMove = currentBest;
                bestScore = currentBestValue; // ここでスコアも更新
            }
            depth++;
        }

        if (bestInternalMove == null && !internalAllPossibleMoves.isEmpty()) {
            System.err.println(this.getName() + " が最適な手を見つけられませんでした。最初の合法手を返します。");
            bestInternalMove = internalAllPossibleMoves.get(0);
        }

        if (bestInternalMove != null) {
            int moveType = bestInternalMove[0];
            if (moveType == 0) { // 駒の移動
                return new int[]{bestInternalMove[1], bestInternalMove[2], bestInternalMove[3], bestInternalMove[4]};
            } else { // 手駒の打ち込み (moveType == 1)
                return new int[]{-1, bestInternalMove[5], bestInternalMove[3], bestInternalMove[4]};
            }
        }
        return null;
    }

    // gj2.javaのminimaxを導入。timeLimit引数を追加。
    private int minimax(Board board, List<Piece> currentPlayerCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType, int depth, int alpha, int beta, long timeLimit) {
        if (System.nanoTime() >= timeLimit) return 0; // 時間切れ

        if (depth == 0 || isTerminal(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType)) {
            return evaluate(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType);
        }

        List<int[]> allPossibleMoves = generateAllLegalMoves(board, currentPlayerCapturedPieces, currentPlayerType);

        if (allPossibleMoves.isEmpty()) {
            return (currentPlayerType == this.playerType) ? LOSE_SCORE : WIN_SCORE;
        }

        sortMoves(allPossibleMoves, board, currentPlayerType);

        if (currentPlayerType == this.playerType) { // 最大化プレイヤー (AI自身)
            int maxEval = LOSE_SCORE - 1;
            for (int[] move : allPossibleMoves) {
                if (System.nanoTime() >= timeLimit) return maxEval; // 時間切れ

                Board nextBoard = board.clone();
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType);
                    // Hiyokoのdemote()呼び出しを削除
                    nextMaxPlayerCaptured.add(captured);
                }

                int eval = minimax(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta, timeLimit);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else { // 最小化プレイヤー (相手)
            int minEval = WIN_SCORE + 1;
            for (int[] move : allPossibleMoves) {
                if (System.nanoTime() >= timeLimit) return minEval; // 時間切れ

                Board nextBoard = board.clone();
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType);
                    // Hiyokoのdemote()呼び出しを削除
                    nextMinPlayerCaptured.add(captured);
                }

                int eval = minimax(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta, timeLimit);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    // gj2.javaのapplyMoveを導入
    private Piece applyMove(Board board, List<Piece> myCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType, int[] move) {
        int moveType = move[0];
        int fromR = move[1];
        int fromC = move[2];
        int toR = move[3];
        int toC = move[4];
        int capturedIdx = move[5];

        Piece capturedPiece = null;

        if (moveType == 0) { // 移動
            Piece pieceToMove = board.getPiece(fromR, fromC);
            capturedPiece = board.getPiece(toR, toC);

            board.removePiece(fromR, fromC);
            board.placePiece(pieceToMove, toR, toC);

            // 昇格処理
            if (pieceToMove instanceof Hiyoko) {
                Hiyoko hiyoko = (Hiyoko) pieceToMove;
                if (!hiyoko.isPromoted() &&
                    ((hiyoko.getOwner() == PlayerType.PLAYER1 && toR == 0) ||
                     (hiyoko.getOwner() == PlayerType.PLAYER2 && toR == Board.ROWS - 1))) {
                    hiyoko.promote();
                }
            }
            return capturedPiece;
        } else if (moveType == 1) { // 打ち込み
            // myCapturedPiecesから実際に駒を削除する (複製されたリストに対して操作)
            Piece pieceToDrop = myCapturedPieces.remove(capturedIdx);
            board.placePiece(pieceToDrop, toR, toC);
            return null;
        }
        return null;
    }

    // gj2.javaのgenerateAllLegalMovesを導入
    private List<int[]> generateAllLegalMoves(Board board, List<Piece> capturedPieces, PlayerType playerType) {
        List<int[]> legalMoves = new ArrayList<>();

        // 盤上の駒の移動を生成
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        Board tempBoard = board.clone();
                        Piece pieceToMove = tempBoard.getPiece(r, c);
                        Piece capturedInSim = tempBoard.getPiece(move[0], move[1]); // シミュレーションで捕獲される駒

                        tempBoard.removePiece(r, c);
                        tempBoard.placePiece(pieceToMove, move[0], move[1]);

                        // 成り判定（シミュレーションボード上での一時的な処理）
                        boolean promotedInSim = false;
                        if (pieceToMove instanceof Hiyoko) {
                            Hiyoko hiyoko = (Hiyoko) pieceToMove;
                            if (!hiyoko.isPromoted() &&
                                ((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                                 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                                hiyoko.promote();
                                promotedInSim = true;
                            }
                        }

                        if (!isPlayerInCheckInternal(tempBoard, playerType)) {
                            legalMoves.add(new int[]{0, r, c, move[0], move[1], -1});
                        }

                        // tempBoardを元の状態に戻す (重要)
                        tempBoard.removePiece(move[0], move[1]);
                        tempBoard.placePiece(pieceToMove, r, c);
                        if(capturedInSim != null) {
                            tempBoard.placePiece(capturedInSim, move[0], move[1]);
                        }
                        // Hiyokoのdemote()呼び出しを削除
                    }
                }
            }
        }

        // 手駒の打ち込みを生成
        for (int i = 0; i < capturedPieces.size(); i++) {
            Piece pieceToDrop = capturedPieces.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) {
                        Board tempBoard = board.clone();
                        tempBoard.placePiece(pieceToDrop, r, c);

                        if (!isPlayerInCheckInternal(tempBoard, playerType)) {
                            legalMoves.add(new int[]{1, -1, -1, r, c, i});
                        }
                        tempBoard.removePiece(r, c);
                    }
                }
            }
        }
        return legalMoves;
    }

    // gj2.javaのisPlayerInCheckInternalを導入
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

    // gj2.javaのisTerminalを導入
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

    // gj2.javaのfindLionを導入
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

    // gj2.javaのisTrialWinを導入
    private boolean isTrialWin(Board board, PlayerType playerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) return false;

        if (playerType == PlayerType.PLAYER1) {
            return lionPos[0] == 0;
        } else {
            return lionPos[0] == Board.ROWS - 1;
        }
    }

    // gj2.javaのevaluateを導入 (AI_gjのevaluateBoardを置き換え)
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

        // 盤上の駒の価値 + 位置の価値 + 危険性評価 + ライオン接近ペナルティ
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionValue = (evaluatePlayerType == PlayerType.PLAYER1) ? BOARD_POSITION_VALUES_PLAYER1[r][c] : BOARD_POSITION_VALUES_PLAYER2[r][c];

                    int dangerPenalty = 0;
                    if (isSquareAttackedBy(board, r, c, opponentTypeForEval)) {
                        dangerPenalty = (int) (pieceValue * DANGER_PENALTY_RATIO);
                        if (piece instanceof Lion) { // ライオンは王手判定で処理済みなので除外
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

        // 手駒の価値
        for (Piece piece : currentPlayerCapturedPieces) {
            score += getPieceValue(piece) / 2;
        }
        for (Piece piece : opponentCapturedPieces) {
            score -= getPieceValue(piece) / 2;
        }

        // 駒の活動度 (Mobility)
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

        // 王手のリスク評価
        if (isPlayerInCheckInternal(board, evaluatePlayerType)) {
            score -= 1000;
        }
        if (isPlayerInCheckInternal(board, opponentTypeForEval)) {
            score += 500;
        }

        // ライオンの安全性評価の強化
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

        // 中央支配の評価
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

        // トライ接近の評価
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

    // gj2.javaのisSquareAttackedByを導入
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

    // gj2.javaのgetPieceValueを導入 (Hiyokoのpromoted状態を考慮)
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

    // gj2.javaのsortMovesを導入
    private void sortMoves(List<int[]> moves, Board board, PlayerType playerType) {
        Collections.sort(moves, (move1, move2) -> {
            int score1 = 0;
            int score2 = 0;

            if (move1[0] == 0) { // 駒の移動
                Piece target1 = board.getPiece(move1[3], move1[4]);
                if (target1 != null && target1.getOwner() != playerType) {
                    score1 += getPieceValue(target1);
                }
            }

            if (move2[0] == 0) { // 駒の移動
                Piece target2 = board.getPiece(move2[3], move2[4]);
                if (target2 != null && target2.getOwner() != playerType) {
                    score2 += getPieceValue(target2);
                }
            }

            return Integer.compare(score2, score1);
        });
    }
}
