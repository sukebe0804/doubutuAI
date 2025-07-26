import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MinMax extends Player {
    // 探索の深さ
    private static final int MAX_DEPTH = 10; 

    // 駒の基本価値
    private static final int LION_VALUE = 10000; // ライオンの価値を高く設定（詰み/トライはこれよりはるかに高い）
    private static final int KIRIN_VALUE = 300;
    private static final int ZOU_VALUE = 300;
    private static final int HIYOKO_VALUE = 100;
    private static final int NIWATORI_VALUE = 500; // 成ったひよこの価値を高く設定

    // 評価定数
    private static final int LION_SAFETY_BONUS_PER_DEFENDER = 50; // ライオンを守る味方駒1つあたりのボーナスを強化
    private static final int LION_ATTACK_PENALTY_PER_ATTACKER = 50; // ライオンを狙う敵駒1つあたりのペナルティを強化
    private static final int CENTER_CONTROL_BONUS = 10; // 中央のマスを支配する駒へのボーナスを強化
    private static final int MOBILITY_BONUS_PER_MOVE = 2; // 1手あたりのモビリティボーナスを強化
    private static final int ATTACKED_OWN_PIECE_PENALTY = 30; // 自分の駒が攻撃されている場合のペナルティを強化
    private static final int ATTACKED_OPPONENT_PIECE_BONUS = 30; // 相手の駒を攻撃している場合のボーナスを強化
    private static final int DEFENDED_OWN_PIECE_BONUS = 20; // 自分の駒が守られている場合のボーナス (ライオン以外) を強化
    private static final int PROMOTION_THREAT_BONUS = 100; // ひよこが敵陣最奥にいる場合のボーナス

    // 駒の配置点テーブル (Piece-Square Tables) - 調整済み
    // PLAYER1 (CPU) 視点での価値。PLAYER2の駒の場合は、行を反転して使用する。
    // (Row: 0=自陣奥 (PLAYER2の最前線), 3=敵陣奥 (PLAYER1の最前線))
    private static final int[][] PST_HIYOKO = {
        {0, 0, 0},    // Row 0
        {10, 20, 10}, // Row 1 (中央に近づく、前進)
        {30, 40, 30}, // Row 2 (敵陣に近い)
        {50, 60, 50}  // Row 3 (敵陣奥、成れるマス)
    };

    private static final int[][] PST_NIWATORI = {
        {30, 40, 30}, // Row 0
        {20, 50, 20}, // Row 1 (中央)
        {20, 50, 20}, // Row 2 (中央)
        {30, 40, 30}  // Row 3
    };

    private static final int[][] PST_KIRIN = {
        {10, 20, 10}, // Row 0
        {5, 10, 5},   // Row 1
        {5, 10, 5},   // Row 2
        {10, 20, 10}  // Row 3
    };

    private static final int[][] PST_ZOU = {
        {10, 5, 10},  // Row 0
        {5, 20, 5},   // Row 1
        {5, 20, 5},   // Row 2
        {10, 5, 10}   // Row 3
    };

    private static final int[][] PST_LION = {
        {-100, -100, -100}, // Row 0 (敵陣奥は危険)
        {-50, -20, -50},  // Row 1
        {0, 20, 0},     // Row 2 (やや安全な自陣)
        {50, 100, 50}   // Row 3 (自陣奥、初期位置付近)
    };

    private Random random; // 同じ評価値の時にランダムに選択するため

    public MinMax(String name) {
        super(name);
        this.random = new Random();
    }

    @Override
    public int[] chooseMove(Game game) {
        // 最善の手と評価値を初期化
        int bestScore = (this.playerType == PlayerType.PLAYER1) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] bestMove = null;

        // すべての可能な手を生成（自殺手は除外されている想定）
        List<int[]> possibleMoves = generateAllPossibleMoves(game);

        // 手の選択をランダムにするためのリスト（同じ評価値を持つ手を入れる）
        List<int[]> equallyGoodMoves = new ArrayList<>();

        if (possibleMoves.isEmpty()) {
            return null; // 動かせる手も打てる手駒もない
        }

        // アルファベータ法のための初期値
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // 各手をシミュレートし、ミニマックス法で評価
        for (int[] move : possibleMoves) {
            // ゲームの状態をコピーしてシミュレーション
            Game simulatedGame = game.clone();
            simulatedGame.setSilentMode(true); // サイレントモードにする

            // クローンされたゲームで手を実行
            boolean moveSuccessful = false;
            if (move.length == 4) { // 駒の移動 {fromRow, fromCol, toRow, toCol}
                int fromRow = move[0];
                int fromCol = move[1];
                int toRow = move[2];
                int toCol = move[3];
                Piece pieceToMove = simulatedGame.getBoard().getPiece(fromRow, fromCol);
                if (pieceToMove != null && pieceToMove.getOwner() == this.playerType) {
                    simulatedGame.makeMove(fromRow, fromCol, toRow, toCol); // Game.javaにmakeMoveを追加
                    moveSuccessful = true;
                }
            } else if (move.length == 3 && move[0] == -1) { // 手駒を打つ { -1, capturedPieceIndex, toRow, toCol }
                int capturedPieceIndex = move[1];
                int toRow = move[2];
                int toCol = move[3];
                Player currentSimulatedPlayer = (this.playerType == simulatedGame.getPlayerA().getPlayerType()) ? simulatedGame.getPlayerA() : simulatedGame.getPlayerB();
                if (capturedPieceIndex >= 0 && capturedPieceIndex < currentSimulatedPlayer.getCapturedPieces().size()) {
                    Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(capturedPieceIndex);
                    simulatedGame.makeDrop(pieceToDrop, toRow, toCol); // Game.javaにmakeDropを追加
                    moveSuccessful = true;
                }
            }

            // 手が成功した場合のみ評価
            if (moveSuccessful) {
                // プレイヤーを切り替える前に評価するため、minimax関数に渡す前に切り替える
                simulatedGame.switchPlayer();

                int score = minimax(simulatedGame, MAX_DEPTH - 1, alpha, beta, simulatedGame.getCurrentPlayer().getPlayerType());

                if (this.playerType == PlayerType.PLAYER1) { // プレイヤー1 (Maximizing Player)
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = move;
                        equallyGoodMoves.clear();
                        equallyGoodMoves.add(move);
                    } else if (score == bestScore) {
                        equallyGoodMoves.add(move);
                    }
                    alpha = Math.max(alpha, bestScore); // α値を更新
                } else { // プレイヤー2 (Minimizing Player)
                    if (score < bestScore) {
                        bestScore = score;
                        bestMove = move;
                        equallyGoodMoves.clear();
                        equallyGoodMoves.add(move);
                    } else if (score == bestScore) {
                        equallyGoodMoves.add(move);
                    }
                    beta = Math.min(beta, bestScore); // β値を更新
                }

                // アルファベータカットオフ
                if (beta <= alpha) {
                    break; // この枝は探索する必要がない
                }
            }
        }

        // 最善の手が複数ある場合はランダムに選択
        if (!equallyGoodMoves.isEmpty()) {
            return equallyGoodMoves.get(random.nextInt(equallyGoodMoves.size()));
        }

        return bestMove; // ここに到達することは稀だが、安全策
    }

    /**
     * ミニマックス法（アルファベータ枝刈り付き）
     *
     * @param game          現在のゲーム状態
     * @param depth         現在の探索深さ
     * @param alpha         アルファ値（Maximizing Playerが見つけた最良のスコア）
     * @param beta          ベータ値（Minimizing Playerが見つけた最良のスコア）
     * @param currentPlayerType 評価するプレイヤーのタイプ (Maximizing Player)
     * @return 現在の盤面における評価値
     */
    private int minimax(Game game, int depth, int alpha, int beta, PlayerType currentPlayerType) {
        PlayerType gameResult = game.isGameOver();
        if (gameResult != null) {
            if (gameResult == this.playerType) {
                // 自分が勝つ場合 (チェックメイト、トライなど)
                return Integer.MAX_VALUE - (MAX_DEPTH - depth); // 深さが浅いほど高い評価
            } else if (gameResult != this.playerType) {
                // 相手が勝つ場合
                return Integer.MIN_VALUE + (MAX_DEPTH - depth); // 深さが浅いほど低い評価
            }
        }

        if (depth == 0) {
            return evaluate(game); // 探索深さに到達したら盤面を評価
        }

        // 手を生成するプレイヤーは、現在のPlayerではなく、minimaxを呼び出した次のPlayer
        PlayerType playerToMove = game.getCurrentPlayer().getPlayerType();

        List<int[]> possibleMoves = generateAllPossibleMoves(game);

        if (possibleMoves.isEmpty()) { // 合法手がない場合、詰みや行き詰まりなので現在の評価を返す (Game.isGameOverで処理済みの場合もある)
             return evaluate(game);
        }

        if (playerToMove == this.playerType) { // Maximizing Player (自分)
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                newGame.setSilentMode(true); // サイレントモードにする

                // 手を適用
                boolean moveSuccessful = false;
                if (move.length == 4) {
                    int fromRow = move[0];
                    int fromCol = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    Piece pieceToMove = newGame.getBoard().getPiece(fromRow, fromCol);
                    if (pieceToMove != null && pieceToMove.getOwner() == playerToMove) { // nullチェックと所有者チェック
                        newGame.makeMove(fromRow, fromCol, toRow, toCol); // Game.javaにmakeMoveを追加
                        moveSuccessful = true;
                    }
                } else if (move.length == 3 && move[0] == -1) {
                    int capturedPieceIndex = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    Player currentSimulatedPlayer = (playerToMove == newGame.getPlayerA().getPlayerType()) ? newGame.getPlayerA() : newGame.getPlayerB();
                    if (capturedPieceIndex >= 0 && capturedPieceIndex < currentSimulatedPlayer.getCapturedPieces().size()) {
                        Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(capturedPieceIndex);
                        newGame.makeDrop(pieceToDrop, toRow, toCol); // Game.javaにmakeDropを追加
                        moveSuccessful = true;
                    }
                }

                if (moveSuccessful) {
                    newGame.switchPlayer(); // 次のプレイヤーの番に切り替える
                    int eval = minimax(newGame, depth - 1, alpha, beta, newGame.getCurrentPlayer().getPlayerType());
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) {
                        break; // βカットオフ
                    }
                }
            }
            return maxEval;
        } else { // Minimizing Player (相手)
            int minEval = Integer.MAX_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                newGame.setSilentMode(true); // サイレントモードにする

                // 手を適用
                boolean moveSuccessful = false;
                if (move.length == 4) {
                    int fromRow = move[0];
                    int fromCol = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    Piece pieceToMove = newGame.getBoard().getPiece(fromRow, fromCol);
                    if (pieceToMove != null && pieceToMove.getOwner() == playerToMove) { // nullチェックと所有者チェック
                        newGame.makeMove(fromRow, fromCol, toRow, toCol); // Game.javaにmakeMoveを追加
                        moveSuccessful = true;
                    }
                } else if (move.length == 3 && move[0] == -1) {
                    int capturedPieceIndex = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    Player currentSimulatedPlayer = (playerToMove == newGame.getPlayerA().getPlayerType()) ? newGame.getPlayerA() : newGame.getPlayerB();
                    if (capturedPieceIndex >= 0 && capturedPieceIndex < currentSimulatedPlayer.getCapturedPieces().size()) {
                        Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(capturedPieceIndex);
                        newGame.makeDrop(pieceToDrop, toRow, toCol); // Game.javaにmakeDropを追加
                        moveSuccessful = true;
                    }
                }

                if (moveSuccessful) {
                    newGame.switchPlayer(); // 次のプレイヤーの番に切り替える
                    int eval = minimax(newGame, depth - 1, alpha, beta, newGame.getCurrentPlayer().getPlayerType());
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) {
                        break; // αカットオフ
                    }
                }
            }
            return minEval;
        }
    }

    /**
     * 現在の盤面を評価する
     *
     * @param game 現在のゲーム状態
     * @return 盤面の評価値
     */
    private int evaluate(Game game) {
        int score = 0;
        Board board = game.getBoard();

        PlayerType myPlayerType = this.playerType;
        PlayerType opponentPlayerType = (myPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // 1. 駒の基本価値と配置点テーブル (PST) による評価
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = 0;
                    int pstValue = 0;
                    int actualRow = r; // PLAYER1の場合
                    if (piece.getOwner() == PlayerType.PLAYER2) {
                        actualRow = Board.ROWS - 1 - r; // PLAYER2の場合、行を反転
                    }

                    if (piece instanceof Lion) {
                        pieceValue = LION_VALUE;
                        pstValue = PST_LION[actualRow][c];
                    } else if (piece instanceof Kirin) {
                        pieceValue = KIRIN_VALUE;
                        pstValue = PST_KIRIN[actualRow][c];
                    } else if (piece instanceof Zou) {
                        pieceValue = ZOU_VALUE;
                        pstValue = PST_ZOU[actualRow][c];
                    } else if (piece instanceof Hiyoko) {
                        if (piece.isPromoted()) {
                            pieceValue = NIWATORI_VALUE;
                            pstValue = PST_NIWATORI[actualRow][c];
                        } else {
                            pieceValue = HIYOKO_VALUE;
                            pstValue = PST_HIYOKO[actualRow][c];
                            // ひよこが敵陣最奥にいる場合のボーナス
                            if (myPlayerType == PlayerType.PLAYER1 && r == Board.ROWS - 1) { // Player1が敵陣最奥 (Row 3)
                                score += PROMOTION_THREAT_BONUS;
                            } else if (myPlayerType == PlayerType.PLAYER2 && r == 0) { // Player2が敵陣最奥 (Row 0)
                                score += PROMOTION_THREAT_BONUS;
                            }
                        }
                    }

                    if (piece.getOwner() == myPlayerType) {
                        score += (pieceValue + pstValue);
                    } else {
                        score -= (pieceValue + pstValue);
                    }
                }
            }
        }

        // 2. 手駒の価値
        // MinMaxクラスのインスタンスが持っている手駒リストを使用
        Player myPlayerInstance = (myPlayerType == game.getPlayerA().getPlayerType()) ? game.getPlayerA() : game.getPlayerB();
        Player opponentPlayerInstance = (opponentPlayerType == game.getPlayerA().getPlayerType()) ? game.getPlayerA() : game.getPlayerB();

        for (Piece p : myPlayerInstance.getCapturedPieces()) { // 自分の手駒
            if (p instanceof Lion) score += LION_VALUE; // Lionは手駒にならないはずだが、念のため
            else if (p instanceof Kirin) score += KIRIN_VALUE;
            else if (p instanceof Zou) score += ZOU_VALUE;
            else if (p instanceof Hiyoko) score += HIYOKO_VALUE;
        }
        for (Piece p : opponentPlayerInstance.getCapturedPieces()) { // 相手の手駒
            if (p instanceof Lion) score -= LION_VALUE;
            else if (p instanceof Kirin) score -= KIRIN_VALUE;
            else if (p instanceof Zou) score -= ZOU_VALUE;
            else if (p instanceof Hiyoko) score -= HIYOKO_VALUE;
        }

        // 3. ライオンの安全度
        int[] myLionPos = findLionPosition(board, myPlayerType);
        int[] opponentLionPos = findLionPosition(board, opponentPlayerType);

        if (myLionPos != null) {
            // 自分のライオンが攻撃されているか
            int attackers = countAttackers(myLionPos[0], myLionPos[1], opponentPlayerType, board);
            score -= (attackers * LION_ATTACK_PENALTY_PER_ATTACKER);

            // 自分のライオンが守られているか
            int defenders = countDefenders(myLionPos[0], myLionPos[1], myPlayerType, board);
            score += (defenders * LION_SAFETY_BONUS_PER_DEFENDER);
        }

        if (opponentLionPos != null) {
            // 相手のライオンが攻撃されているか (自分から見て攻撃)
            int attackers = countAttackers(opponentLionPos[0], opponentLionPos[1], myPlayerType, board);
            score += (attackers * LION_ATTACK_PENALTY_PER_ATTACKER); // 相手のライオンへの攻撃は自分にとってプラス

            // 相手のライオンが守られているか (自分から見て防御)
            int defenders = countDefenders(opponentLionPos[0], opponentLionPos[1], opponentPlayerType, board);
            score -= (defenders * LION_SAFETY_BONUS_PER_DEFENDER); // 相手のライオンが守られているのは自分にとってマイナス
        }

        // 4. 中央支配 (Board.ROWS/2, Board.COLS/2 は中央付近のマス)
        // 動物将棋の盤面は小さいため、中央の概念を少し広げる
        // 実際の中央のマス (1,1) や (2,1)
        int[][] centralSquares = {{1, 1}, {2, 1}};
        for (int[] sq : centralSquares) {
            Piece p = board.getPiece(sq[0], sq[1]);
            if (p != null) {
                if (p.getOwner() == myPlayerType) {
                    score += CENTER_CONTROL_BONUS;
                } else {
                    score -= CENTER_CONTROL_BONUS;
                }
            }
        }

        // 5. モビリティ（動かせる手の数）
        score += MOBILITY_BONUS_PER_MOVE * calculateMobility(game, myPlayerType);
        score -= MOBILITY_BONUS_PER_MOVE * calculateMobility(game, opponentPlayerType);

        // 6. 駒の攻防評価（ライオン以外）
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && !(piece instanceof Lion)) {
                    // 自分の駒が攻撃されている場合のペナルティ
                    if (piece.getOwner() == myPlayerType && isAttacked(r, c, opponentPlayerType, board)) {
                        score -= ATTACKED_OWN_PIECE_PENALTY;
                    }

                    // 自分の駒が守られている場合のボーナス (ライオン以外)
                    if (piece.getOwner() == myPlayerType && isSquareDefended(r, c, myPlayerType, board)) {
                        score += DEFENDED_OWN_PIECE_BONUS;
                    }
                    
                    // 相手の駒が攻撃されている場合のボーナス (自分の駒が攻撃している)
                    if (piece.getOwner() == opponentPlayerType && isAttacked(r, c, myPlayerType, board)) {
                        score += ATTACKED_OPPONENT_PIECE_BONUS;
                    }
                }
            }
        }
        return score;
    }

    /**
     * 指定された座標にいる駒を攻撃している敵駒の数を数える。
     * @param targetRow ターゲット駒の行
     * @param targetCol ターゲット駒の列
     * @param attackingPlayerType 攻撃側のプレイヤータイプ
     * @param board 現在の盤面
     * @return 攻撃している駒の数
     */
    private int countAttackers(int targetRow, int targetCol, PlayerType attackingPlayerType, Board board) {
        int attackers = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == attackingPlayerType) {
                    // 駒の移動先リストを取得し、ターゲットマスが含まれているかチェック
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // isAttackedはGameクラスのisValidMoveAndNotIntoCheck/isValidDropAndNotIntoCheckと異なり
                        // 「その駒がそのマスに動けるか」を単純にチェックする (自殺手考慮なし)
                        if (move[0] == targetRow && move[1] == targetCol) {
                            attackers++;
                            break; // その駒が攻撃できればカウントして次の駒へ
                        }
                    }
                }
            }
        }
        return attackers;
    }

    /**
     * 指定された座標にいる駒を守っている味方駒の数を数える。
     * @param targetRow ターゲット駒の行
     * @param targetCol ターゲット駒の列
     * @param defendingPlayerType 防御側のプレイヤータイプ
     * @param board 現在の盤面
     * @return 守っている駒の数
     */
    private int countDefenders(int targetRow, int targetCol, PlayerType defendingPlayerType, Board board) {
        int defenders = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == defendingPlayerType) {
                    // 守っている駒自身は含めない
                    if (r == targetRow && c == targetCol) continue;

                    // 駒の移動先リストを取得し、ターゲットマスが含まれているかチェック
                    // ここでのgetPossibleMovesは、自殺手チェックをせず、単にその駒がそのマスに移動できるかを判定する
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == targetRow && move[1] == targetCol) {
                            // その駒がそのマスに移動できれば、守っていると見なす
                            defenders++;
                            break;
                        }
                    }
                }
            }
        }
        return defenders;
    }

    /**
     * 盤面上の指定されたプレイヤーのライオンの位置を返す。
     * @param board 盤面
     * @param playerType プレイヤータイプ
     * @return ライオンの[行, 列]座標、見つからない場合はnull
     */
    private int[] findLionPosition(Board board, PlayerType playerType) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion && piece.getOwner() == playerType) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /**
     * 指定されたプレイヤーの駒が動かせる合法手の総数を計算する (モビリティ)。
     * @param game 現在のゲーム状態
     * @param playerType プレイヤータイプ
     * @return 合法手の総数
     */
    private int calculateMobility(Game game, PlayerType playerType) {
        int mobility = 0;
        Board board = game.getBoard();

        // 駒の移動による合法手
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        if (game.isValidMoveAndNotIntoCheck(playerType, r, c, move[0], move[1])) {
                            mobility++;
                        }
                    }
                }
            }
        }

        // 手駒を打つ合法手
        // chooseMove や minimax で使われる currentPlayer ではなく、
        // game オブジェクトから直接 Player インスタンスを取得する
        Player currentPlayer = (playerType == game.getPlayerA().getPlayerType()) ? game.getPlayerA() : game.getPlayerB();
        
        for (int i = 0; i < currentPlayer.getCapturedPieces().size(); i++) {
            Piece pieceToDrop = currentPlayer.getCapturedPieces().get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c) && game.isValidDropAndNotIntoCheck(playerType, pieceToDrop, r, c)) {
                        mobility++;
                    }
                }
            }
        }
        return mobility;
    }

    /**
     * 指定されたマスが敵の駒によって攻撃されているかを判定します。
     * @param targetRow 判定するマスの行
     * @param targetCol 判定するマスの列
     * @param attackingPlayerType 攻撃側のプレイヤータイプ
     * @param board 現在の盤面
     * @return 攻撃されている場合はtrue、そうでない場合はfalse
     */
    private boolean isAttacked(int targetRow, int targetCol, PlayerType attackingPlayerType, Board board) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == attackingPlayerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == targetRow && move[1] == targetCol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 指定された駒が、自分の駒によって守られているかを判定します。
     * これは MinMax クラス内の private メソッドとして定義されています。
     * @param pieceRow 守られている駒の行
     * @param pieceCol 守られている駒の列
     * @param defendingPlayerType 守っているプレイヤーのタイプ (守られている駒と同じプレイヤー)
     * @param board 現在の盤面
     * @return 守られている場合はtrue、そうでない場合はfalse
     */
    private boolean isSquareDefended(int pieceRow, int pieceCol, PlayerType defendingPlayerType, Board board) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece defenderPiece = board.getPiece(r, c);
                if (defenderPiece != null && defenderPiece.getOwner() == defendingPlayerType) {
                    // 守っている駒自身は含まないようにチェック
                    if (r == pieceRow && c == pieceCol) {
                        continue;
                    }

                    List<int[]> possibleMoves = defenderPiece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == pieceRow && move[1] == pieceCol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * すべての可能な合法手を生成します。
     * 駒の移動と手駒を打つ手の両方を含みます。
     * suicidal move (自分から王手になる手) は Game.java の isValidMoveAndNotIntoCheck / isValidDropAndNotIntoCheck で除外されます。
     * @param game 現在のゲーム状態
     * @return 合法手のリスト
     */
    private List<int[]> generateAllPossibleMoves(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        PlayerType myPlayerType = this.playerType; // MinMaxインスタンス自身のプレイヤータイプ

        // 1. 駒の移動に関する合法手を収集
        Board currentBoard = game.getBoard();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = currentBoard.getPiece(r, c);
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, currentBoard);
                    for (int[] move : movesForPiece) {
                        // 移動が王手にならないかチェック
                        if (game.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                            allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }

        // 2. 手駒を打つ合法手を収集
        Player currentPlayer = (myPlayerType == PlayerType.PLAYER1) ? game.getPlayerA() : game.getPlayerB();
        for (int i = 0; i < currentPlayer.getCapturedPieces().size(); i++) {
            Piece pieceToDrop = currentPlayer.getCapturedPieces().get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 空いているマスであれば打てる AND その手が王手にならないかチェック
                    if (currentBoard.isEmpty(r, c) && game.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
                        // 手駒を打つ手は、特別な形式でリストに追加 { -1, 手駒リストのインデックス, 落とす行, 落とす列 }
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        return allPossibleMoves;
    }

    // clone() メソッドは Player 抽象クラスで実装されているため、ここでは不要。
    // 必要であれば、MinMax固有のフィールドをディープコピーするためにオーバーライドする。
    @Override
    public MinMax clone() {
        return (MinMax) super.clone();
    }
}