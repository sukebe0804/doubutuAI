import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MinMax extends Player {
    // 探索の深さ
    private static final int MAX_DEPTH = 8; // 探索深度を調整（計算資源と相談）

    // 駒の基本価値
    private static final int LION_VALUE = 2500; // ライオンの価値を非常に高く設定（詰み/トライはこれよりはるかに高い）
    private static final int KIRIN_VALUE = 300;
    private static final int ZOU_VALUE = 300;
    private static final int HIYOKO_VALUE = 100;
    private static final int NIWATORI_VALUE = 200; // 成ったひよこの価値を高く設定

    // 評価定数
    private static final int LION_SAFETY_BONUS_PER_DEFENDER = 100; // ライオンを守る味方駒1つあたりのボーナスを強化
    private static final int LION_ATTACK_PENALTY_PER_ATTACKER = 100; // ライオンを狙う敵駒1つあたりのペナルティを強化
    private static final int CENTER_CONTROL_BONUS = 15; // 中央のマスを支配する駒へのボーナスを強化
    private static final int MOBILITY_BONUS_PER_MOVE = 5; // 1手あたりのモビリティボーナスを強化
    private static final int ATTACKED_OWN_PIECE_PENALTY = 50; // 自分の駒が攻撃されている場合のペナルティを強化
    private static final int ATTACKED_OPPONENT_PIECE_BONUS = 50; // 相手の駒を攻撃している場合のボーナスを強化
    private static final int DEFENDED_OWN_PIECE_BONUS = 30; // 自分の駒が守られている場合のボーナス (ライオン以外) を強化
    private static final int PROMOTION_THREAT_BONUS = 150; // ひよこが敵陣最奥にいる場合のボーナス
    private static final int TRIAL_WIN_SCORE = 10000; // トライによる勝利点

    // 駒の配置点テーブル (Piece-Square Tables) - 調整済み
    // PLAYER1 (CPU) 視点での価値。PLAYER2の駒の場合は、行を反転して使用する。
    // (Row: 0=自陣奥 (PLAYER2の最前線), 3=敵陣奥 (PLAYER1の最前線))
    private static final int[][] PST_HIYOKO = {
        {50, 60, 50},    // Row 0 (PLAYER2の初期配置側)
        {40, 50, 40}, // Row 1
        {30, 40, 30}, // Row 2
        {10, 0, 10}  // Row 3 (PLAYER1の初期配置側) - ひよこがここに来ると成る可能性が高いので、低い値に設定
    };

    private static final int[][] PST_NIWATORI = {
        {100, 120, 100}, // Row 0 (PLAYER2の初期配置側) - 成り駒は強い
        {80, 100, 80}, // Row 1
        {60, 80, 60}, // Row 2
        {40, 50, 40} // Row 3 (PLAYER1の初期配置側)
    };

    private static final int[][] PST_KIRIN = {
        {20, 30, 20}, // Row 0
        {40, 50, 40}, // Row 1
        {60, 80, 60}, // Row 2
        {80, 100, 80} // Row 3
    };

    private static final int[][] PST_ZOU = {
        {20, 30, 20}, // Row 0
        {40, 50, 40}, // Row 1
        {60, 80, 60}, // Row 2
        {80, 100, 80} // Row 3
    };

    private static final int[][] PST_LION = {
        {-1000, -1200, -1000}, // 危険な位置は低く
        {-500, -600, -500},
        { 0, 100, 0},
        { 500, 1000, 500} // 自陣奥は高く
    };

    public MinMax(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        // 現在のプレイヤーとボードの状態をMinMaxアルゴリズムのルートノードとして設定
        SimulationState initialState = new SimulationState(game.getBoard().clone(), game.getPlayerA().clone(), game.getPlayerB().clone(), this.getPlayerType()); // PlayerA, PlayerBはGameクラスから取得し、自身のPlayerTypeを渡す

        int[] bestMove = null;
        int bestValue = Integer.MIN_VALUE;

        // chooseMoveで呼ばれるgetAllPossibleMovesにはGameインスタンスを渡す
        // GameクラスのisValidMoveAndNotIntoCheckとisValidDropAndNotIntoCheckを利用
        List<int[]> moves = getAllPossibleMoves(game); 
        
        // AlphaBeta同様、合法手がなければnullを返す
        if (moves.isEmpty()) {
            return null;
        }

        // 探索効率向上のため、合法手をソートする
        sortMoves(moves, initialState.board, this.getPlayerType(), game); // gameインスタンスも渡す
        
        // Minimax探索の開始
        for (int[] move : moves) {
            SimulationState nextState = initialState.clone(); // 現在の状態を複製
            
            // nextStateのplayerA, playerBのplayerTypeが正しく設定されていることを確認
            if (nextState.playerA.getPlayerType() == null) nextState.playerA.setPlayerType(PlayerType.PLAYER1);
            if (nextState.playerB.getPlayerType() == null) nextState.playerB.setPlayerType(PlayerType.PLAYER2);

            nextState.applyMove(move); // 手を適用

            // ミニマックスアルゴリズムは、相手のターンを考慮して最小値を探す
            int value = minimax(nextState, MAX_DEPTH - 1, false);

            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
        }
        return bestMove;
    }

    // chooseMoveから呼ばれる、Gameインスタンスを引数にとる合法手生成メソッド
    // ここに手駒を打つ処理を追加
    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        PlayerType myPlayerType = this.getPlayerType(); // 現在の手番のプレイヤータイプ

        // 1. 盤上の駒の移動に関する合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = game.getBoard().getPiece(r, c);
                // 自分の駒であれば
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, game.getBoard());
                    for (int[] move : movesForPiece) {
                        // 移動が王手にならないかチェック (Gameクラスのメソッドを使用)
                        if (game.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                            // {元の行, 元の列, 新しい行, 新しい列}
                            allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }

        // 2. 手駒を打つ合法手を収集
        List<Piece> captured = this.getCapturedPieces(); // MinMaxインスタンス自身の手駒を取得
        for (int i = 0; i < captured.size(); i++) {
            Piece pieceToDrop = captured.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 空いているマスであれば打てる AND その手が王手にならないかチェック (Gameクラスのメソッドを使用)
                    if (game.getBoard().isEmpty(r, c) && game.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
                        // 手駒を打つ手は、特別な形式でリストに追加
                        // { -1, 手駒リストのインデックス, 落とす行, 落とす列 }
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        return allPossibleMoves;
    }


    private int minimax(SimulationState state, int depth, boolean maximizingPlayer) {
        // 探索終了条件
        // 1. 深さ制限に到達
        // 2. ゲームが終了（詰み、トライ勝利）
        if (depth == 0 || state.isGameOver() != null) {
            return evaluate(state.board, this.getPlayerType(), state.playerA, state.playerB); // 評価関数を呼び出し
        }

        // minimax内ではSimulationStateの情報を直接利用して合法手を生成
        List<int[]> moves = state.getLegalMovesInSimulation(); 
        
        // 合法手がない場合、評価を返す (ステイルメイトなど)
        if (moves.isEmpty()) {
             return evaluate(state.board, this.getPlayerType(), state.playerA, state.playerB);
        }

        // 探索効率向上のため、合法手をソートする
        // minimax内で渡されるstateのboardとplayerインスタンスを使ってソート
        // sortMoves(moves, state.board, state.currentPlayerType, state.toGame()); // toGameは削除するため変更
        sortMovesForSimulation(moves, state);


        if (maximizingPlayer) { // 自分の手番 (最大化)
            int maxValue = Integer.MIN_VALUE;
            for (int[] move : moves) {
                SimulationState nextState = state.clone(); // 状態を複製
                nextState.applyMove(move); // 手を適用
                maxValue = Math.max(maxValue, minimax(nextState, depth - 1, false));
            }
            return maxValue;
        } else { // 相手の手番 (最小化)
            int minValue = Integer.MAX_VALUE;
            for (int[] move : moves) {
                SimulationState nextState = state.clone(); // 状態を複製
                nextState.applyMove(move); // 手を適用
                minValue = Math.min(minValue, minimax(nextState, depth - 1, true));
            }
            return minValue;
        }
    }

    // 評価関数
    private int evaluate(Board board, PlayerType myPlayerType, Player playerA, Player playerB) {
        int score = 0;
        PlayerType opponentPlayerType = (myPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // 1. 駒の価値による評価
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionValue = getPositionValue(piece, r, c); // 配置点テーブルからの価値

                    if (piece.getOwner() == myPlayerType) {
                        score += pieceValue;
                        score += positionValue;
                    } else {
                        score -= pieceValue;
                        score -= positionValue;
                    }
                }
            }
        }

        // 2. 王手判定と詰み、トライ勝利の評価
        // isCheckMateはPlayerType.PLAYER1がチェックメイトされているか判定するため、
        // 評価関数内で呼び出すプレイヤーの型に注意が必要。
        // ここではmyPlayerTypeが相手を詰ませているか、自分が詰まされていないかを確認する
        if (isCheckMate(board, opponentPlayerType, playerA, playerB)) { // 相手を詰ませた場合
            return TRIAL_WIN_SCORE; // 非常に高い点数
        }
        if (isCheckMate(board, myPlayerType, playerA, playerB)) { // 自分が詰まされた場合
            return -TRIAL_WIN_SCORE; // 非常に低い点数
        }

        // トライ勝利の判定（ライオンが敵陣最奥に到達し、相手に取られない）
        if (isTrialWin(board, myPlayerType, opponentPlayerType)) {
             return TRIAL_WIN_SCORE;
        }
        if (isTrialWin(board, opponentPlayerType, myPlayerType)) {
             return -TRIAL_WIN_SCORE;
        }
        
        // 3. ライオンの安全性評価
        int myLionSafety = countLionDefenders(board, myPlayerType);
        int opponentLionSafety = countLionDefenders(board, opponentPlayerType);
        score += myLionSafety * LION_SAFETY_BONUS_PER_DEFENDER;
        score -= opponentLionSafety * LION_SAFETY_BONUS_PER_DEFENDER; // 相手のライオンの安全度をマイナス評価

        // 4. ライオンへの攻撃評価
        int myLionAttackers = countLionAttackers(board, opponentPlayerType, myPlayerType); // 相手から見た自分のライオンへの攻撃
        int opponentLionAttackers = countLionAttackers(board, myPlayerType, opponentPlayerType); // 自分から見た相手のライオンへの攻撃
        score -= myLionAttackers * LION_ATTACK_PENALTY_PER_ATTACKER;
        score += opponentLionAttackers * LION_ATTACK_PENALTY_PER_ATTACKER;

        // 5. 中央支配ボーナス
        score += countCenterControl(board, myPlayerType) * CENTER_CONTROL_BONUS;
        score -= countCenterControl(board, opponentPlayerType) * CENTER_CONTROL_BONUS;

        // 6. モビリティ（動かせる手の数）
        // MinMaxのコンテキストでは、SimulationState内のplayerAとplayerBを使用する
        int myMobility = countPossibleMovesForPlayer(board, myPlayerType, (myPlayerType == PlayerType.PLAYER1) ? playerA : playerB);
        int opponentMobility = countPossibleMovesForPlayer(board, opponentPlayerType, (opponentPlayerType == PlayerType.PLAYER1) ? playerA : playerB);
        score += myMobility * MOBILITY_BONUS_PER_MOVE;
        score -= opponentMobility * MOBILITY_BONUS_PER_MOVE;

        // 7. 攻撃されている自分の駒と攻撃している相手の駒の評価
        score -= countAttackedOwnPieces(board, myPlayerType, opponentPlayerType) * ATTACKED_OWN_PIECE_PENALTY;
        score += countAttackedOpponentPieces(board, myPlayerType, opponentPlayerType) * ATTACKED_OPPONENT_PIECE_BONUS;

        // 8. 守られている自分の駒の評価 (ライオン以外)
        score += countDefendedOwnPieces(board, myPlayerType, opponentPlayerType) * DEFENDED_OWN_PIECE_BONUS;
        // 相手の守られている駒は評価しないか、マイナスにするか検討

        // 9. 成りによる脅威
        score += countPromotionThreat(board, myPlayerType) * PROMOTION_THREAT_BONUS;
        score -= countPromotionThreat(board, opponentPlayerType) * PROMOTION_THREAT_BONUS;
        
        // 10. ライオン接近ペナルティ（AlphaBetaを参考に導入）
        int[] myLionPos = findLion(board, myPlayerType);
        int[] opponentLionPos = findLion(board, opponentPlayerType);

        if (myLionPos != null) {
            score -= getLionProximityPenalty(board, myLionPos[0], myLionPos[1], opponentPlayerType);
        }
        if (opponentLionPos != null) {
            score += getLionProximityPenalty(board, opponentLionPos[0], opponentLionPos[1], myPlayerType);
        }

        return score;
    }

    // 駒の価値を返すヘルパーメソッド
    private int getPieceValue(Piece piece) {
        if (piece instanceof Hiyoko) {
            return piece.isPromoted() ? NIWATORI_VALUE : HIYOKO_VALUE;
        } else if (piece instanceof Kirin) {
            return KIRIN_VALUE;
        } else if (piece instanceof Zou) {
            return ZOU_VALUE;
        } else if (piece instanceof Lion) {
            return LION_VALUE;
        }
        return 0;
    }

    // 駒の配置点テーブルから価値を取得するヘルパーメソッド
    private int getPositionValue(Piece piece, int row, int col) {
        int[][] pst;
        // PLAYER1とPLAYER2でテーブルを反転させる必要がある
        if (piece.getOwner() == PlayerType.PLAYER1) {
            if (piece instanceof Hiyoko) {
                pst = PST_HIYOKO;
            } else if (piece instanceof Kirin) {
                pst = PST_KIRIN;
            } else if (piece instanceof Zou) {
                pst = PST_ZOU;
            } else if (piece instanceof Lion) {
                pst = PST_LION;
            } else {
                return 0;
            }
            return pst[row][col];
        } else { // PLAYER2の場合、行を反転してテーブルを参照
            if (piece instanceof Hiyoko) {
                pst = PST_HIYOKO;
            } else if (piece instanceof Kirin) {
                pst = PST_KIRIN;
            } else if (piece instanceof Zou) {
                pst = PST_ZOU;
            } else if (piece instanceof Lion) {
                pst = PST_LION;
            } else {
                return 0;
            }
            return pst[Board.ROWS - 1 - row][col];
        }
    }


    // ライオンの守り駒の数を数える
    private int countLionDefenders(Board board, PlayerType lionOwner) {
        int defenders = 0;
        int[] lionPos = findLion(board, lionOwner);
        if (lionPos == null) return 0; // ライオンがいない場合

        int lRow = lionPos[0];
        int lCol = lionPos[1];

        // ライオンの周囲8マスをチェック
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue; // ライオン自身のマスはスキップ

                int nRow = lRow + dr;
                int nCol = lCol + dc;

                if (board.isValidCoordinate(nRow, nCol)) {
                    Piece piece = board.getPiece(nRow, nCol);
                    if (piece != null && piece.getOwner() == lionOwner) {
                        // その駒が実際にライオンを守っているか (ライオンの動きをカバーしているか) を詳細にチェックすることも可能だが、
                        // ここでは簡易的に隣接している味方駒をディフェンダーとみなす
                        defenders++;
                    }
                }
            }
        }
        return defenders;
    }

    // ライオンへの攻撃駒の数を数える
    private int countLionAttackers(Board board, PlayerType attackerType, PlayerType lionOwner) {
        int attackers = 0;
        int[] lionPos = findLion(board, lionOwner);
        if (lionPos == null) return 0;

        int lRow = lionPos[0];
        int lCol = lionPos[1];

        // 盤上の全ての敵駒について、ライオンを攻撃できるかチェック
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == attackerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == lRow && move[1] == lCol) {
                            attackers++;
                            break; // この駒はライオンを攻撃できる
                        }
                    }
                }
            }
        }
        return attackers;
    }

    // 中央支配ボーナスを計算
    private int countCenterControl(Board board, PlayerType playerType) {
        int controlledSquares = 0;
        // 中央のマス (例: R=1,2, C=0,1,2)
        int[][] CENTRAL_SQUARES = {
            {1, 0}, {1, 1}, {1, 2},
            {2, 0}, {2, 1}, {2, 2}
        };

        for (int[] coord : CENTRAL_SQUARES) {
            int r = coord[0];
            int c = coord[1];
            Piece piece = board.getPiece(r, c);
            if (piece != null && piece.getOwner() == playerType) {
                controlledSquares++;
            }
        }
        return controlledSquares;
    }

    // プレイヤーが動かせる駒の総数を数える（モビリティ）
    private int countPossibleMovesForPlayer(Board board, PlayerType playerType, Player playerInstance) {
        int mobility = 0;
        
        // 1. 盤上の駒の移動
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    mobility += piece.getPossibleMoves(r, c, board).size();
                }
            }
        }

        // 2. 手駒を打つ手の数 (各手駒が置けるマス数を概算)
        // 正確にはisValidDropAndNotIntoCheckでフィルタリングした数を数えるべきだが、
        // ここは評価関数なので簡易的な指標として総マス数を掛ける
        mobility += playerInstance.getCapturedPieces().size() * Board.ROWS * Board.COLS;
        
        return mobility;
    }

    // 攻撃されている自分の駒の数を数える
    private int countAttackedOwnPieces(Board board, PlayerType myPlayerType, PlayerType opponentPlayerType) {
        int attackedPieces = 0;
        // 相手の全ての駒が攻撃できるマスを収集
        List<int[]> opponentAttackMoves = new ArrayList<>();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece opponentPiece = board.getPiece(r, c);
                if (opponentPiece != null && opponentPiece.getOwner() == opponentPlayerType) {
                    opponentAttackMoves.addAll(opponentPiece.getPossibleMoves(r, c, board));
                }
            }
        }

        // 自分の駒が攻撃されているかチェック
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece myPiece = board.getPiece(r, c);
                if (myPiece != null && myPiece.getOwner() == myPlayerType) {
                    // このマスが相手の攻撃範囲に含まれているか
                    for (int[] attackMove : opponentAttackMoves) {
                        if (attackMove[0] == r && attackMove[1] == c) {
                            attackedPieces++;
                            break; // 複数の駒から攻撃されていても1つとしてカウント
                        }
                    }
                }
            }
        }
        return attackedPieces;
    }

    // 攻撃している相手の駒の数を数える
    private int countAttackedOpponentPieces(Board board, PlayerType myPlayerType, PlayerType opponentPlayerType) {
        int attackedPieces = 0;
        // 自分の全ての駒が攻撃できるマスを収集
        List<int[]> myAttackMoves = new ArrayList<>();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece myPiece = board.getPiece(r, c);
                if (myPiece != null && myPiece.getOwner() == myPlayerType) {
                    myAttackMoves.addAll(myPiece.getPossibleMoves(r, c, board));
                }
            }
        }

        // 相手の駒が攻撃されているかチェック
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece opponentPiece = board.getPiece(r, c);
                if (opponentPiece != null && opponentPiece.getOwner() == opponentPlayerType) {
                    // このマスが自分の攻撃範囲に含まれているか
                    for (int[] attackMove : myAttackMoves) {
                        if (attackMove[0] == r && attackMove[1] == c) {
                            attackedPieces++;
                            break;
                        }
                    }
                }
            }
        }
        return attackedPieces;
    }

    // 守られている自分の駒の数を数える (ライオン以外)
    private int countDefendedOwnPieces(Board board, PlayerType myPlayerType, PlayerType opponentPlayerType) {
        int defendedPieces = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece myPiece = board.getPiece(r, c);
                if (myPiece != null && myPiece.getOwner() == myPlayerType && !(myPiece instanceof Lion)) {
                    // この駒が攻撃されているかチェック
                    boolean isAttacked = false;
                    for (int oppR = 0; oppR < Board.ROWS; oppR++) {
                        for (int oppC = 0; oppC < Board.COLS; oppC++) {
                            Piece opponentPiece = board.getPiece(oppR, oppC);
                            if (opponentPiece != null && opponentPiece.getOwner() == opponentPlayerType) {
                                List<int[]> opponentMoves = opponentPiece.getPossibleMoves(oppR, oppC, board);
                                for (int[] move : opponentMoves) {
                                    if (move[0] == r && move[1] == c) {
                                        isAttacked = true;
                                        break;
                                    }
                                }
                            }
                            if (isAttacked) break;
                        }
                        if (isAttacked) break;
                    }

                    if (isAttacked) { // 攻撃されている場合のみ、守りがあるかチェック
                        // 自分の他の駒がこの駒をカバーしているか
                        for (int ownR = 0; ownR < Board.ROWS; ownR++) {
                            for (int ownC = 0; ownC < Board.COLS; ownC++) {
                                if (ownR == r && ownC == c) continue; // 自分の駒自身は除く
                                Piece potentialDefender = board.getPiece(ownR, ownC);
                                if (potentialDefender != null && potentialDefender.getOwner() == myPlayerType) {
                                    List<int[]> defenderMoves = potentialDefender.getPossibleMoves(ownR, ownC, board);
                                    for (int[] move : defenderMoves) {
                                        if (move[0] == r && move[1] == c) {
                                            defendedPieces++;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return defendedPieces;
    }

    // 成りによる脅威の評価
    private int countPromotionThreat(Board board, PlayerType playerType) {
        int threat = 0;
        for (int c = 0; c < Board.COLS; c++) {
            // PLAYER1 (先手) の場合、敵陣最奥は行3
            if (playerType == PlayerType.PLAYER1) {
                Piece piece = board.getPiece(3, c);
                if (piece instanceof Hiyoko && piece.getOwner() == playerType && !piece.isPromoted()) {
                    threat++;
                }
            } else { // PLAYER2 (後手) の場合、敵陣最奥は行0
                Piece piece = board.getPiece(0, c);
                if (piece instanceof Hiyoko && piece.getOwner() == playerType && !piece.isPromoted()) {
                    threat++;
                }
            }
        }
        return threat;
    }

    // ライオンのいる位置を探す
    private int[] findLion(Board board, PlayerType playerType) {
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

    // 指定された座標が攻撃範囲内にあるかチェックするヘルパーメソッド
    private boolean isSquareAttacked(Board board, int targetRow, int targetCol, PlayerType attackingPlayerType) {
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

    // 王手判定（指定されたプレイヤーが王手されているか）
    private boolean isCheck(Board board, PlayerType checkedPlayerType) {
        int[] lionPos = findLion(board, checkedPlayerType);
        if (lionPos == null) return false; // ライオンがいなければ王手ではない

        PlayerType opponentPlayerType = (checkedPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        return isSquareAttacked(board, lionPos[0], lionPos[1], opponentPlayerType);
    }

    // 詰み判定 (指定されたプレイヤーが詰まされているか)
    private boolean isCheckMate(Board board, PlayerType checkedPlayerType, Player playerA, Player playerB) {
        if (!isCheck(board, checkedPlayerType)) {
            return false; // 王手でなければ詰みではない
        }

        Player currentPlayingPlayer = (checkedPlayerType == PlayerType.PLAYER1) ? playerA : playerB;
        // getAllPossibleMovesWithCurrentStateはSimulationStateのコンテキストで動くように変更済み
        List<int[]> legalMovesForCheckMate = getAllPossibleMovesWithCurrentState(board, currentPlayingPlayer);

        // どの手も王手を回避できるかチェック
        for (int[] move : legalMovesForCheckMate) {
            // シミュレーション状態をクローンし、手を適用してチェック
            SimulationState tempState = new SimulationState(board.clone(), playerA.clone(), playerB.clone(), checkedPlayerType);
            tempState.applyMove(move); // applyMoveで手駒のremoveなども処理される
            if (!tempState.isCheck(checkedPlayerType)) { // 王手を回避できる手が見つかった
                return false;
            }
        }
        return true; // 全ての手が王手になる
    }

    // SimulationState内で使うための、盤面とPlayerインスタンスを直接受け取る合法手生成メソッド
    private List<int[]> getAllPossibleMovesWithCurrentState(Board board, Player player) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        PlayerType myPlayerType = player.getPlayerType();

        // 1. 盤上の駒の移動に関する合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        // シミュレーション内のisValidMoveAndNotIntoCheck
                        if (isValidMoveInSimulation(board, player, r, c, move[0], move[1])) {
                            allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }

        // 2. 手駒を打つ合法手を収集
        List<Piece> captured = player.getCapturedPieces(); // 引数で渡されたplayerの手駒
        for (int i = 0; i < captured.size(); i++) {
            Piece pieceToDrop = captured.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 空いているマスであれば打てるかチェック & シミュレーション内のisValidDropAndNotIntoCheck
                    if (board.isEmpty(r, c) && isValidDropInSimulation(board, player, pieceToDrop, r, c)) {
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        return allPossibleMoves;
    }

    // シミュレーション内での駒の移動の合法性チェック（王手にならないか）
    private boolean isValidMoveInSimulation(Board board, Player currentPlayer, int fromRow, int fromCol, int toRow, int toCol) {
        Board tempBoard = board.clone();
        Player tempPlayerA = (currentPlayer.getPlayerType() == PlayerType.PLAYER1) ? currentPlayer.clone() : new MinMax("tempA"); // 実際にはSimulationStateのplayerA/Bをコピーする必要がある
        Player tempPlayerB = (currentPlayer.getPlayerType() == PlayerType.PLAYER2) ? currentPlayer.clone() : new MinMax("tempB"); // 実際にはSimulationStateのplayerA/Bをコピーする必要がある

        // HACK: ここでPlayerオブジェクトの適切なコピーが行われていないため、SimulationStateから呼び出すように修正すべき
        // このメソッドはMinMaxクラスの直下にあり、SimulationStateの内部状態にアクセスできないため、
        // SimulationState内部に同様のメソッドを実装するか、MinMaxのminimaxから直接呼び出すgetAllPossibleMovesForMinimaxに含めるべき。
        // 現在のisCheckMateで使用されているgetAllPossibleMovesWithCurrentStateは、
        // boardとplayer (currentPlayingPlayer) を引数に取っているので、tempPlayerA/Bの生成方法を修正する。

        // isCheckMateのコンテキストでは、playerAとplayerBが引数として渡されるので、それらを使う。
        // ここはMinMaxクラスのメソッドなので、SimulationStateの内部的なPlayerインスタンスを直接参照できない。
        // このisValidMoveInSimulationはSimulationStateの内部メソッドとして実装するのが最も自然。

        // 応急処置として、MinMaxのchooseMoveで使われるgetAllPossibleMovesと、
        // minimaxやisCheckMateで使われるgetAllPossibleMovesForSimulationの2種類に分ける。
        // このisValidMoveInSimulationは、後者のgetAllPossibleMovesForSimulation内で使われる。

        // Temporarily create a minimal SimulationState to check validity.
        // This is a bit convoluted but necessary given the constraint.
        SimulationState tempState;
        if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
             tempState = new SimulationState(board.clone(), currentPlayer.clone(), new MinMax("dummy").clone(), currentPlayer.getPlayerType()); // ダミーのPlayerB
             tempState.playerB.setPlayerType(PlayerType.PLAYER2); // ダミーにもタイプ設定
        } else {
             tempState = new SimulationState(board.clone(), new MinMax("dummy").clone(), currentPlayer.clone(), currentPlayer.getPlayerType()); // ダミーのPlayerA
             tempState.playerA.setPlayerType(PlayerType.PLAYER1); // ダミーにもタイプ設定
        }

        // 駒の移動
        Piece movedPiece = tempState.board.getPiece(fromRow, fromCol);
        Piece capturedPiece = tempState.board.getPiece(toRow, toCol);

        tempState.board.removePiece(fromRow, fromCol);
        tempState.board.placePiece(movedPiece, toRow, toCol);

        if (capturedPiece != null) {
            // シミュレーションなので、仮で捕獲駒を追加
            if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
                tempState.playerA.addCapturedPiece(capturedPiece);
            } else {
                tempState.playerB.addCapturedPiece(capturedPiece);
            }
        }
        
        return !tempState.isCheck(currentPlayer.getPlayerType());
    }

    // シミュレーション内での手駒を打つ合法性チェック（王手にならないか）
    private boolean isValidDropInSimulation(Board board, Player currentPlayer, Piece pieceToDrop, int toRow, int toCol) {
        Board tempBoard = board.clone();
        // HACK: isValidMoveInSimulationと同様の問題。SimulationStateに実装すべき。

        SimulationState tempState;
        if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
             tempState = new SimulationState(board.clone(), currentPlayer.clone(), new MinMax("dummy").clone(), currentPlayer.getPlayerType());
             tempState.playerB.setPlayerType(PlayerType.PLAYER2);
        } else {
             tempState = new SimulationState(board.clone(), new MinMax("dummy").clone(), currentPlayer.clone(), currentPlayer.getPlayerType());
             tempState.playerA.setPlayerType(PlayerType.PLAYER1);
        }

        // 手駒を打つ
        tempState.board.placePiece(pieceToDrop.clone(), toRow, toCol); // クローンを置く

        // 手駒リストからの削除はapplyMoveで行われるため、ここでは行わない
        // ここでのチェックは「その場所に打った結果、王手にならないか」のみ
        
        return !tempState.isCheck(currentPlayer.getPlayerType());
    }


    // トライ勝利判定
    private boolean isTrialWin(Board board, PlayerType playerType, PlayerType opponentPlayerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) return false;

        // PLAYER1のライオンが敵陣最奥 (Row 3) にいるか
        if (playerType == PlayerType.PLAYER1 && lionPos[0] == 3) {
            // ライオンが敵陣最奥にいて、かつ次に相手に取られないかを確認
            return !isSquareAttacked(board, lionPos[0], lionPos[1], opponentPlayerType);
        }
        // PLAYER2のライオンが敵陣最奥 (Row 0) にいるか
        if (playerType == PlayerType.PLAYER2 && lionPos[0] == 0) {
            // ライオンが敵陣最奥にいて、かつ次に相手に取られないかを確認
            return !isSquareAttacked(board, lionPos[0], lionPos[1], opponentPlayerType);
        }
        return false;
    }

    // ライオン接近ペナルティの計算
    private int getLionProximityPenalty(Board board, int r, int c, PlayerType opponentPlayerType) {
        int penalty = 0;
        for (int lr = 0; lr < Board.ROWS; lr++) {
            for (int lc = 0; lc < Board.COLS; lc++) {
                Piece lion = board.getPiece(lr, lc);
                if (lion != null && lion instanceof Lion && lion.getOwner() == opponentPlayerType) {
                    // 自分の駒が敵ライオンに近づくとペナルティ
                    int distance = Math.abs(r - lr) + Math.abs(c - lc);
                    if (distance <= 2) { // 2マス以内
                        penalty += (3 - distance) * 50; // 近いほどペナルティ大
                    }
                }
            }
        }
        return penalty;
    }


    // chooseMoveから呼ばれるソート用メソッド
    private void sortMoves(List<int[]> moves, Board board, PlayerType playerType, Game game) {
        Collections.sort(moves, (move1, move2) -> {
            // move1とmove2の評価点（仮）を比較して並べ替え
            // ここでは、単純に駒を取れる手を優先する
            
            // 簡易的な評価でソート (完全な評価は重すぎるため)
            int score1 = 0;
            int score2 = 0;

            // move1の評価: 一時的なGame状態を作成して適用
            Game tempGame1 = game.clone(); // Gameのcloneメソッドを使用
            applyMoveToGame(tempGame1, move1);
            score1 = evaluate(tempGame1.getBoard(), playerType, tempGame1.getPlayerA(), tempGame1.getPlayerB());

            // move2の評価: 一時的なGame状態を作成して適用
            Game tempGame2 = game.clone(); // Gameのcloneメソッドを使用
            applyMoveToGame(tempGame2, move2);
            score2 = evaluate(tempGame2.getBoard(), playerType, tempGame2.getPlayerA(), tempGame2.getPlayerB());

            // 降順にソート (評価が高い方が先)
            return Integer.compare(score2, score1);
        });
    }

    // sortMoves内でGameインスタンスに手を適用するためのヘルパー
    private void applyMoveToGame(Game game, int[] move) {
        if (move[0] == -1) { // 打ち込みの場合
            // 手駒リストから対応する駒を見つける
            Piece pieceToDrop = null;
            Player currentPlayer = game.getCurrentPlayer(); // Gameが持つcurrentPlayer
            List<Piece> captured = currentPlayer.getCapturedPieces();
            if (move[1] >= 0 && move[1] < captured.size()) {
                 pieceToDrop = captured.get(move[1]);
            }
            if (pieceToDrop != null) {
                game.performDrop(pieceToDrop, move[2], move[3]);
            }
        } else { // 移動の場合
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }


    // minimaxから呼ばれるソート用メソッド
    private void sortMovesForSimulation(List<int[]> moves, SimulationState state) {
        Collections.sort(moves, (move1, move2) -> {
            int score1 = 0;
            int score2 = 0;

            // move1の評価
            SimulationState tempState1 = state.clone();
            tempState1.applyMove(move1);
            score1 = evaluate(tempState1.board, state.currentPlayerType, tempState1.playerA, tempState1.playerB);

            // move2の評価
            SimulationState tempState2 = state.clone();
            tempState2.applyMove(move2);
            score2 = evaluate(tempState2.board, state.currentPlayerType, tempState2.playerA, tempState2.playerB);

            // 降順にソート (評価が高い方が先)
            return Integer.compare(score2, score1);
        });
    }


    // ライオンが捕獲されたかどうかの判定（isCheckMateと似ているが、直接ライオンが盤面にいないかをチェック）
    private boolean isLionCaptured(Board board, PlayerType lionOwner) {
        return findLion(board, lionOwner) == null;
    }


    // --------------------------------------------------------------------------
    // SimulationState: MinMax探索用の状態を保持する内部クラス
    // --------------------------------------------------------------------------
    private class SimulationState implements Cloneable {
        public Board board;
        public Player playerA; // PLAYER1
        public Player playerB; // PLAYER2
        public PlayerType currentPlayerType; // 現在手番のプレイヤー

        public SimulationState(Board board, Player playerA, Player playerB, PlayerType currentPlayerType) {
            this.board = board;
            this.playerA = playerA;
            this.playerB = playerB;
            this.currentPlayerType = currentPlayerType;
        }

        // 手を状態に適用する
        public void applyMove(int[] move) {
            Player currentPlayer = (this.currentPlayerType == PlayerType.PLAYER1) ? playerA : playerB;
            Player opponentPlayer = (this.currentPlayerType == PlayerType.PLAYER1) ? playerB : playerA;

            if (move[0] == -1) { // 打ち込みの場合
                // 手駒リストのインデックスから駒を取得し、手駒から削除
                Piece pieceToDrop = currentPlayer.getCapturedPieces().remove(move[1]); 
                board.placePiece(pieceToDrop, move[2], move[3]);
            } else { // 移動の場合
                Piece movedPiece = board.getPiece(move[0], move[1]);
                Piece capturedPiece = board.getPiece(move[2], move[3]);

                board.removePiece(move[0], move[1]);
                
                // 成りの判定と適用
                if (movedPiece instanceof Hiyoko && !movedPiece.isPromoted()) {
                    // PLAYER1のひよこが敵陣最奥 (Row 3) に移動
                    if (this.currentPlayerType == PlayerType.PLAYER1 && move[2] == 3) {
                        movedPiece.promote();
                    }
                    // PLAYER2のひよこが敵陣最奥 (Row 0) に移動
                    else if (this.currentPlayerType == PlayerType.PLAYER2 && move[2] == 0) {
                        movedPiece.promote();
                    }
                }

                board.placePiece(movedPiece, move[2], move[3]);

                if (capturedPiece != null) {
                    opponentPlayer.addCapturedPiece(capturedPiece); // 捕獲した駒を相手の手駒に追加
                }
            }
            // プレイヤーの切り替え
            this.currentPlayerType = getOpponentPlayerType(this.currentPlayerType);
        }

        // SimulationState内で使う、合法手生成メソッド (王手にならない手のみ)
        public List<int[]> getLegalMovesInSimulation() {
            List<int[]> legalMoves = new ArrayList<>();
            Player currentPlayer = (this.currentPlayerType == PlayerType.PLAYER1) ? playerA : playerB;

            // 1. 盤上の駒の移動に関する合法手を収集
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = board.getPiece(r, c);
                    if (piece != null && piece.getOwner() == this.currentPlayerType) {
                        List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                        for (int[] move : movesForPiece) {
                            // 手を適用して王手にならないかチェック
                            if (isValidMoveInSimulation(r, c, move[0], move[1])) {
                                legalMoves.add(new int[]{r, c, move[0], move[1]});
                            }
                        }
                    }
                }
            }

            // 2. 手駒を打つ合法手を収集
            List<Piece> captured = currentPlayer.getCapturedPieces();
            for (int i = 0; i < captured.size(); i++) {
                Piece pieceToDrop = captured.get(i);
                for (int r = 0; r < Board.ROWS; r++) {
                    for (int c = 0; c < Board.COLS; c++) {
                        // 空いているマスであれば打てる AND その手が王手にならないかチェック
                        if (board.isEmpty(r, c) && isValidDropInSimulation(pieceToDrop, r, c)) {
                            legalMoves.add(new int[]{-1, i, r, c});
                        }
                    }
                }
            }
            return legalMoves;
        }

        // シミュレーション内での駒の移動の合法性チェック（王手にならないか）
        private boolean isValidMoveInSimulation(int fromRow, int fromCol, int toRow, int toCol) {
            SimulationState tempState = this.clone(); // 現在の状態をクローン
            
            // 駒の移動を実行 (ここでは手駒の増減は考慮しない)
            Piece movedPiece = tempState.board.getPiece(fromRow, fromCol);
            Piece capturedPiece = tempState.board.getPiece(toRow, toCol); // 捕獲される駒

            tempState.board.removePiece(fromRow, fromCol);
            tempState.board.placePiece(movedPiece, toRow, toCol);

            // 捕獲駒があれば、仮で相手の手駒に追加（isCheckの判定には直接影響しないが、状態の一貫性のため）
            if (capturedPiece != null) {
                Player opponent = (this.currentPlayerType == PlayerType.PLAYER1) ? tempState.playerB : tempState.playerA;
                opponent.addCapturedPiece(capturedPiece); // 相手のCapturedPiecesに追加
            }
            
            // 移動後の盤面で、自分のライオンが王手でないかを確認
            return !tempState.isCheck(this.currentPlayerType);
        }

        // シミュレーション内での手駒を打つ合法性チェック（王手にならないか）
        private boolean isValidDropInSimulation(Piece pieceToDrop, int toRow, int toCol) {
            SimulationState tempState = this.clone(); // 現在の状態をクローン

            // 手駒を打つ
            tempState.board.placePiece(pieceToDrop.clone(), toRow, toCol); // クローンを置く

            // 手駒リストからの削除はapplyMoveで行われるため、ここでは行わない
            // ここでのチェックは「その場所に打った結果、王手にならないか」のみ
            
            return !tempState.isCheck(this.currentPlayerType);
        }


        // ゲームが終了したかを判定 (詰み、トライ勝利)
        public PlayerType isGameOver() {
            // 詰み判定
            if (isCheckMate(board, PlayerType.PLAYER1, playerA, playerB)) {
                return PlayerType.PLAYER2; // PLAYER1が詰んだのでPLAYER2の勝利
            }
            if (isCheckMate(board, PlayerType.PLAYER2, playerA, playerB)) {
                return PlayerType.PLAYER1; // PLAYER2が詰んだのでPLAYER1の勝利
            }

            // トライ勝利判定
            if (isTrialWin(board, PlayerType.PLAYER1, PlayerType.PLAYER2)) {
                return PlayerType.PLAYER1;
            }
            if (isTrialWin(board, PlayerType.PLAYER2, PlayerType.PLAYER1)) {
                return PlayerType.PLAYER2;
            }
            
            return null; // ゲームはまだ終了していない
        }

        // 王手判定（指定されたプレイヤーが王手されているか）
        public boolean isCheck(PlayerType checkedPlayerType) {
            int[] lionPos = findLion(board, checkedPlayerType);
            if (lionPos == null) return false; // ライオンがいなければ王手ではない

            PlayerType opponentPlayerType = (checkedPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
            return isSquareAttacked(board, lionPos[0], lionPos[1], opponentPlayerType);
        }


        // ライオンの位置を探す (SimulationStateの内部メソッド)
        private int[] findLion(Board board, PlayerType playerType) {
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

        // 指定された座標が攻撃範囲内にあるかチェックするヘルパーメソッド
        private boolean isSquareAttacked(Board board, int targetRow, int targetCol, PlayerType attackingPlayerType) {
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

        // プレイヤータイプから相手のプレイヤータイプを取得 (SimulationStateの内部メソッド)
        private PlayerType getOpponentPlayerType(PlayerType playerType) {
            return (playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        }

        @Override
        public SimulationState clone() {
            try {
                SimulationState cloned = (SimulationState) super.clone();
                cloned.board = this.board.clone(); // Boardもディープコピー
                cloned.playerA = this.playerA.clone(); // Playerもディープコピー
                cloned.playerB = this.playerB.clone(); // Playerもディープコピー
                // currentPlayerType はプリミティブなのでそのままコピーされる
                return cloned;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(); // 発生しないはず
            }
        }
    }
}