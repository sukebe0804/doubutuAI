// MinMax.java の修正

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MinMax extends Player {
    // 探索の深さ
    private static final int MAX_DEPTH = 10; // 探索深度を調整（計算資源と相談）

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
        {40, 50, 40}  // Row 3 (PLAYER1の初期配置側)
    };

    private static final int[][] PST_KIRIN = {
        {20, 30, 20}, // Row 0
        {40, 50, 40},   // Row 1
        {60, 70, 60},   // Row 2
        {30, 40, 30}  // Row 3
    };

    private static final int[][] PST_ZOU = {
        {20, 30, 20},  // Row 0
        {40, 50, 40},   // Row 1
        {60, 70, 60},   // Row 2
        {30, 40, 30}   // Row 3
    };

    private static final int[][] PST_LION = {
        {10, 10, 10}, // Row 0 (敵陣奥は危険だが、トライの可能性)
        {20, 30, 20},  // Row 1
        {40, 50, 40},     // Row 2 (安全な自陣)
        {60, 70, 60}   // Row 3 (自陣奥、初期位置付近 - 安全性が高い)
    };

    private Random random;

    public MinMax(String name) {
        super(name);
        this.random = new Random();
    }

    // AIが実際に手を指すためのメソッド
    @Override
    public int[] chooseMove(Game game) {
        int bestScore = (this.playerType == PlayerType.PLAYER1) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] bestMove = null;
        List<int[]> equallyGoodMoves = new ArrayList<>();

        // 現在のゲーム状態をMinMax内部のシミュレーション用に変換
        // ここでGameクラスから取得したプレイヤーオブジェクトのクローンをSimulationStateに渡す
        SimulationState initialState = new SimulationState(game.getBoard().clone(), game.getPlayerA().clone(), game.getPlayerB().clone(), game.getCurrentPlayer().getPlayerType());

        // シミュレーション状態のgenerateAllPossibleMovesを呼び出す
        List<int[]> possibleMoves = initialState.generateAllPossibleMoves();

        if (possibleMoves.isEmpty()) {
            return null;
        }

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (int[] move : possibleMoves) {
            SimulationState nextState = initialState.clone();
            boolean moveSuccessful = false;

            // シミュレーション用の手実行メソッドを呼び出す
            if (move.length == 4) { // 駒の移動 {fromRow, fromCol, toRow, toCol}
                moveSuccessful = nextState.makeMove(nextState.currentPlayerType, move[0], move[1], move[2], move[3]);
            } else if (move.length == 3 && move[0] == -1) { // 手駒を打つ { -1, capturedPieceIndex, toRow, toCol }
                Player player = (nextState.currentPlayerType == PlayerType.PLAYER1) ? nextState.playerA : nextState.playerB;
                Piece pieceToDrop = player.getCapturedPieces().get(move[1]);
                moveSuccessful = nextState.makeDrop(nextState.currentPlayerType, pieceToDrop, move[2], move[3]);
            }

            if (moveSuccessful) {
                // 手を実行した後、プレイヤーを切り替える
                nextState.switchPlayer();

                // minimax呼び出し時に、currentPlayerTypeを次に手を指すプレイヤーのタイプで渡す
                int score = minimax(nextState, MAX_DEPTH - 1, alpha, beta, nextState.currentPlayerType);

                if (this.playerType == PlayerType.PLAYER1) { // Maximizing Player (AI自身)
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = move;
                        equallyGoodMoves.clear();
                        equallyGoodMoves.add(move);
                    } else if (score == bestScore) {
                        equallyGoodMoves.add(move);
                    }
                    alpha = Math.max(alpha, bestScore);
                } else { // Minimizing Player (AI自身がPLAYER2の場合)
                    if (score < bestScore) {
                        bestScore = score;
                        bestMove = move;
                        equallyGoodMoves.clear();
                        equallyGoodMoves.add(move);
                    } else if (score == bestScore) {
                        equallyGoodMoves.add(move);
                    }
                    beta = Math.min(beta, bestScore);
                }
                if (beta <= alpha) {
                    break; // α-βカット
                }
            }
        }
        if (!equallyGoodMoves.isEmpty()) {
            return equallyGoodMoves.get(random.nextInt(equallyGoodMoves.size()));
        }
        return bestMove;
    }

    /**
     * ミニマックス法（アルファベータ枝刈り付き）
     * @param state 現在のシミュレーション状態
     * @param depth 現在の探索深さ
     * @param alpha アルファ値
     * @param beta ベータ値
     * @param currentPlayerType 現在のプレイヤーのタイプ (Minimaxが評価している手番のプレイヤー)
     * @return 評価値
     */
    private int minimax(SimulationState state, int depth, int alpha, int beta, PlayerType currentPlayerType) {
        // 終端ノード（ゲーム終了または探索深さに到達）
        if (depth == 0 || state.isGameOver() != null) {
            return state.evaluateBoard(this.playerType); // ここで MinMax の評価関数を呼び出す
        }

        // 現在のプレイヤーがMaximizing Player (自分) の場合
        if (currentPlayerType == this.playerType) { // この手番のプレイヤーがAI自身の場合
            int maxEval = Integer.MIN_VALUE;
            List<int[]> possibleMoves = state.generateAllPossibleMoves();
            Collections.shuffle(possibleMoves); // 探索の多様性を増すため

            for (int[] move : possibleMoves) {
                SimulationState nextState = state.clone();
                boolean moveSuccessful = false;

                if (move.length == 4) { // 駒の移動 {fromRow, fromCol, toRow, toCol}
                    moveSuccessful = nextState.makeMove(nextState.currentPlayerType, move[0], move[1], move[2], move[3]);
                } else if (move.length == 3 && move[0] == -1) { // 手駒を打つ { -1, capturedPieceIndex, toRow, toCol }
                    Player player = (nextState.currentPlayerType == PlayerType.PLAYER1) ? nextState.playerA : nextState.playerB;
                    // capturedPieceIndex を使用して pieceToDrop を取得
                    Piece pieceToDrop = player.getCapturedPieces().get(move[1]);
                    moveSuccessful = nextState.makeDrop(nextState.currentPlayerType, pieceToDrop, move[2], move[3]);
                }

                if (moveSuccessful) {
                    nextState.switchPlayer();
                    int eval = minimax(nextState, depth - 1, alpha, beta, nextState.currentPlayerType);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) {
                        break; // α-βカット
                    }
                }
            }
            return maxEval;
        }
        // 現在のプレイヤーがMinimizing Player (相手) の場合
        else { // この手番のプレイヤーが相手の場合
            int minEval = Integer.MAX_VALUE;
            List<int[]> possibleMoves = state.generateAllPossibleMoves();
            Collections.shuffle(possibleMoves); // 探索の多様性を増すため

            for (int[] move : possibleMoves) {
                SimulationState nextState = state.clone();
                boolean moveSuccessful = false;

                if (move.length == 4) { // 駒の移動 {fromRow, fromCol, toRow, toCol}
                    moveSuccessful = nextState.makeMove(nextState.currentPlayerType, move[0], move[1], move[2], move[3]);
                } else if (move.length == 3 && move[0] == -1) { // 手駒を打つ { -1, capturedPieceIndex, toRow, toCol }
                    Player player = (nextState.currentPlayerType == PlayerType.PLAYER1) ? nextState.playerA : nextState.playerB;
                    // capturedPieceIndex を使用して pieceToDrop を取得
                    Piece pieceToDrop = player.getCapturedPieces().get(move[1]);
                    moveSuccessful = nextState.makeDrop(nextState.currentPlayerType, pieceToDrop, move[2], move[3]);
                }

                if (moveSuccessful) {
                    nextState.switchPlayer();
                    int eval = minimax(nextState, depth - 1, alpha, beta, nextState.currentPlayerType);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) {
                        break; // α-βカット
                    }
                }
            }
            return minEval;
        }
    }


    // シミュレーション用のゲーム状態を表す内部クラス
    private class SimulationState implements Cloneable {
        private Board board;
        private Player playerA; // PlayerType.PLAYER1 のプレイヤー
        private Player playerB; // PlayerType.PLAYER2 のプレイヤー
        private PlayerType currentPlayerType;

        public SimulationState(Board board, Player playerA, Player playerB, PlayerType currentPlayerType) {
            this.board = board;
            this.playerA = playerA;
            this.playerB = playerB;
            this.currentPlayerType = currentPlayerType;
        }

        public PlayerType getCurrentPlayerType() {
            return currentPlayerType;
        }

        public Board getBoard() {
            return board;
        }

        public Player getPlayerA() {
            return playerA;
        }

        public Player getPlayerB() {
            return playerB;
        }

        // 盤面の駒の移動処理
        public boolean makeMove(PlayerType playerType, int fromRow, int fromCol, int toRow, int toCol) {
            Piece piece = board.getPiece(fromRow, fromCol);
            if (piece == null || piece.getOwner() != playerType) {
                return false; // 動かす駒がないか、自分の駒ではない
            }

            // まず、元の駒が実際に移動可能かチェック
            List<int[]> possibleMoves = piece.getPossibleMoves(fromRow, fromCol, board);
            boolean isValidMoveAttempt = false;
            for (int[] move : possibleMoves) {
                if (move[0] == toRow && move[1] == toCol) {
                    isValidMoveAttempt = true;
                    break;
                }
            }
            if (!isValidMoveAttempt) {
                return false; // 指定された移動が駒の移動範囲外
            }


            // 王手になる手かどうかチェック（このチェックは、仮に移動した後の盤面で行う必要がある）
            // そのため、一旦盤面の状態を仮に変化させてからチェックする
            // **重要**: ここで nextBoard, nextPlayerA, nextPlayerB のクローンを作成し、
            // それらを使って isValidMoveAndNotIntoCheck を呼び出すべきではない。
            // isValidMoveAndNotIntoCheck は、現在の SimulationState のボードとプレイヤーを使って、
            // そのボードに仮の変更を加えてチェックするロジックになっているため。
            if (!isValidMoveAndNotIntoCheck(playerType, fromRow, fromCol, toRow, toCol)) {
                return false; // その手を指すと王手になる
            }

            // makeMove 自体は副作用を持つべきなので、isValidMoveAndNotIntoCheck でチェックを通った後、
            // 実際の駒の移動と捕獲を行う。
            Piece targetPiece = board.getPiece(toRow, toCol);
            if (targetPiece != null) {
                // 相手の駒を取る場合
                Player opponentPlayer = (playerType == PlayerType.PLAYER1) ? playerB : playerA;
                targetPiece.setOwner(playerType); // 取得した駒の所有者を自分に変える
                targetPiece.unPromote();          // 成り状態を解除する
                opponentPlayer.addCapturedPiece(targetPiece); // 相手の駒を自分の手駒に追加 (PlayerクラスのaddCapturedPieceが正しい処理を含む)
            }

            board.removePiece(fromRow, fromCol);
            board.placePiece(piece, toRow, toCol);

            // 成りの判定と処理
            if (piece instanceof Hiyoko) {
                if (playerType == PlayerType.PLAYER1 && toRow == Board.ROWS - 1) { // Player1が一番奥の列に到達
                    piece.promote();
                } else if (playerType == PlayerType.PLAYER2 && toRow == 0) { // Player2が一番奥の列に到達
                    piece.promote();
                }
            }
            return true;
        }

        // 手駒を打つ処理
        public boolean makeDrop(PlayerType playerType, Piece pieceToDrop, int toRow, int toCol) {
            if (board.getPiece(toRow, toCol) != null) {
                return false; // 既に駒があるマスには打てない
            }

            // makeDrop 自体は副作用を持つべきなので、isValidDropAndNotIntoCheck でチェックを通った後、
            // 実際の駒の配置を行う。
            board.placePiece(pieceToDrop, toRow, toCol);
            Player player = (playerType == PlayerType.PLAYER1) ? playerA : playerB;
            player.removeCapturedPiece(pieceToDrop); // 手駒から削除
            return true;
        }

        // プレイヤーを切り替える
        public void switchPlayer() {
            this.currentPlayerType = getOpponentPlayerType(this.currentPlayerType);
        }

        // ゲーム終了判定 (ライオンが取られたか、トライルール)
        public PlayerType isGameOver() {
            int[] player1LionPos = findLion(board, PlayerType.PLAYER1);
            int[] player2LionPos = findLion(board, PlayerType.PLAYER2);

            // どちらかのライオンが取られたらゲーム終了
            if (player1LionPos == null) {
                return PlayerType.PLAYER2; // Player1のライオンが取られたらPlayer2の勝ち
            }
            if (player2LionPos == null) {
                return PlayerType.PLAYER1; // Player2のライオンが取られたらPlayer1の勝ち
            }

            // トライルール判定: 相手の陣地の一番奥に自分のライオンが入ったら勝ち
            // 中央の列 (1) に限定
            if (player1LionPos[0] == Board.ROWS - 1 && player1LionPos[1] == 1) { // Player1のライオンが敵陣の一番奥の真ん中に入った
                return PlayerType.PLAYER1;
            }
            if (player2LionPos[0] == 0 && player2LionPos[1] == 1) { // Player2のライオンが敵陣の一番奥の真ん中に入った
                return PlayerType.PLAYER2;
            }
            return null; // ゲームはまだ終了していない
        }

        /**
         * 現在の盤面と手駒から、合法な全ての手（移動と打ち込み）を生成する。
         * @return 全ての合法手のリスト。各手は {fromRow, fromCol, toRow, toCol} または {-1, capturedPieceIndex, toRow, toCol} の形式。
         */
        public List<int[]> generateAllPossibleMoves() {
            List<int[]> allPossibleMoves = new ArrayList<>();
            PlayerType myPlayerType = this.currentPlayerType;

            // 1. 盤上の駒の合法手を収集
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = this.board.getPiece(r, c);
                    if (piece != null && piece.getOwner() == myPlayerType) {
                        List<int[]> possibleMovesForPiece = piece.getPossibleMoves(r, c, this.board);
                        for (int[] move : possibleMovesForPiece) {
                            // isVaildMoveAndNotIntoCheck は SimulationState のメソッドなので this でアクセス
                            if (this.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                                allPossibleMoves.add(new int[]{r, c, move[0], move[1]});
                            }
                        }
                    }
                }
            }

            // 2. 手駒を打つ合法手を収集
            Player currentPlayerObj = (myPlayerType == PlayerType.PLAYER1) ? this.playerA : this.playerB;
            List<Piece> capturedPiecesList = currentPlayerObj.getCapturedPieces();

            for (int i = 0; i < capturedPiecesList.size(); i++) {
                Piece pieceToDrop = capturedPiecesList.get(i);
                for (int r = 0; r < Board.ROWS; r++) {
                    for (int c = 0; c < Board.COLS; c++) {
                        if (this.board.isEmpty(r, c)) {
                            // 成り禁止マス（ひよこ）と二歩のチェックをここで行う
                            if (this.isDroppablePosition(pieceToDrop, r, c)) { // SimulationStateのメソッドなので this でアクセス
                                // isValidDropAndNotIntoCheck は SimulationState のメソッドなので this でアクセス
                                if (this.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
                                    allPossibleMoves.add(new int[]{-1, i, r, c});
                                }
                            }
                        }
                    }
                }
            }
            return allPossibleMoves;
        }

        // 新しいヘルパーメソッド：駒を打てる位置かどうかの基本的なチェック（成り禁止、二歩）
        private boolean isDroppablePosition(Piece piece, int toRow, int toCol) {
            // ひよこ（成らない限り）の行き過ぎた位置への打ち込み禁止
            if (piece instanceof Hiyoko && !piece.isPromoted()) {
                if (piece.getOwner() == PlayerType.PLAYER1 && toRow == Board.ROWS - 1) return false;
                if (piece.getOwner() == PlayerType.PLAYER2 && toRow == 0) return false;
            }

            // 二歩のチェック (同じ列に自分のひよこがもう一つあるか)
            if (piece instanceof Hiyoko && !piece.isPromoted()) {
                for (int r = 0; r < Board.ROWS; r++) {
                    // もし打とうとしている駒と同じタイプのひよこがその列にすでにあり、かつ同じプレイヤーのものなら二歩
                    Piece existingPiece = board.getPiece(r, toCol);
                    if (existingPiece instanceof Hiyoko && !existingPiece.isPromoted() && existingPiece.getOwner() == piece.getOwner()) {
                        return false; // その列に既にひよこがいる
                    }
                }
            }
            return true;
        }

        /**
         * 指定された手が合法であり、かつその手によって自分のライオンが王手にならないかをチェックする。
         * @param playerType 現在のプレイヤーのタイプ
         * @param fromRow 移動元の行
         * @param fromCol 移動元の列
         * @param toRow 移動先の行
         * @param toCol 移動先の列
         * @return その手が合法で、かつ王手にならない場合は true
         */
        public boolean isValidMoveAndNotIntoCheck(PlayerType playerType, int fromRow, int fromCol, int toRow, int toCol) {
            // 仮に手を実行した新しい盤面を作成
            Board nextBoard = this.board.clone();
            // Player オブジェクトもクローンする
            Player nextPlayerA = this.playerA.clone();
            Player nextPlayerB = this.playerB.clone();

            Piece pieceToMove = nextBoard.getPiece(fromRow, fromCol);
            if (pieceToMove == null || pieceToMove.getOwner() != playerType) {
                return false; // 動かす駒がない、または自分の駒ではない
            }

            // 移動先に駒がある場合、それを捕獲するシミュレーション
            Piece capturedPiece = nextBoard.getPiece(toRow, toCol);
            if (capturedPiece != null) {
                if (capturedPiece.getOwner() == playerType) {
                    return false; // 自分の駒がある場所には移動できない
                }
                // 相手の駒であれば、その駒を盤面から取り除き、手駒に加える
                nextBoard.removePiece(toRow, toCol);
                // 駒の所有者を現在のプレイヤーに設定し、成り状態を解除して手駒に追加
                capturedPiece.setOwner(playerType);
                capturedPiece.unPromote();
                Player currentPlayerForSim = (playerType == PlayerType.PLAYER1) ? nextPlayerA : nextPlayerB;
                currentPlayerForSim.addCapturedPiece(capturedPiece);
            }

            // 駒を移動
            nextBoard.removePiece(fromRow, fromCol);
            nextBoard.placePiece(pieceToMove, toRow, toCol); // pieceToMove は既にクローンされたボードの駒を指している

            // 移動した結果、自分のライオンが王手にならないかチェック
            // isKingInCheckもSimulationStateのメソッドなので、nextBoardを使って呼び出す
            return !this.isKingInCheck(nextBoard, playerType);
        }

        /**
         * 指定された手駒打ちが合法であり、かつその手によって自分のライオンが王手にならないかをチェックする。
         * @param playerType 現在のプレイヤーのタイプ
         * @param pieceToDrop 打つ手駒
         * @param toRow 打つ先の行
         * @param toCol 打つ先の列
         * @return その手が合法で、かつ王手にならない場合は true
         */
        public boolean isValidDropAndNotIntoCheck(PlayerType playerType, Piece pieceToDrop, int toRow, int toCol) {
            // 仮に手を実行した新しい盤面を作成
            Board nextBoard = this.board.clone();
            // Player オブジェクトもクローンする
            Player nextPlayerA = this.playerA.clone();
            Player nextPlayerB = this.playerB.clone();

            // 打つ先に駒がないことを確認
            if (nextBoard.getPiece(toRow, toCol) != null) {
                return false;
            }

            // 手駒を仮に配置 (pieceToDropは元のリストから取得したオブジェクトだが、ここでは新しいボードに配置するだけ)
            // ただし、pieceToDrop自体はシミュレーションの都合上、クローンされているべきではない。
            // 呼び出し元の generateAllPossibleMoves で取得した Piece そのものを渡すため。
            // SimulationState の makeDrop の方で player.removeCapturedPiece(pieceToDrop) を呼ぶときに、
            // その removeCapturedPiece が内部的にリストからオブジェクトを削除する。
            // ここではあくまで「打ったらどうなるか」をシミュレーションするだけなので、pieceToDrop をそのまま使う。
            nextBoard.placePiece(pieceToDrop, toRow, toCol);

            // 手駒リストから一時的に削除するシミュレーション
            Player currentPlayerForSim = (playerType == PlayerType.PLAYER1) ? nextPlayerA : nextPlayerB;
            // removeCapturedPiece は Piece オブジェクトを直接受け取るため、
            // pieceToDrop を渡す。これで正確なオブジェクトがリストから削除される。
            // ただし、この isValidDropAndNotIntoCheck の中では、nextBoard を使った王手チェックのみを行うため、
            // 実際に capturedPieces から削除する処理は行わない。
            // 削除の処理は makeDrop で実際の手を適用する際に行われるべき。
            // ここでは、isKingInCheck の引数として渡すために capturedPieces が正確な状態になっている必要はないため、
            // capturedPieces の変更は不要。
            // 強いて言うなら、ここで `currentPlayerForSim.removeCapturedPiece(pieceToDrop);` をすると
            // nextPlayerA/B の capturedPieces が変更されてしまうので、もしこのチェック後に
            // nextPlayerA/B が再利用されるなら問題になる。しかし、ここでは nextBoard に対してのみ影響を与えるべき。

            // 打った結果、自分のライオンが王手にならないかチェック
            // isKingInCheckもSimulationStateのメソッドなので、nextBoardを使って呼び出す
            return !this.isKingInCheck(nextBoard, playerType);
        }

        /**
         * 指定されたプレイヤーのライオンが王手になっているかチェックする。
         * @param currentBoard 現在の盤面
         * @param playerType チェック対象のプレイヤータイプ
         * @return 王手であれば true、そうでなければ false
         */
        public boolean isKingInCheck(Board currentBoard, PlayerType playerType) {
            int[] kingPos = findLion(currentBoard, playerType);
            if (kingPos == null) {
                // ライオンが盤面にいない場合は王手ではない（既に取られてゲーム終了している可能性）
                // ただし、このメソッドは「その手を指すと王手になるか」をチェックするために呼ばれるので、
                // ライオンがいない場合は（その手によってライオンが取られた可能性はあるが）
                // 「王手ではない」と判断するのが適切。ゲーム終了判定は別途 isGameOver で行う。
                return false;
            }

            PlayerType opponentType = getOpponentPlayerType(playerType);

            // 敵の駒がライオンを攻撃できるかチェック
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = currentBoard.getPiece(r, c);
                    if (piece != null && piece.getOwner() == opponentType) {
                        List<int[]> possibleMoves = piece.getPossibleMoves(r, c, currentBoard);
                        for (int[] move : possibleMoves) {
                            if (move[0] == kingPos[0] && move[1] == kingPos[1]) {
                                return true; // 王手である
                            }
                        }
                    }
                }
            }
            return false; // 王手ではない
        }

        /**
         * 盤面を評価する
         * @param evaluatingPlayerType 評価対象のプレイヤータイプ (MinMaxインスタンス自身のプレイヤータイプ)
         * @return 盤面の評価値
         */
        private int evaluateBoard(PlayerType evaluatingPlayerType) {
            int score = 0;

            // 駒の価値と配置ボーナス
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = this.board.getPiece(r, c);
                    if (piece != null) {
                        int pieceValue = getPieceValue(piece);
                        int[][] pst = getPieceSquareTable(piece);
                        // プレイヤー1の駒はそのまま行を使用、プレイヤー2の駒は行を反転してPSTを適用
                        int rowForPST = (piece.getOwner() == PlayerType.PLAYER1) ? r : Board.ROWS - 1 - r;

                        int positionBonus = 0;
                        if (pst != null && rowForPST >= 0 && rowForPST < pst.length && c >= 0 && c < pst[0].length) {
                            positionBonus = pst[rowForPST][c];
                        }

                        if (piece.getOwner() == evaluatingPlayerType) {
                            score += pieceValue + positionBonus;
                            // 中央支配ボーナス
                            if ((r == 1 || r == 2) && c == 1) { // 中央のマス
                                score += CENTER_CONTROL_BONUS;
                            }
                        } else {
                            score -= (pieceValue + positionBonus);
                            // 相手駒の中央支配ペナルティ
                            if ((r == 1 || r == 2) && c == 1) {
                                score -= CENTER_CONTROL_BONUS;
                            }
                        }
                    }
                }
            }

            // 手駒の価値
            // SimulationState の playerA と playerB を使用
            for (Piece p : playerA.getCapturedPieces()) {
                if (PlayerType.PLAYER1 == evaluatingPlayerType) {
                    score += getPieceValue(p) * 3; // 自手駒はボーナス
                } else {
                    score -= getPieceValue(p) * 3; // 相手手駒はペナルティ
                }
            }
            for (Piece p : playerB.getCapturedPieces()) {
                if (PlayerType.PLAYER2 == evaluatingPlayerType) {
                    score += getPieceValue(p) * 3; // 自手駒はボーナス
                } else {
                    score -= getPieceValue(p) * 3; // 相手手駒はペナルティ
                }
            }

            // ライオンの安全性評価
            int[] myLionPos = findLion(this.board, evaluatingPlayerType);
            int[] opponentLionPos = findLion(this.board, getOpponentPlayerType(evaluatingPlayerType));

            if (myLionPos != null) {
                int myLionSafety = calculateLionSafety(this.board, evaluatingPlayerType, myLionPos[0], myLionPos[1]);
                score += myLionSafety;
            }

            if (opponentLionPos != null) {
                int opponentLionSafety = calculateLionSafety(this.board, getOpponentPlayerType(evaluatingPlayerType), opponentLionPos[0], opponentLionPos[1]);
                score -= opponentLionSafety; // 相手のライオンの安全性が高いほど、こちらには不利
            }

            // モビリティボーナス
            score += calculateMobility(this.board, evaluatingPlayerType);
            score -= calculateMobility(this.board, getOpponentPlayerType(evaluatingPlayerType));

            // 成り脅威ボーナス (ひよこが敵陣最奥にいる場合)
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = this.board.getPiece(r, c);
                    if (piece instanceof Hiyoko && !piece.isPromoted() && piece.getOwner() == evaluatingPlayerType) {
                        if ((evaluatingPlayerType == PlayerType.PLAYER1 && r == Board.ROWS - 1) ||
                            (evaluatingPlayerType == PlayerType.PLAYER2 && r == 0)) {
                            score += PROMOTION_THREAT_BONUS;
                        }
                    }
                }
            }
            
            // トライルール評価
            if (myLionPos != null) {
                // ライオンが特定のトライ位置（中央）にいる場合にボーナス
                if ( (evaluatingPlayerType == PlayerType.PLAYER1 && myLionPos[0] == Board.ROWS -1 && myLionPos[1] == 1) ||
                     (evaluatingPlayerType == PlayerType.PLAYER2 && myLionPos[0] == 0 && myLionPos[1] == 1) ) {
                    score += TRIAL_WIN_SCORE; // トライ成功は勝利点に等しい
                }
            }

            // 危険な駒の評価
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = this.board.getPiece(r, c);
                    if (piece != null) {
                        // 相手からの攻撃をチェック
                        List<int[]> attackingMoves = getAttackingMoves(this.board, r, c, getOpponentPlayerType(piece.getOwner()));
                        if (!attackingMoves.isEmpty()) {
                            // 自分の駒が攻撃されている
                            if (piece.getOwner() == evaluatingPlayerType) {
                                score -= ATTACKED_OWN_PIECE_PENALTY;
                            }
                            // 相手の駒を攻撃している
                            else {
                                score += ATTACKED_OPPONENT_PIECE_BONUS;
                            }
                        }
                        // 自分の駒が守られている場合のボーナス (ライオン以外)
                        if (piece.getOwner() == evaluatingPlayerType && !(piece instanceof Lion)) {
                            List<int[]> defendingMoves = getAttackingMoves(this.board, r, c, evaluatingPlayerType);
                            if (!defendingMoves.isEmpty()) {
                                score += DEFENDED_OWN_PIECE_BONUS;
                            }
                        }
                    }
                }
            }

            return score;
        }

        // 駒の価値を取得 (SimulationStateの内部メソッド)
        private int getPieceValue(Piece piece) {
            if (piece instanceof Lion) {
                return LION_VALUE;
            } else if (piece instanceof Kirin) {
                return KIRIN_VALUE;
            } else if (piece instanceof Zou) {
                return ZOU_VALUE;
            } else if (piece instanceof Hiyoko) {
                if (piece.isPromoted()) {
                    return NIWATORI_VALUE;
                }
                return HIYOKO_VALUE;
            }
            return 0;
        }

        // 駒ごとの配置点テーブルを取得 (SimulationStateの内部メソッド)
        private int[][] getPieceSquareTable(Piece piece) {
            if (piece instanceof Hiyoko) {
                if (piece.isPromoted()) {
                    return PST_NIWATORI;
                }
                return PST_HIYOKO;
            } else if (piece instanceof Kirin) {
                return PST_KIRIN;
            } else if (piece instanceof Zou) {
                return PST_ZOU;
            } else if (piece instanceof Lion) {
                return PST_LION;
            }
            return null;
        }

        // ライオンの安全性を評価 (SimulationStateの内部メソッド)
        private int calculateLionSafety(Board board, PlayerType playerType, int lionRow, int lionCol) {
            int safetyScore = 0;
            // 相手からの攻撃数に応じたペナルティ
            List<int[]> attackingMoves = getAttackingMoves(board, lionRow, lionCol, getOpponentPlayerType(playerType));
            safetyScore -= (attackingMoves.size() * LION_ATTACK_PENALTY_PER_ATTACKER);

            // 味方による防御数に応じたボーナス
            List<int[]> defendingMoves = getAttackingMoves(board, lionRow, lionCol, playerType);
            safetyScore += (defendingMoves.size() * LION_SAFETY_BONUS_PER_DEFENDER);
            return safetyScore;
        }

        // 特定のマスを攻撃できる駒のリストを返す (SimulationStateの内部メソッド)
        private List<int[]> getAttackingMoves(Board board, int targetRow, int targetCol, PlayerType attackerType) {
            List<int[]> attackers = new ArrayList<>();
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = board.getPiece(r, c);
                    if (piece != null && piece.getOwner() == attackerType) {
                        List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                        for (int[] move : possibleMoves) {
                            if (move[0] == targetRow && move[1] == targetCol) {
                                attackers.add(new int[]{r, c});
                            }
                        }
                    }
                }
            }
            return attackers;
        }

        // モビリティ (移動可能な手の数) を評価 (SimulationStateの内部メソッド)
        private int calculateMobility(Board board, PlayerType playerType) {
            int mobility = 0;
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece piece = board.getPiece(r, c);
                    if (piece != null && piece.getOwner() == playerType) {
                        mobility += piece.getPossibleMoves(r, c, board).size();
                    }
                }
            }
            // ここが修正のポイント: this.playerA / this.playerB は SimulationState のフィールド
            Player player = (playerType == PlayerType.PLAYER1) ? this.playerA : this.playerB;
            mobility += player.getCapturedPieces().size() * 2;
            return mobility * MOBILITY_BONUS_PER_MOVE;
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
                cloned.playerB = this.playerB.clone(); // Playerもディープコピーdd
                // currentPlayerType はプリミティブなのでそのままコピーされる
                return cloned;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(); // 発生しないはず
            }
        }
    }
}