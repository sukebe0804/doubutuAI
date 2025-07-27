// AI_gj.java

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// PlayerType, Board, Game, Piece クラスが同じパッケージにあるか、適切にインポートされていることを前提とします
// 必要に応じて以下のインポートを有効にしてください。
// import PlayerType;
// import Board;
// import Game;
// import Piece;
// import Hiyoko;
// import Kirin;
// import Zou;
// import Lion;

public class AI_gj extends Player {

    private static final int MAX_DEPTH = 6; // 探索の深さ
    private static final int WIN_SCORE = 100000; // 勝利時の評価点
    private static final int LOSE_SCORE = -100000; // 敗北時の評価点

    // 駒の価値を設定（AlphaBeta.javaを参考に調整、またはさらに調整）
    private static final int HIYOKO_VALUE = 80;
    private static final int KIRIN_VALUE = 200;
    private static final int ZOU_VALUE = 200;
    private static final int LION_VALUE = 10000;
    private static final int NIWATORI_VALUE = 100; // にわとりの価値を上げる

    // 盤上の位置ごとの価値 (例: 中央を高く、端を低く)
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

    // 中央支配のボーナス（盤面の中央付近のマス）
    // 3x4盤面における中央マスは、R=1,2 C=0,1,2 あたりを指す
    private static final int CENTER_CONTROL_BONUS = 2; // 駒一つあたり
    // 中央のマス (例: r=1,2)
    private static final int[][] CENTRAL_SQUARES = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 0}, {2, 1}, {2, 2}
    };

    // トライ関連の評価定数 (AlphaBeta.javaを参考に)
    private static final int TRIAL_BONUS = 1500;  // トライ成功時のボーナスを非常に高く設定
    private static final int TRIAL_THREAT_BONUS = 500; // トライ位置近くにいる場合のボーナス

    // 危険性評価パラメータ（必要に応じて調整）
    private static final double DANGER_PENALTY_RATIO = 1.5;

    // ライオン接近ペナルティ（AlphaBeta.javaから導入）
    private static final int LION_PROXIMITY_PENALTY = 100; // 調整可能


    // コンストラクタ修正：Playerクラスは名前のみを受け取るため、superにはnameのみ渡す
    public AI_gj(String name, PlayerType type) {
        super(name); // PlayerクラスのコンストラクタはStringのみを受け取る
        this.setPlayerType(type); // PlayerTypeはsetterで設定する
    }

    // 引数なしのコンストラクタも同様に修正
    public AI_gj(String name) {
        super(name); // PlayerクラスのコンストラクタはStringのみを受け取る
        // PlayerTypeは後でGame.javaからsetPlayerTypeで設定される前提
    }


    @Override
    public int[] chooseMove(Game game) {
        // 現在のボードの状態と手駒を複製して、探索用にコピー
        Board currentBoard = game.getBoard().clone();
        List<Piece> myCapturedPieces = new ArrayList<>(this.getCapturedPieces());
        List<Piece> opponentCapturedPieces = new ArrayList<>();

        // 相手の手駒を取得
        if (this.getPlayerType() == PlayerType.PLAYER1) {
            opponentCapturedPieces.addAll(game.getPlayerB().getCapturedPieces());
        } else {
            opponentCapturedPieces.addAll(game.getPlayerA().getCapturedPieces());
        }

        // internalAllPossibleMovesは、AI_gjの内部で使う6要素の形式で保持する
        List<int[]> internalAllPossibleMoves = new ArrayList<>();

        // 盤上の駒の移動を生成
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = currentBoard.getPiece(r, c);
                if (piece != null && piece.getOwner() == this.getPlayerType()) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, currentBoard);
                    for (int[] move : movesForPiece) {
                        // 仮想的に手を適用し、王手にならないかチェック
                        Board tempBoard = currentBoard.clone();
                        Piece pieceToMove = tempBoard.getPiece(r, c); // 移動する駒を一時的に取得
                        Piece capturedInSim = tempBoard.getPiece(move[0], move[1]); // 移動先に駒があるか

                        tempBoard.removePiece(r, c);
                        tempBoard.placePiece(pieceToMove, move[0], move[1]);
                        
                        // 成り判定
                        if (pieceToMove instanceof Hiyoko) {
                            Hiyoko hiyoko = (Hiyoko) pieceToMove;
                            if (!hiyoko.isPromoted() &&
                                ((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                                 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                                hiyoko.promote();
                            }
                        }

                        // この手で自分のライオンが王手にならないかチェック
                        if (!isPlayerInCheckInternal(tempBoard, this.getPlayerType())) {
                            // 内部形式は6要素のまま: {moveType, fromR, fromC, toR, toC, capturedIdx}
                            internalAllPossibleMoves.add(new int[]{0, r, c, move[0], move[1], -1});
                        }
                        
                        // tempBoardを元の状態に戻す (次の手のチェックのために重要)
                        tempBoard.removePiece(move[0], move[1]); // 仮で置いた駒を削除
                        tempBoard.placePiece(pieceToMove, r, c); // 元の場所に戻す
                        if(capturedInSim != null) { // もし捕獲があったら捕獲された駒も戻す
                            tempBoard.placePiece(capturedInSim, move[0], move[1]);
                        }
                        // 成り状態を戻す（重要）
                        if (pieceToMove instanceof Hiyoko && ((Hiyoko)pieceToMove).isPromoted() &&
                            ((pieceToMove.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                             (pieceToMove.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                            ((Hiyoko)pieceToMove).demote();
                        }
                    }
                }
            }
        }

        // 手駒の打ち込みを生成
        List<Piece> testMyCapturedPieces = new ArrayList<>(myCapturedPieces);
        for (int i = 0; i < testMyCapturedPieces.size(); i++) {
            Piece pieceToDrop = testMyCapturedPieces.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (currentBoard.isEmpty(r, c)) { // 打ち込み先が空いているか
                        // 仮想的に打ち込みを行い、王手にならないかチェック
                        Board tempBoard = currentBoard.clone();
                        tempBoard.placePiece(pieceToDrop, r, c); // 仮置き

                        // この手で自分のライオンが王手にならないかチェック
                        if (!isPlayerInCheckInternal(tempBoard, this.getPlayerType())) {
                            // 内部形式は6要素のまま: {moveType, -1, -1, toR, toC, capturedIdx}
                            internalAllPossibleMoves.add(new int[]{1, -1, -1, r, c, i});
                        }
                        // tempBoardを元の状態に戻す (次の手のチェックのために重要)
                        tempBoard.removePiece(r, c); // 仮置きした駒を削除
                    }
                }
            }
        }

        // 合法手が全くない場合の処理
        if (internalAllPossibleMoves.isEmpty()) {
            System.err.println(this.getName() + " は合法手を見つけられませんでした。投了します。");
            // Gameクラス側でnullが返された場合に投了と判断するロジックが必要
            return null; 
        }
        
        // 探索効率のため、簡単なヒューリスティックでソート (例: 捕獲できる手を優先する)
        sortMoves(internalAllPossibleMoves, currentBoard, this.getPlayerType());
        
        int bestScore = LOSE_SCORE - 1; // 最小値で初期化
        int[] bestInternalMove = null; // 内部形式のベストな手

        // αβ法のための初期化
        int alpha = LOSE_SCORE - 1;
        int beta = WIN_SCORE + 1;

        for (int[] move : internalAllPossibleMoves) { // 内部形式のmoveを使用
            // 仮想的な盤面と手駒リストを生成し、手を適用
            Board nextBoard = currentBoard.clone();
            List<Piece> nextMyCaptured = new ArrayList<>(myCapturedPieces);
            List<Piece> nextOpponentCaptured = new ArrayList<>(opponentCapturedPieces);

            Piece capturedPiece = applyMove(nextBoard, nextMyCaptured, nextOpponentCaptured, this.getPlayerType(), move); 
            if (capturedPiece != null) {
                // 捕獲された駒の所有権を変更し、成り状態を解除して手駒リストに追加
                capturedPiece.setOwner(this.getPlayerType()); 
                // ここでHiyokoかどうかをチェックしてからdemoteを呼ぶ
                if (capturedPiece instanceof Hiyoko) {
                    ((Hiyoko) capturedPiece).demote(); // Hiyoko.javaにdemote()がある前提
                }
                nextMyCaptured.add(capturedPiece); 
            }

            // ミニマックス探索を実行
            PlayerType nextPlayerType = (this.getPlayerType() == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
            
            // minimax の引数: (盤面, 次のターンに最大化するプレイヤーの手駒, 次のターンに最小化するプレイヤーの手駒, 次のプレイヤータイプ, 深さ, α, β)
            int score = minimax(nextBoard, nextOpponentCaptured, nextMyCaptured, nextPlayerType, MAX_DEPTH - 1, alpha, beta);

            // 評価値を最大化する手を選択
            if (score > bestScore) {
                bestScore = score;
                bestInternalMove = move; // 内部形式のベストな手を保存
            }
            alpha = Math.max(alpha, score); // α値を更新
        }
        
        // 万が一 bestInternalMove が null のままの場合のフォールバック
        if (bestInternalMove == null && !internalAllPossibleMoves.isEmpty()) {
            System.err.println(this.getName() + " が最適な手を見つけられませんでした。最初の合法手を返します。");
            bestInternalMove = internalAllPossibleMoves.get(0); // リストが空でないことはすでに確認済み
        }

        // ここで、Game.javaが期待する4要素の形式に変換して返す
        if (bestInternalMove != null) {
            int moveType = bestInternalMove[0];
            if (moveType == 0) { // 駒の移動
                return new int[]{bestInternalMove[1], bestInternalMove[2], bestInternalMove[3], bestInternalMove[4]}; // {fromR, fromC, toR, toC}
            } else { // 手駒の打ち込み (moveType == 1)
                return new int[]{-1, bestInternalMove[5], bestInternalMove[3], bestInternalMove[4]}; // {-1, capturedIdx, toR, toC}
            }
        }
        return null; // 合法手が見つからない場合
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
        // 終端ノードの判定（深さ制限、勝敗が決まった場合）
        // isTerminal()は内部でevaluate()を呼び出しているので、ここで評価値を返す
        if (depth == 0 || isTerminal(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType)) {
            return evaluate(board, currentPlayerCapturedPieces, opponentCapturedPieces, currentPlayerType);
        }

        List<int[]> allPossibleMoves = new ArrayList<>();
        // 盤上の駒の移動を生成
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == currentPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, board);
                    for (int[] move : movesForPiece) {
                        // 仮想的に手を適用し、王手にならないかチェック
                        Board tempBoard = board.clone();
                        Piece pieceToMove = tempBoard.getPiece(r, c); // 移動する駒を一時的に取得
                        Piece capturedInSim = tempBoard.getPiece(move[0], move[1]); // 移動先に駒があるか

                        tempBoard.removePiece(r, c);
                        tempBoard.placePiece(pieceToMove, move[0], move[1]);

                        // 成り判定
                        if (pieceToMove instanceof Hiyoko) {
                            Hiyoko hiyoko = (Hiyoko) pieceToMove;
                            if (!hiyoko.isPromoted() &&
                                ((hiyoko.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                                 (hiyoko.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                                hiyoko.promote();
                            }
                        }

                        // この手で自分のライオンが王手にならないかチェック
                        if (!isPlayerInCheckInternal(tempBoard, currentPlayerType)) {
                            allPossibleMoves.add(new int[]{0, r, c, move[0], move[1], -1}); // 内部形式のまま
                        }
                        
                        // tempBoardを元の状態に戻す (次の手のチェックのために重要)
                        tempBoard.removePiece(move[0], move[1]); // 仮で置いた駒を削除
                        tempBoard.placePiece(pieceToMove, r, c); // 元の場所に戻す
                        if(capturedInSim != null) { // もし捕獲があったら捕獲された駒も戻す
                            tempBoard.placePiece(capturedInSim, move[0], move[1]);
                        }
                        // 成り状態を戻す（重要）
                        if (pieceToMove instanceof Hiyoko && ((Hiyoko)pieceToMove).isPromoted() &&
                            ((pieceToMove.getOwner() == PlayerType.PLAYER1 && move[0] == 0) ||
                             (pieceToMove.getOwner() == PlayerType.PLAYER2 && move[0] == Board.ROWS - 1))) {
                            ((Hiyoko)pieceToMove).demote();
                        }
                    }
                }
            }
        }
        // 手駒の打ち込みを生成
        for (int i = 0; i < currentPlayerCapturedPieces.size(); i++) {
            Piece pieceToDrop = currentPlayerCapturedPieces.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (board.isEmpty(r, c)) { // 打ち込み先が空いているか
                        // 仮想的に打ち込みを行い、王手にならないかチェック
                        Board tempBoard = board.clone();
                        tempBoard.placePiece(pieceToDrop, r, c); // 仮置き

                        // この手で自分のライオンが王手にならないかチェック
                        if (!isPlayerInCheckInternal(tempBoard, currentPlayerType)) {
                            allPossibleMoves.add(new int[]{1, -1, -1, r, c, i}); // 内部形式のまま
                        }
                        // tempBoardを元の状態に戻す (次の手のチェックのために重要)
                        tempBoard.removePiece(r, c); // 仮置きした駒を削除
                    }
                }
            }
        }

        // 合法手が全くない場合、現在のプレイヤーは「負け」とみなす
        // この時点で王手されていないのに動かせない、つまり手詰まり
        if (allPossibleMoves.isEmpty()) {
            // 現在の評価対象がミニマックスで最大化しているプレイヤーであれば、それが手詰まりなのでLOSE_SCORE
            // 最小化している相手プレイヤーであれば、相手が手詰まりなのでWIN_SCORE
            return (currentPlayerType == this.getPlayerType()) ? LOSE_SCORE : WIN_SCORE;
        }

        // 探索効率のため、簡単なヒューリスティックでソート
        sortMoves(allPossibleMoves, board, currentPlayerType);

        if (currentPlayerType == this.getPlayerType()) { // 最大化プレイヤー (AI自身)
            int maxEval = LOSE_SCORE - 1;
            for (int[] move : allPossibleMoves) {
                Board nextBoard = board.clone();
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType); // 所有権を自分に変える
                    // ここでHiyokoかどうかをチェックしてからdemoteを呼ぶ
                    if (captured instanceof Hiyoko) {
                        ((Hiyoko) captured).demote(); // 成り状態を解除 (Hiyoko.javaにdemote()がある前提)
                    }
                    nextMaxPlayerCaptured.add(captured);
                }
                
                int eval = minimax(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // βカット
                }
            }
            return maxEval;
        } else { // 最小化プレイヤー (相手)
            int minEval = WIN_SCORE + 1;
            for (int[] move : allPossibleMoves) {
                Board nextBoard = board.clone();
                List<Piece> nextMinPlayerCaptured = new ArrayList<>(currentPlayerCapturedPieces);
                List<Piece> nextMaxPlayerCaptured = new ArrayList<>(opponentCapturedPieces);

                Piece captured = applyMove(nextBoard, nextMinPlayerCaptured, nextMaxPlayerCaptured, currentPlayerType, move);
                if (captured != null) {
                    captured.setOwner(currentPlayerType); // 相手に所有権を変える
                    // ここでHiyokoかどうかをチェックしてからdemoteを呼ぶ
                    if (captured instanceof Hiyoko) {
                        ((Hiyoko) captured).demote(); // 成り状態を解除 (Hiyoko.javaにdemote()がある前提)
                    }
                    nextMinPlayerCaptured.add(captured);
                }
                
                int eval = minimax(nextBoard, nextMaxPlayerCaptured, nextMinPlayerCaptured, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1, depth - 1, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // αカット
                }
            }
            return minEval;
        }
    }

    /**
     * 手を適用し、捕獲された駒を返す
     * @param board 盤面
     * @param myCapturedPieces 適用するプレイヤーの手駒リスト（打ち込み時に使用）
     * @param opponentCapturedPieces 相手の手駒リスト（影響なし、引数合わせのため）
     * @param currentPlayerType 現在の手番のプレイヤータイプ
     * @param move 適用する手 {moveType, fromR, fromC, toR, toC, capturedIdx}
     * @return 捕獲された駒 (もしあれば)
     */
    private Piece applyMove(Board board, List<Piece> myCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType, int[] move) {
        int moveType = move[0];
        int fromR = move[1];
        int fromC = move[2];
        int toR = move[3];
        int toC = move[4];
        int capturedIdx = move[5]; // 打ち込み時の手駒のインデックス

        Piece capturedPiece = null; // 捕獲された駒を保持

        if (moveType == 0) { // 移動
            Piece pieceToMove = board.getPiece(fromR, fromC); // 元の位置の駒を取得
            capturedPiece = board.getPiece(toR, toC);       // 移動先に駒があれば取得
            
            board.removePiece(fromR, fromC);                 // 元の位置から駒を削除
            board.placePiece(pieceToMove, toR, toC);         // 新しい位置に駒を置く
            
            // 昇格処理（ひよこが敵陣に入ったら自動的ににわとりに昇格）
            if (pieceToMove instanceof Hiyoko) {
                Hiyoko hiyoko = (Hiyoko) pieceToMove;
                if (!hiyoko.isPromoted() &&
                    ((hiyoko.getOwner() == PlayerType.PLAYER1 && toR == 0) ||
                     (hiyoko.getOwner() == PlayerType.PLAYER2 && toR == Board.ROWS - 1))) {
                    hiyoko.promote();
                }
            }
            return capturedPiece; // 捕獲された駒を返す
        } else if (moveType == 1) { // 打ち込み
            // applyMoveが呼び出される時点では、myCapturedPiecesはAI_gjの内部コピーなので、
            // そのインデックスを使って直接操作します。
            Piece pieceToDrop = myCapturedPieces.remove(capturedIdx); // 手駒から削除
            board.placePiece(pieceToDrop, toR, toC);
            return null; // 打ち込みでは駒は捕獲されない
        }
        return null;
    }

    /**
     * 指定されたプレイヤーのライオンが王手されているか判定する内部ヘルパーメソッド
     * (Board.javaにisPlayerInCheckがない場合の代替)
     * @param board 判定対象の盤面
     * @param playerType 判定するプレイヤータイプ
     * @return 王手されていればtrue、そうでなければfalse
     */
    private boolean isPlayerInCheckInternal(Board board, PlayerType playerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) {
            // ライオンが盤上にいない（すでに捕獲されている）場合は、王手にはなり得ない
            return false; 
        }

        PlayerType opponentType = (playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // 盤上のすべての相手の駒をチェックし、その利きに自分のライオンがいるかを確認
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponentType) {
                    List<int[]> possibleAttacks = piece.getPossibleMoves(r, c, board);
                    for (int[] attackMove : possibleAttacks) {
                        if (attackMove[0] == lionPos[0] && attackMove[1] == lionPos[1]) {
                            return true; // ライオンが相手の駒の利きに入っている
                        }
                    }
                }
            }
        }
        return false; // 王手ではない
    }


    /**
     * 終端ノードかどうかの判定
     * @param board 盤面
     * @param currentPlayerCapturedPieces 現在の手番のプレイヤーの手駒
     * @param opponentCapturedPieces 相手の手番のプレイヤーの手駒
     * @param currentPlayerType 現在の手番のプレイヤータイプ
     * @return 終端ノードであればtrue
     */
    private boolean isTerminal(Board board, List<Piece> currentPlayerCapturedPieces, List<Piece> opponentCapturedPieces, PlayerType currentPlayerType) {
        // ライオンが捕獲された場合
        if (findLion(board, currentPlayerType) == null) { // 自分のライオンがいなければ負け
            return true;
        }
        if (findLion(board, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1) == null) { // 相手のライオンがいなければ勝ち
            return true;
        }
        
        // トライ勝利 (簡略化のため、ここでは「敵陣最奥到達」のみをトライ勝利条件とする。)
        if (isTrialWin(board, currentPlayerType)) {
            return true;
        }
        // 相手がトライ勝利している場合も終端ノード
        if (isTrialWin(board, (currentPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1)) {
            return true;
        }
        
        return false;
    }

    // ライオンの位置を探すヘルパーメソッド
    private int[] findLion(Board board, PlayerType playerType) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece instanceof Lion && piece.getOwner() == playerType) {
                    return new int[]{r, c};
                }
            }
        }
        return null; // ライオンが見つからない
    }

    /**
     * トライ勝利の判定 (簡略版)
     * @param board 盤面
     * @param playerType プレイヤータイプ
     * @return トライ勝利であればtrue
     */
    private boolean isTrialWin(Board board, PlayerType playerType) {
        int[] lionPos = findLion(board, playerType);
        if (lionPos == null) return false; // ライオンがいない場合はトライ勝利ではない

        if (playerType == PlayerType.PLAYER1) {
            // 先手プレイヤーのライオンが敵陣最奥に到達 (行0)
            return lionPos[0] == 0;
        } else {
            // 後手プレイヤーのライオンが敵陣最奥に到達 (最終行)
            return lionPos[0] == Board.ROWS - 1;
        }
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
        // 1. 勝敗の判定を最優先
        int[] myLionPos = findLion(board, evaluatePlayerType);
        int[] opponentLionPos = findLion(board, (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1);

        if (myLionPos == null) { // 自分のライオンがいなければ負け
            return LOSE_SCORE;
        }
        if (opponentLionPos == null) { // 相手のライオンがいなければ勝ち
            return WIN_SCORE;
        }
        
        // トライ勝利の判定
        if (isTrialWin(board, evaluatePlayerType)) {
            return WIN_SCORE; // 非常に高い評価
        }
        // 相手がトライ勝利している場合
        if (isTrialWin(board, (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1)) {
            return LOSE_SCORE; // 負け
        }

        int score = 0;
        PlayerType opponentTypeForEval = (evaluatePlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // 2. 盤上の駒の価値 + 位置の価値 + 危険性評価 + ライオン接近ペナルティ
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionValue = (evaluatePlayerType == PlayerType.PLAYER1) ? BOARD_POSITION_VALUES_PLAYER1[r][c] : BOARD_POSITION_VALUES_PLAYER2[r][c];

                    // 危険性評価 (AlphaBeta.javaのロジックを参考に)
                    int dangerPenalty = 0;
                    if (isSquareAttackedBy(board, r, c, opponentTypeForEval)) { // 相手の駒の利きにあるか
                        dangerPenalty = (int) (pieceValue * DANGER_PENALTY_RATIO); // 駒の価値に応じてペナルティ
                        // ライオンは特別なので、危険でもペナルティはつけない（王手で判断済みのため）
                        if (piece instanceof Lion) {
                            dangerPenalty = 0;
                        }
                    }

                    // ライオン接近ペナルティ (AlphaBeta.javaから導入)
                    int lionProximityPenalty = 0;
                    // 自分の駒が相手のライオンから近い場合、相手にとって取りやすいのでペナルティ
                    if (piece.getOwner() == evaluatePlayerType && opponentLionPos != null) {
                        int distance = Math.abs(r - opponentLionPos[0]) + Math.abs(c - opponentLionPos[1]); // マンハッタン距離
                        if (distance <= 2) { // 2マス以内にいる場合
                            lionProximityPenalty = LION_PROXIMITY_PENALTY * (3 - distance); // 近いほどペナルティ大
                        }
                    }
                    
                    if (piece.getOwner() == evaluatePlayerType) {
                        score += pieceValue + positionValue - dangerPenalty - lionProximityPenalty;
                    } else {
                        score -= (pieceValue + positionValue) - dangerPenalty + lionProximityPenalty; // 相手の駒はマイナス（相手にとっての駒の危険性はプラス）
                    }
                }
            }
        }

        // 3. 手駒の価値
        for (Piece piece : currentPlayerCapturedPieces) {
            score += getPieceValue(piece) / 2; // 手駒は盤上の駒の半分程度の価値とする
        }
        for (Piece piece : opponentCapturedPieces) {
            score -= getPieceValue(piece) / 2; // 相手の手駒はマイナス
        }

        // 4. 駒の活動度 (Mobility)
        int myMobility = 0;
        int opponentMobility = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    // ここでの getPossibleMoves は、王手回避のチェックを含まない生の手を返す前提
                    List<int[]> moves = piece.getPossibleMoves(r, c, board);
                    if (piece.getOwner() == evaluatePlayerType) {
                        // 自分の駒が合法的に動ける数をカウントする (isPlayerInCheckInternalでフィルタリング済みなので不要な場合もある)
                        // より正確には、その手を指しても王手にならない合法手の数をカウントすべき
                        // しかし、パフォーマンスのため簡略化する場合もある
                        myMobility += moves.size();
                    } else {
                        opponentMobility += moves.size();
                    }
                }
            }
        }
        score += myMobility; // 活動度そのまま加算
        score -= opponentMobility; // 相手の活動度が高いとマイナス

        // 5. 王手のリスク評価 (簡易版)
        if (isPlayerInCheckInternal(board, evaluatePlayerType)) {
            score -= 1000; // 自分が王手されている場合は大きなペナルティ（価値の高いライオンが危険に晒されている）
        }
        // 相手が王手されている場合 (相手が王手を解除しなければいけないので、有利)
        if (isPlayerInCheckInternal(board, opponentTypeForEval)) {
            score += 500; // 相手が王手されている場合はボーナス
        }

        // 6. ライオンの安全性評価の強化 (すでにあったロジックをより重視)
        if (myLionPos != null) {
            int safeSquares = 0;
            int dangerousSquares = 0;
            // ライオンの周り8方向（将棋のライオンの動きを考慮）
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
                        safeSquares++; // 味方の駒がいる
                    } else {
                        if (isSquareAttackedBy(board, r, c, opponentTypeForEval)) {
                            dangerousSquares++; // 危険なマス
                        }
                    }
                }
            }
            score += safeSquares * 10; // 安全なマスが多いほどボーナスを大きくする
            score -= dangerousSquares * 20; // 危険なマスが多いほどペナルティを大きくする
        }

        // 7. 中央支配の評価
        for (int[] centralCoord : CENTRAL_SQUARES) {
            int r = centralCoord[0];
            int c = centralCoord[1];
            Piece piece = board.getPiece(r, c);
            if (piece != null) {
                if (piece.getOwner() == evaluatePlayerType) {
                    score += CENTER_CONTROL_BONUS * 2; // 中央支配のボーナスを少し上げる
                } else {
                    score -= CENTER_CONTROL_BONUS * 2;
                }
            }
        }

        // 8. トライ接近の評価（AI_gjにも導入）
        if (myLionPos != null) {
            // 自分のライオンがトライ位置に近づいている場合
            if (evaluatePlayerType == PlayerType.PLAYER1) { // 先手
                int distToGoal = myLionPos[0]; // 行0が目標
                score += TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal); // 0に近いほどボーナス
            } else { // 後手
                int distToGoal = Board.ROWS - 1 - myLionPos[0]; // 最終行が目標
                score += TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal); // 最終行に近いほどボーナス
            }
        }
        // 相手のライオンがトライ位置に近づいている場合
        if (opponentLionPos != null) {
            if (opponentTypeForEval == PlayerType.PLAYER1) { // 相手が先手
                int distToGoal = opponentLionPos[0];
                score -= TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            } else { // 相手が後手
                int distToGoal = Board.ROWS - 1 - opponentLionPos[0];
                score -= TRIAL_THREAT_BONUS * (Board.ROWS - 1 - distToGoal);
            }
        }
        
        return score;
    }

    /**
     * 指定されたマスが特定のプレイヤーの駒の利きに置かれているかを判定するヘルパーメソッド
     * @param board 盤面
     * @param targetR 判定対象の行
     * @param targetC 判定対象の列
     * @param attackingPlayerType 攻撃しているとみなすプレイヤーのタイプ
     * @return 攻撃されているマスであればtrue
     */
    private boolean isSquareAttackedBy(Board board, int targetR, int targetC, PlayerType attackingPlayerType) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == attackingPlayerType) {
                    // その駒の移動可能マスにtargetR, targetCが含まれるか
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == targetR && move[1] == targetC) {
                            return true; // 攻撃されている
                        }
                    }
                }
            }
        }
        return false; // 攻撃されていない
    }


    /**
     * 駒の価値を取得するヘルパーメソッド
     * @param piece 駒
     * @return 駒の価値
     */
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

    /**
     * 探索効率向上のため、合法手をソートするヘルパーメソッド
     * (捕獲できる手を優先するなど)
     * @param moves ソート対象の合法手リスト (内部形式)
     * @param board 現在の盤面
     * @param playerType 現在のプレイヤータイプ
     */
    private void sortMoves(List<int[]> moves, Board board, PlayerType playerType) {
        // λ式でComparatorを定義
        Collections.sort(moves, (move1, move2) -> {
            // move1とmove2の評価点（仮）を比較して並べ替え
            // ここでは、単純に駒を取れる手を優先する
            int score1 = 0;
            int score2 = 0;

            // move1の評価
            if (move1[0] == 0) { // 駒の移動
                Piece target1 = board.getPiece(move1[3], move1[4]); // 移動先の駒
                if (target1 != null && target1.getOwner() != playerType) {
                    score1 += getPieceValue(target1); // 駒を取れる場合、その駒の価値を加算
                }
            }
            // 打ち込みの手は、ここでは捕獲がないので0点（必要に応じて調整）

            // move2の評価
            if (move2[0] == 0) { // 駒の移動
                Piece target2 = board.getPiece(move2[3], move2[4]);
                if (target2 != null && target2.getOwner() != playerType) {
                    score2 += getPieceValue(target2);
                }
            }
            
            // 降順にソート (スコアが高い方が前に来るように)
            return Integer.compare(score2, score1);
        });
    }
}
