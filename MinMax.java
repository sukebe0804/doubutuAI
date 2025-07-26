import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MinMax extends Player {
    private static final int MAX_DEPTH = 10; // 探索の深さ

    // 駒の基本価値
    private static final int LION_VALUE = 1000;
    private static final int KIRIN_VALUE = 100;
    private static final int ZOU_VALUE = 100;
    private static final int HIYOKO_VALUE = 80;
    private static final int NIWATORI_VALUE = 100; // 成ったひよこの価値をさらに高く設定

    // 評価定数 (既存のもの)
    private static final int LION_SAFETY_BONUS_PER_DEFENDER = 20; // ライオンを守る味方駒1つあたりのボーナス
    private static final int LION_ATTACK_PENALTY_PER_ATTACKER = 20; // ライオンを狙う敵駒1つあたりのペナルティ
    private static final int CENTER_CONTROL_BONUS = 5; // 中央のマスを支配する駒へのボーナス
    private static final int MOBILITY_BONUS_PER_MOVE = 1; // 1手あたりのモビリティボーナス

    // --- 新しい評価定数: 駒の攻防評価関連 ---
    private static final int ATTACKED_OWN_PIECE_PENALTY = 15; // 自分の駒が攻撃されている場合のペナルティ
    private static final int ATTACKED_OPPONENT_PIECE_BONUS = 20; // 相手の駒を攻撃している場合のボーナス
    private static final int DEFENDED_OWN_PIECE_BONUS = 10; // 自分の駒が守られている場合のボーナス (ライオン以外)

    // --- ここから駒の配置点テーブル (Piece-Square Tables) ---
    // (値は調整可能であり、盤面サイズ 4x3 に合わせて定義)
    // PLAYER1 (CPU) 視点での価値。PLAYER2の駒の場合は、行を反転して使用する。

    // ひよこ (Hiyoko) の配置点: 前進するほど価値が高い
    // (Row: 0=自陣奥, 3=敵陣奥)
    private static final int[][] PST_HIYOKO = {
        {0, 0, 0},    // Row 0
        {0, 0, 0},    // Row 1
        {5, 10, 5},   // Row 2 (敵陣に近い)
        {15, 20, 15}  // Row 3 (敵陣奥、成れるマス)
    };

    // にわとり (Niwatori) の配置点: 成った駒は全体的に価値が高いが、中央を重視
    private static final int[][] PST_NIWATORI = {
        {10, 15, 10}, // Row 0
        {5, 20, 5},   // Row 1 (中央)
        {5, 20, 5},   // Row 2 (中央)
        {10, 15, 10}  // Row 3
    };

    // キリン (Kirin) の配置点: 盤の中央列や端の自由な場所を重視
    private static final int[][] PST_KIRIN = {
        {5, 10, 5},   // Row 0
        {0, 5, 0},    // Row 1
        {0, 5, 0},    // Row 2
        {5, 10, 5}    // Row 3
    };

    // ゾウ (Zou) の配置点: 斜めの利きを活かせる中央付近を重視
    private static final int[][] PST_ZOU = {
        {5, 0, 5},    // Row 0
        {0, 10, 0},   // Row 1
        {0, 10, 0},   // Row 2
        {5, 0, 5}     // Row 3
    };

    // ライオン (Lion) の配置点: 序盤は安全な自陣、終盤は中央に近づく
    // (ここはゲームの進行度で調整することも可能だが、今回は単純化)
    // 序盤は端に寄りすぎず、中央に留まることを優先。終盤は敵陣に近づくことでボーナス。
    private static final int[][] PST_LION = {
        {-20, -30, -20}, // Row 0 (敵陣奥)
        {-10, -5, -10},  // Row 1
        {0, 5, 0},     // Row 2 (やや安全な自陣)
        {10, 20, 10}   // Row 3 (自陣奥、ただしトライ可能な位置も含まれるため、敵陣に近づくと評価が逆転)
    };
    // --- 駒の配置点テーブルの追加ここまで ---

    public MinMax(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        // 最善の手と評価値を初期化
        int bestScore = (this.playerType == PlayerType.PLAYER1) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] bestMove = null;

        // すべての可能な手を生成（自殺手はここで除外される）
        List<int[]> possibleMoves = generateAllPossibleMoves(game);

        // 手の選択をランダムにするためのリスト（同じ評価値を持つ手を入れる）
        List<int[]> equallyGoodMoves = new ArrayList<>();

        // 可能な手が一つもない場合（詰みなど）に対応
        if (possibleMoves.isEmpty()) {
            return null; // または適切なエラーハンドリング
        }

        for (int[] move : possibleMoves) {
            // ゲームの状態をコピーしてシミュレーション
            Game simulatedGame = game.clone();
            // クローンされたゲームをサイレントモードにする
            simulatedGame.setSilentMode(true);

            // シミュレーションされたゲームで手を実行
            // generateAllPossibleMoves で既に合法性は確認済みなので、ここでは必ず成功すると仮定
            if (move[0] == -1) { // 手駒を打つ場合
                // move[1]は手駒リストのインデックス
                Player currentSimulatedPlayer = simulatedGame.getCurrentPlayer();
                if (currentSimulatedPlayer.getCapturedPieces().size() > move[1]) {
                     Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(move[1]);
                     simulatedGame.performDrop(pieceToDrop, move[2], move[3]);
                } else {
                    // このケースはgenerateAllPossibleMovesが正しく動作していれば発生しないはず
                    // エラーハンドリングまたはログ出力
                    System.err.println("Error: Invalid captured piece index in minimax for drop move.");
                    continue; // この手をスキップ
                }
            } else { // 駒を動かす場合
                simulatedGame.performMove(move[0], move[1], move[2], move[3]);
            }

            // 次のプレイヤーに切り替え
            simulatedGame.switchPlayer();

            // ミニマックス探索で評価値を計算
            // evaluateBoard に depth を渡す
            int score = minimax(simulatedGame, MAX_DEPTH - 1, (this.playerType == PlayerType.PLAYER1), Integer.MIN_VALUE, Integer.MAX_VALUE);

            // 最善手の更新
            if (this.playerType == PlayerType.PLAYER1) { // CPUがPlayer1 (最大化プレイヤー)
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                    equallyGoodMoves.clear();
                    equallyGoodMoves.add(move);
                } else if (score == bestScore) {
                    equallyGoodMoves.add(move);
                }
            } else { // CPUがPlayer2 (最小化プレイヤー)
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                    equallyGoodMoves.clear();
                    equallyGoodMoves.add(move);
                } else if (score == bestScore) {
                    equallyGoodMoves.add(move);
                }
            }
        }

        // 可能な手が一つもない場合（詰みなど）に対応
        if (bestMove == null && !possibleMoves.isEmpty()) {
             // すべての手が非常に悪い評価値の場合、可能な手の中からランダムに選択するなどのフォールバック
            Random rand = new Random();
            bestMove = possibleMoves.get(rand.nextInt(possibleMoves.size()));
        }

        // 同じ評価値を持つ手があればランダムに選択
        if (!equallyGoodMoves.isEmpty()) {
            Random rand = new Random();
            bestMove = equallyGoodMoves.get(rand.nextInt(equallyGoodMoves.size()));
        }

        return bestMove;
    }

    private int minimax(Game game, int depth, boolean maximizingPlayer, int alpha, int beta) {
        // ゲーム終了判定または探索深さの限界に達したら評価値を返す
        PlayerType winner = game.isGameOver();
        if (winner != null) {
            if (winner == this.playerType) { // 自分の勝利
                return 100000 + depth; // 早く勝つ手を優先 (深く探索した結果の勝利は、より価値が高い)
            } else { // 相手の勝利
                return -100000 - depth; // 早く負ける手を避ける (深く探索した結果の敗北は、より避けるべき)
            }
        }
        if (depth == 0) {
            return evaluateBoard(game, depth); // evaluateBoard に depth を渡す
        }

        // ここで生成される手も、自身のライオンが王手になる手は含まれない
        List<int[]> possibleMoves = generateAllPossibleMoves(game);

        // 可能な手が一つもない場合（詰みなど）の評価
        if (possibleMoves.isEmpty()) {
            // 現在のプレイヤーが詰んでいる場合
            if (game.isKingInCheck(game.getCurrentPlayer().getPlayerType())) {
                // 詰んでいるのは相手の番なので、これは自分にとっての勝利
                if (game.getCurrentPlayer().getPlayerType() != this.playerType) { // 相手が詰んでいる
                    return 10000 + depth;
                } else { // 自分が詰んでいる
                    return -10000 - depth;
                }
            }
            // 詰みではないが、合法手が一つもない場合（千日手など、動物将棋では稀）
            return 0; // 引き分けのような評価
        }


        if (maximizingPlayer) { // CPU (Player1) の手番の場合 (最大化)
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : possibleMoves) {
                Game simulatedGame = game.clone();
                simulatedGame.setSilentMode(true);

                if (move[0] == -1) { // 手駒を打つ場合
                    Player currentSimulatedPlayer = simulatedGame.getCurrentPlayer();
                    if (currentSimulatedPlayer.getCapturedPieces().size() > move[1]) {
                         Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(move[1]);
                         simulatedGame.performDrop(pieceToDrop, move[2], move[3]);
                    } else {
                        System.err.println("Error: Invalid captured piece index in minimax (simulated game) for drop move.");
                        continue;
                    }
                } else { // 駒を動かす場合
                    simulatedGame.performMove(move[0], move[1], move[2], move[3]);
                }

                simulatedGame.switchPlayer();
                int eval = minimax(simulatedGame, depth - 1, false, alpha, beta);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // α-βカットオフ
                }
            }
            return maxEval;
        } else { // 人間 (Player2) の手番の場合 (最小化)
            int minEval = Integer.MAX_VALUE;
            for (int[] move : possibleMoves) {
                Game simulatedGame = game.clone();
                simulatedGame.setSilentMode(true);

                if (move[0] == -1) { // 手駒を打つ場合
                    Player currentSimulatedPlayer = simulatedGame.getCurrentPlayer();
                    if (currentSimulatedPlayer.getCapturedPieces().size() > move[1]) {
                        Piece pieceToDrop = currentSimulatedPlayer.getCapturedPieces().get(move[1]);
                        simulatedGame.performDrop(pieceToDrop, move[2], move[3]);
                    } else {
                        System.err.println("Error: Invalid captured piece index in minimax (simulated game) for drop move.");
                        continue;
                    }
                } else { // 駒を動かす場合
                    simulatedGame.performMove(move[0], move[1], move[2], move[3]);
                }

                simulatedGame.switchPlayer();
                int eval = minimax(simulatedGame, depth - 1, true, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // α-βカットオフ
                }
            }
            return minEval;
        }
    }

    private List<int[]> generateAllPossibleMoves(Game game) {
        List<int[]> allMoves = new ArrayList<>();
        Board board = game.getBoard();
        Player currentPlayer = game.getCurrentPlayer();
        PlayerType currentPlayerType = currentPlayer.getPlayerType();

        // 盤上の駒の移動
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == currentPlayerType) {
                    List<int[]> possiblePieceMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] moveCoord : possiblePieceMoves) {
                        int[] potentialMove = new int[]{r, c, moveCoord[0], moveCoord[1]};

                        // この手を指した後、自分のライオンが王手にならないかチェック
                        Game simulatedGame = game.clone();
                        simulatedGame.setSilentMode(true);
                        
                        // performMove は合法でない場合に false を返すため、その結果も考慮する
                        boolean moveSuccessful = simulatedGame.performMove(potentialMove[0], potentialMove[1], potentialMove[2], potentialMove[3]);

                        // 有効な手であり、かつその手によって自分の王が王手にならない場合にのみ追加
                        if (moveSuccessful && !simulatedGame.isKingInCheck(currentPlayerType)) {
                            allMoves.add(potentialMove);
                        }
                    }
                }
            }
        }

        // 手駒を打つ手
        List<Piece> capturedPieces = currentPlayer.getCapturedPieces();
        // 直接 capturedPieces をループし、インデックス 'i' を使用
        for (int i = 0; i < capturedPieces.size(); i++) {
            // Piece pieceToExamine = capturedPieces.get(i); // この行は必須ではない
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) {
                        Piece pieceToExamine = capturedPieces.get(i); // 手駒のルール適用のため、ここで取得

                        // ひよこを相手の最終段に打つのは禁止 (打ち歩詰めのルールの一部、成れない場所への打ち込み)
                        if (pieceToExamine instanceof Hiyoko) {
                            if (currentPlayerType == PlayerType.PLAYER1 && r == Board.ROWS - 1) { // Player1の敵陣最終段
                                continue;
                            }
                            if (currentPlayerType == PlayerType.PLAYER2 && r == 0) { // Player2の敵陣最終段
                                continue;
                            }
                        }

                        int[] potentialDrop = new int[]{-1, i, r, c}; // 'i' をそのままインデックスとして使用

                        // この手を指した後、自分のライオンが王手にならないかチェック
                        Game simulatedGame = game.clone();
                        simulatedGame.setSilentMode(true);
                        
                        // simulatedGameのcurrentPlayerからpieceToDropのクローンを取得して渡す必要がある
                        Player simulatedCurrentPlayer = simulatedGame.getCurrentPlayer();
                        Piece simulatedPieceToDrop = null;
                        // インデックスが範囲内かチェック
                        if (simulatedCurrentPlayer.getCapturedPieces().size() > i) {
                            simulatedPieceToDrop = simulatedCurrentPlayer.getCapturedPieces().get(i);
                        } else {
                            // これは通常発生しないはずだが、安全のためスキップ
                            System.err.println("Error: Captured piece index out of bounds in generateAllPossibleMoves simulation.");
                            continue;
                        }

                        boolean moveSuccessful = simulatedGame.performDrop(simulatedPieceToDrop, potentialDrop[2], potentialDrop[3]);

                        // 有効な手であり、かつその手によって自分の王が王手にならない場合にのみ追加
                        if (moveSuccessful && !simulatedGame.isKingInCheck(currentPlayerType)) {
                            allMoves.add(potentialDrop);
                        }
                    }
                }
            }
        }
        return allMoves;
    }

    private int evaluateBoard(Game game, int depth) { // depth を引数に追加
        int score = 0;
        Board board = game.getBoard();
        PlayerType PlayerBType = this.playerType;
        PlayerType PlayerAType = (PlayerBType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = 0;
                    int positionalValue = 0; // 駒の配置点

                    // 駒の基本価値と配置点を計算
                    if (piece instanceof Lion) {
                        pieceValue = LION_VALUE;
                        positionalValue = getPositionalValue(PST_LION, r, c, piece.getOwner(), Board.ROWS);
                    } else if (piece instanceof Kirin) {
                        pieceValue = KIRIN_VALUE;
                        positionalValue = getPositionalValue(PST_KIRIN, r, c, piece.getOwner(), Board.ROWS);
                    } else if (piece instanceof Zou) {
                        pieceValue = ZOU_VALUE;
                        positionalValue = getPositionalValue(PST_ZOU, r, c, piece.getOwner(), Board.ROWS);
                    } else if (piece instanceof Hiyoko) {
                        if (piece.isPromoted()) {
                            pieceValue = NIWATORI_VALUE;
                            positionalValue = getPositionalValue(PST_NIWATORI, r, c, piece.getOwner(), Board.ROWS);
                        } else {
                            pieceValue = HIYOKO_VALUE;
                            positionalValue = getPositionalValue(PST_HIYOKO, r, c, piece.getOwner(), Board.ROWS);
                        }
                    }

                    // ライオンの安全性評価 (既存ロジック - 周囲の駒)
                    if (piece instanceof Lion) {
                        // ライオンが敵陣に近づくほどボーナス (トライルール考慮)
                        if (piece.getOwner() == PlayerType.PLAYER1) {
                            pieceValue += (r * 20); // 敵陣(r=3)に近づくほど高得点
                        } else { // 人間のライオンの場合
                            pieceValue += ((Board.ROWS - 1 - r) * 20); // 敵陣(r=0)に近づくほど高得点
                        }

                        int defenderCount = 0;
                        int attackerCount = 0;
                        int[][] surroundingDeltas = {
                            {-1, -1}, {-1, 0}, {-1, 1},
                            {0, -1},           {0, 1},
                            {1, -1}, {1, 0}, {1, 1}
                        };
                        for (int[] delta : surroundingDeltas) {
                            int checkRow = r + delta[0];
                            int checkCol = c + delta[1];
                            if (board.isValidCoordinate(checkRow, checkCol)) {
                                Piece adjPiece = board.getPiece(checkRow, checkCol);
                                if (adjPiece != null) {
                                    if (adjPiece.getOwner() == piece.getOwner()) { // 味方駒
                                        defenderCount++;
                                    } else { // 敵駒
                                        attackerCount++;
                                    }
                                }
                            }
                        }
                        score += defenderCount * LION_SAFETY_BONUS_PER_DEFENDER;
                        score -= attackerCount * LION_ATTACK_PENALTY_PER_ATTACKER;
                    }
                    
                    // 中央支配のボーナス (既存ロジック)
                    if ((r >= 1 && r <= 2)) {
                        if (piece.getOwner() == PlayerBType) {
                            score += CENTER_CONTROL_BONUS;
                        } else {
                            score -= CENTER_CONTROL_BONUS;
                        }
                    }

                    // 駒の攻防評価 (新しいロジック)
                    if (piece.getOwner() == PlayerBType) {
                        // 自分の駒が攻撃されているか
                        if (isSquareAttacked(r, c, PlayerAType, board)) {
                            score -= ATTACKED_OWN_PIECE_PENALTY;
                        }
                        // 自分の駒が味方に守られているか (ライオンの周囲評価と重複しないように、ライオン以外に適用)
                        if (!(piece instanceof Lion) && isSquareDefended(r, c, PlayerBType, board)) {
                            score += DEFENDED_OWN_PIECE_BONUS;
                        }
                        score += pieceValue + positionalValue;
                    } else { // 相手の駒の場合
                        // 相手の駒が攻撃されているか (CPUが攻撃しているか)
                        if (isSquareAttacked(r, c, PlayerBType, board)) {
                            score += ATTACKED_OPPONENT_PIECE_BONUS;
                        }
                        // 相手の駒が味方に守られているか (相手の守りに対するペナルティ)
                        if (!(piece instanceof Lion) && isSquareDefended(r, c, PlayerAType, board)) {
                            score -= DEFENDED_OWN_PIECE_BONUS; // 相手が守っている駒が多いとCPUには不利
                        }
                        score -= (pieceValue + positionalValue);
                    }
                }
            }
        }

        // 手駒の価値 (既存ロジック)
        for (Piece p : this.getCapturedPieces()) { // CPUの手駒
            if (p instanceof Lion) score += LION_VALUE; 
            else if (p instanceof Kirin) score += KIRIN_VALUE;
            else if (p instanceof Zou) score += ZOU_VALUE;
            else if (p instanceof Hiyoko) score += HIYOKO_VALUE;
        }
        for (Piece p : game.getPlayerA().getCapturedPieces()) { // 人間の手駒
            if (p instanceof Lion) score -= LION_VALUE;
            else if (p instanceof Kirin) score -= KIRIN_VALUE;
            else if (p instanceof Zou) score -= ZOU_VALUE;
            else if (p instanceof Hiyoko) score -= HIYOKO_VALUE;
        }

        // モビリティ（駒の活動範囲）の評価 (既存ロジック)
        int cpuMobility = 0;
        int humanMobility = 0;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    List<int[]> moves = piece.getPossibleMoves(r, c, board);
                    
                    if (piece.getOwner() == PlayerBType) {
                        cpuMobility += moves.size();
                    } else {
                        humanMobility += moves.size();
                    }
                }
            }
        }
        score += cpuMobility * MOBILITY_BONUS_PER_MOVE;
        score -= humanMobility * MOBILITY_BONUS_PER_MOVE;

        // ゲームの勝敗判定によるスコア調整 (既存ロジック)
        PlayerType winner = game.isGameOver();
        if (winner == PlayerBType) {
            score += 10000 + depth; 
        } else if (winner == PlayerAType) {
            score -= 10000 + depth;
        }

        return score;
    }

    /**
     * 駒の所有者に応じて配置点テーブルから適切な値を取得するヘルパーメソッド。
     * Player2 (後手) の駒の場合、テーブルの行を反転して使用します。
     * @param pstTable 駒の配置点テーブル (Player1視点)
     * @param row 駒の現在の行
     * @param col 駒の現在の列
     * @param owner 駒の所有者
     * @param boardRows 盤の総行数
     * @return 該当する配置点
     */
    private int getPositionalValue(int[][] pstTable, int row, int col, PlayerType owner, int boardRows) {
        if (owner == PlayerType.PLAYER1) {
            return pstTable[row][col];
        } else {
            // Player2 (後手) の場合、テーブルを垂直方向に反転させる
            return pstTable[boardRows - 1 - row][col];
        }
    }

    /**
     * 指定されたマスが、指定されたプレイヤータイプの駒によって攻撃されているかを判定します。
     * ここでの「攻撃」は、その駒が合法的にそのマスに移動（または捕獲）できることを意味します。
     * ただし、その移動によって王手が回避できるかなどのチェックは含まれません。
     * @param targetRow 判定するマスの行
     * @param targetCol 判定するマスの列
     * @param attackingPlayerType 攻撃しているプレイヤーのタイプ
     * @param board 現在の盤面
     * @return 攻撃されている場合はtrue、そうでない場合はfalse
     */
    private boolean isSquareAttacked(int targetRow, int targetCol, PlayerType attackingPlayerType, Board board) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == attackingPlayerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board); // 全ての可能な移動先を取得
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
}