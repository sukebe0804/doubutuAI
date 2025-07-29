import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class AI_gj extends Player {
    // 探索の深さ
    private static final int MAX_DEPTH = 5; 

    // 駒の基本価値
    private static final int LION_VALUE = 10000; // ライオンの価値を高く設定（詰み/トライはこれよりはるかに高い）
    private static final int KIRIN_VALUE = 300;
    private static final int ZOU_VALUE = 300;
    private static final int HIYOKO_VALUE = 100;
    private static final int NIWATORI_VALUE = 200; // 成ったひよこの価値を高く設定

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
        {50, 50, 50},    // Row 0
        {30, 30, 30}, // Row 1 (中央に近づく、前進)
        {20, 20, 20}, // Row 2 (敵陣に近い)
        {20, 0, 20}  // Row 3
    };

    private static final int[][] PST_NIWATORI = {
        {20, 20, 20}, // Row 0
        {40, 40, 40}, // Row 1 (中央)
        {10, 10, 10}, // Row 2 (中央)
        {0, 0, 0}  // Row 3
    };

    private static final int[][] PST_KIRIN = {
        {0, 0, 0}, // Row 0
        {20, 20, 25},   // Row 1
        {50, 25, 25},   // Row 2
        {10, 10, 0}  // Row 3
    };

    private static final int[][] PST_ZOU = {
        {0, 5, 0},  // Row 0
        {50, 25, 50},   // Row 1
        {25, 300, 25},   // Row 2
        {0, 0, 50}   // Row 3
    };

    private static final int[][] PST_LION = {
        {500, 500, 500}, // Row 0 (敵陣奥は危険)
        {100, 50, 100},  // Row 1
        {50, 200, 50},     // Row 2 (やや安全な自陣)
        {100, 100, 100}   // Row 3 (自陣奥、初期位置付近)
    };

    private Random random; // 同じ評価値の時にランダムに選択するため

    public AI_gj(String name) {
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

            // クローンされたゲームで手を適用
            // ここでapplyMoveを呼び出すように変更
            applyMove(simulatedGame, move);

            // ミニマックス法で評価
            int eval = minimax(simulatedGame, MAX_DEPTH - 1, alpha, beta, simulatedGame.getCurrentPlayer().getPlayerType());

            // 最善手の更新
            if (this.playerType == PlayerType.PLAYER1) { // 探索中のAIがPLAYER1 (Maximizing Player) の場合
                if (eval > bestScore) {
                    bestScore = eval;
                    bestMove = move;
                    equallyGoodMoves.clear(); // より良い手が見つかったのでクリア
                    equallyGoodMoves.add(move);
                } else if (eval == bestScore) {
                    equallyGoodMoves.add(move); // 同じ評価値なら追加
                }
                alpha = Math.max(alpha, eval); // Alpha値の更新
            } else { // 探索中のAIがPLAYER2 (Minimizing Player) の場合
                if (eval < bestScore) {
                    bestScore = eval;
                    bestMove = move;
                    equallyGoodMoves.clear(); // より良い手が見つかったのでクリア
                    equallyGoodMoves.add(move);
                } else if (eval == bestScore) {
                    equallyGoodMoves.add(move); // 同じ評価値なら追加
                }
                beta = Math.min(beta, eval); // Beta値の更新
            }
        }

        // 同じ評価値の手が複数ある場合、ランダムに選択
        if (!equallyGoodMoves.isEmpty()) {
            return equallyGoodMoves.get(random.nextInt(equallyGoodMoves.size()));
        }

        return bestMove; // 最善手を返す
    }

    private int minimax(Game game, int depth, int alpha, int beta, PlayerType maximizingPlayer) {
        // ゲーム終了判定
        PlayerType gameResult = game.isGameOver();
        if (gameResult != null) {
            if (gameResult == this.playerType) { // 探索中のAIが勝つ場合 (詰み、トライなど)
                return Integer.MAX_VALUE - (MAX_DEPTH - depth); // 深さが浅いほど高い評価
            } else { // 相手が勝つ場合
                return Integer.MIN_VALUE + (MAX_DEPTH - depth); // 深さが浅いほど低い評価
            }
        }

        if (depth == 0) {
            return evaluate(game); // 探索深さに到達したら盤面を評価
        }

        // 手を生成するプレイヤーは、現在のPlayerではなく、minimaxを呼び出した次のPlayer
        PlayerType playerToMove = game.getCurrentPlayer().getPlayerType();
        List<int[]> possibleMoves = generateAllPossibleMoves(game);

        if (possibleMoves.isEmpty()) {
            // 合法手がない場合、詰みや行き詰まりなので現在の評価を返す (Game.isGameOverで処理済みの場合もある)
            // この場合、そのプレイヤーが動けないため、負けに近い評価を返す
            if (playerToMove == this.playerType) { // 自分の番で合法手がない = 負けに近い
                return Integer.MIN_VALUE + (MAX_DEPTH - depth);
            } else { // 相手の番で合法手がない = 勝ちに近い
                return Integer.MAX_VALUE - (MAX_DEPTH - depth);
            }
        }

        if (playerToMove == this.playerType) { // Maximizing Player (AI_gj自身)
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                newGame.setSilentMode(true);
                // ここでapplyMoveを呼び出すように変更
                applyMove(newGame, move);
                int eval = minimax(newGame, depth - 1, alpha, beta, newGame.getCurrentPlayer().getPlayerType());
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval); // Alpha値の更新
                if (beta <= alpha) {
                    break; // βカット
                }
            }
            return maxEval;
        } else { // Minimizing Player (相手)
            int minEval = Integer.MAX_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                newGame.setSilentMode(true);
                // ここでapplyMoveを呼び出すように変更
                applyMove(newGame, move);
                int eval = minimax(newGame, depth - 1, alpha, beta, newGame.getCurrentPlayer().getPlayerType());
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval); // Beta値の更新
                if (beta <= alpha) {
                    break; // αカット
                }
            }
            return minEval;
        }
    }

    /**
     * 現在の盤面を評価する（AI_gjの視点から）
     * 正の値はAI_gjにとって有利、負の値はAI_gjにとって不利
     * @param game 現在のゲーム状態
     * @return 盤面の評価値
     */
    private int evaluate(Game game) {
        int score = 0;
        Board board = game.getBoard();

        // 駒の価値と配置点テーブルの評価
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null) {
                    int pieceValue = 0;
                    int[][] pst = null;
                    if (p instanceof Lion) {
                        pieceValue = LION_VALUE;
                        pst = PST_LION;
                    } else if (p instanceof Kirin) {
                        pieceValue = KIRIN_VALUE;
                        pst = PST_KIRIN;
                    } else if (p instanceof Zou) {
                        pieceValue = ZOU_VALUE;
                        pst = PST_ZOU;
                    } else if (p instanceof Hiyoko) {
                        if (p.isPromoted()) {
                            pieceValue = NIWATORI_VALUE;
                            pst = PST_NIWATORI;
                        } else {
                            pieceValue = HIYOKO_VALUE;
                            pst = PST_HIYOKO;
                        }
                    }

                    int adjustedRow = (p.getOwner() == PlayerType.PLAYER1) ? r : (Board.ROWS - 1 - r);
                    int positionBonus = (pst != null) ? pst[adjustedRow][c] : 0;

                    if (p.getOwner() == this.playerType) {
                        score += pieceValue + positionBonus;
                    } else {
                        score -= (pieceValue + positionBonus);
                    }
                }
            }
        }

        // ライオンの安全度と相手ライオンへの攻撃
        int myLionRow = -1, myLionCol = -1;
        int opponentLionRow = -1, opponentLionCol = -1;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p instanceof Lion) {
                    if (p.getOwner() == this.playerType) {
                        myLionRow = r;
                        myLionCol = c;
                    } else {
                        opponentLionRow = r;
                        opponentLionCol = c;
                    }
                }
            }
        }

        // 自分のライオンの安全度
        if (myLionRow != -1) {
            // ライオンが危険な位置にいる場合（敵陣奥など）のペナルティ
            // PLAYER1の場合、row 0が敵陣奥
            // PLAYER2の場合、row 3が敵陣奥
            if (this.playerType == PlayerType.PLAYER1 && myLionRow == 0) {
                score -= LION_ATTACK_PENALTY_PER_ATTACKER * 5; // 敵陣最奥へのペナルティを強化
            } else if (this.playerType == PlayerType.PLAYER2 && myLionRow == (Board.ROWS - 1)) {
                score -= LION_ATTACK_PENALTY_PER_ATTACKER * 5;
            }

            // ライオンを守る味方駒の数に応じたボーナス
            score += countNeighboringPieces(board, myLionRow, myLionCol, this.playerType) * LION_SAFETY_BONUS_PER_DEFENDER;

            // 相手の攻撃駒からの脅威に対するペナルティ
            score -= countAttackingPieces(game, board, myLionRow, myLionCol, this.playerType) * LION_ATTACK_PENALTY_PER_ATTACKER;
        }

        // 相手ライオンへの攻撃度
        if (opponentLionRow != -1) {
            score += countAttackingPieces(game, board, opponentLionRow, opponentLionCol, this.playerType == PlayerType.PLAYER1 ? PlayerType.PLAYER2 : PlayerType.PLAYER1) * ATTACKED_OPPONENT_PIECE_BONUS;
        }


        // 手駒の評価（手駒が多いほど有利）
        // AI_gjにとっての手駒
        score += this.getCapturedPieces().size() * 5; // 手駒1つあたりのボーナスを調整

        // 相手にとっての手駒
        Player opponentPlayer = (this.playerType == PlayerType.PLAYER1) ? game.getPlayerB() : game.getPlayerA();
        score -= opponentPlayer.getCapturedPieces().size() * 5; // 相手の手駒1つあたりのペナルティを調整


        // モビリティ（動かせる手の数）
        score += generateAllPossibleMoves(game).size() * MOBILITY_BONUS_PER_MOVE;

        // 自分の駒が攻撃されている場合のペナルティ
        // 相手の駒が自分の駒を攻撃しているかチェック
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getOwner() == this.playerType && isAttackedByOpponent(game, board, r, c, this.playerType)) {
                    score -= ATTACKED_OWN_PIECE_PENALTY;
                }
            }
        }

        // 自分の駒が守られている場合のボーナス (ライオン以外)
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == this.playerType && !(piece instanceof Lion) && isDefendedByOwnPiece(game, board, r, c, this.playerType)) {
                    score += DEFENDED_OWN_PIECE_BONUS;
                }
            }
        }

        // 中央支配ボーナス (簡易版)
        // 中央に近いマスに駒がある場合のボーナス
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    // 盤面中央に近いほどボーナス
                    int centerDist = Math.abs(r - (Board.ROWS / 2 - 1)) + Math.abs(c - (Board.COLS / 2 - 1)); // 盤面中心からの距離 (簡易計算)
                    score += (3 - centerDist) * CENTER_CONTROL_BONUS; // 距離が小さいほどボーナス大
                }
            }
        }

        // ひよこが敵陣最奥にいる場合のボーナス
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p instanceof Hiyoko && p.getOwner() == this.playerType) {
                    if (this.playerType == PlayerType.PLAYER1 && r == (Board.ROWS - 1)) { // Player1のひよこが敵陣最奥に到達
                        score += PROMOTION_THREAT_BONUS;
                    } else if (this.playerType == PlayerType.PLAYER2 && r == 0) { // Player2のひよこが敵陣最奥に到達
                        score += PROMOTION_THREAT_BONUS;
                    }
                }
            }
        }
        
        return score;
    }

    // 周囲の味方駒の数を数えるヘルパーメソッド (ライオンの安全度ボーナス用)
    private int countNeighboringPieces(Board board, int row, int col, PlayerType ownerType) {
        int count = 0;
        int[][] deltas = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, // 上下左右
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1} // 斜め
        };
        for (int[] delta : deltas) {
            int newRow = row + delta[0];
            int newCol = col + delta[1];
            if (board.isValidCoordinate(newRow, newCol)) {
                Piece p = board.getPiece(newRow, newCol);
                if (p != null && p.getOwner() == ownerType) {
                    count++;
                }
            }
        }
        return count;
    }

    // 特定のマスが相手の駒によって攻撃されているかチェックするヘルパーメソッド
    private boolean isAttackedByOpponent(Game game, Board board, int targetRow, int targetCol, PlayerType ownPlayerType) {
        PlayerType opponentPlayerType = (ownPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getOwner() == opponentPlayerType) {
                    // 相手の駒の全ての可能な移動先をチェック
                    List<int[]> possibleMoves = p.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // move[0]はターゲット行、move[1]はターゲット列
                        if (move[0] == targetRow && move[1] == targetCol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // 特定のマスにいる自分の駒が味方の駒によって守られているかチェックするヘルパーメソッド
    private boolean isDefendedByOwnPiece(Game game, Board board, int targetRow, int targetCol, PlayerType ownPlayerType) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getOwner() == ownPlayerType) {
                    // 自分の駒の全ての可能な移動先をチェック
                    List<int[]> possibleMoves = p.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // move[0]はターゲット行、move[1]はターゲット列
                        if (move[0] == targetRow && move[1] == targetCol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // 特定のマスを攻撃している敵駒の数を数えるヘルパーメソッド (追加)
    private int countAttackingPieces(Game game, Board board, int targetRow, int targetCol, PlayerType targetPieceOwnerType) {
        int count = 0;
        PlayerType opponentPlayerType = (targetPieceOwnerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getOwner() == opponentPlayerType) {
                    List<int[]> possibleMoves = p.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // move[0]はターゲット行、move[1]はターゲット列
                        if (move[0] == targetRow && move[1] == targetCol) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    // すべての合法手を生成するメソッド
    private List<int[]> generateAllPossibleMoves(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        Board currentBoard = game.getBoard();
        PlayerType myPlayerType = this.playerType;

        // 1. 盤面上の駒の合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = currentBoard.getPiece(r, c);
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> possibleMovesForPiece = piece.getPossibleMoves(r, c, currentBoard);
                    for (int[] move : possibleMovesForPiece) {
                    // GameクラスのisValidMoveAndNotIntoCheckメソッドを直接利用。
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
                    // GameクラスのisValidDropAndNotIntoCheckメソッドを直接利用。
                    if (currentBoard.isEmpty(r, c) && game.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
                        // 手駒を打つ手は、特別な形式でリストに追加 { -1, 手駒リストのインデックス, 落とす行, 落とす列 }
                        allPossibleMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        return allPossibleMoves;
    }

    // clone() メソッド
    @Override
    public AI_gj clone() {
        // Playerクラスのcloneを呼び出し、AI_gjにキャスト
        AI_gj cloned = (AI_gj) super.clone();
        // random オブジェクトは新規作成 (状態を持たないので既存のコピーは不要)
        cloned.random = new Random();
        return cloned;
    }

    // 新しいヘルパーメソッド: 手のタイプに応じてGameクラスの適切なメソッドを呼び出す
    private void applyMove(Game game, int[] move) {
        // move配列の形式をチェック:
        // 通常の移動: { fromRow, fromCol, toRow, toCol }
        // 手駒を打つ: { -1, capturedPieceIndex, toRow, toCol }
        if (move[0] == -1) { // 手駒を打つ手の場合
            // Player.javaのcapturedPiecesはディープコピーされている想定
            // applyMoveが呼ばれる時点でgame.getCurrentPlayer()は正しいプレイヤーになっている
            // ただし、このメソッドはシミュレーション用であり、直接game.getCurrentPlayer()から
            // 手駒を取得すると、Minimaxの深さによっては意図しないPlayerの手駒を参照する可能性がある。
            // そのため、ここでは chooseMove のループ内で取得した capturedPieces のインデックスを
            // 信頼して、現在のゲーム状態のプレイヤーから手駒を取得する。
            
            // 重要: minimax内でgame.clone()しているので、simulatedGameのcapturedPiecesから取得する
            // simulatedGame.getCurrentPlayer()が正しいプレイヤーを指していることを前提とする
            // chooseMoveやminimaxでgame.clone()後にcurrentPlayerが切り替わるため、
            // newGame.getCurrentPlayer() から手駒を取得するのが適切
            Piece pieceToDrop = game.getCurrentPlayer().getCapturedPieces().get(move[1]);
            game.makeDrop(pieceToDrop, move[2], move[3]);
        } else { // 通常の移動の場合
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }
}