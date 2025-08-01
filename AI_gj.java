import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AI_gj extends Player {

    private static final int MAX_DEPTH = 5; // 探索の深さ
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
    // 先手 (PLAYER1) 視点 (上が敵陣)
    // 0: [0,0] [0,1] [0,2] (敵陣)
    // 1: [1,0] [1,1] [1,2]
    // 2: [2,0] [2,1] [2,2]
    // 3: [3,0] [3,1] [3,2] (自陣)
    private static final int[][] POSITION_VALUE = {
        {10, 20, 10},
        {5,  15, 5},
        {5,  15, 5},
        {10, 20, 10}
    };

    // コンストラクタ
    public AI_gj(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        get.silentMode
        // 現在のプレイヤータイプを設定
        this.setPlayerType(game.getCurrentPlayer().getPlayerType());

        // すべての合法手を生成
        List<int[]> legalMoves = getAllPossibleMoves(game);

        if (legalMoves.isEmpty()) {
            return null; // 動かせる手がない場合
        }

        // アルファベータ法で最善手を探索
        int bestValue = Integer.MIN_VALUE;
        int[] bestMove = null;

        // 合法手をソートして探索効率を向上させる
        sortMoves(legalMoves, game.getBoard(), this.playerType);


        for (int[] move : legalMoves) {
            // 仮のゲーム状態を作成
            Game newGame = game.clone();
            
            // 実際に手を適用（ゲームの状態を変更）
            if (move[0] == 0) { // 駒の移動
                int fromRow = move[1];
                int fromCol = move[2];
                int toRow = move[3];
                int toCol = move[4];
                
                // 仮の移動を実行
                newGame.performMove(fromRow, fromCol, toRow, toCol);
                
                // 評価
                int value = minimax(newGame, MAX_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, false);

                if (value > bestValue) {
                    bestValue = value;
                    bestMove = move;
                }
            } else if (move[0] == 1) { // 駒の打ち込み
                int pieceIndex = move[1];
                int toRow = move[2];
                int toCol = move[3];

                // ★修正箇所: ここで新しいインスタンスを作成するのではなく、手駒リストから実際の駒を取得
                Piece pieceToDrop = null;
                Player currentPlayerInNewGame = (this.playerType == PlayerType.PLAYER1) ? newGame.getPlayerA() : newGame.getPlayerB();
                if (currentPlayerInNewGame.getCapturedPieces().size() > pieceIndex) {
                    pieceToDrop = currentPlayerInNewGame.getCapturedPieces().get(pieceIndex);
                }
                
                if (pieceToDrop != null) {
                    newGame.performDrop(pieceToDrop, toRow, toCol);

                    // 評価
                    int value = minimax(newGame, MAX_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, false);

                    if (value > bestValue) {
                        bestValue = value;
                        bestMove = move;
                    }
                }
            }
        }
        return bestMove;
    }


    private int minimax(Game game, int depth, int alpha, int beta, boolean maximizingPlayer) {
        PlayerType currentPlayerType = maximizingPlayer ? this.playerType : 
                                       (this.playerType == PlayerType.PLAYER1 ? PlayerType.PLAYER2 : PlayerType.PLAYER1);

        // 終了条件
        PlayerType winner = game.isGameOver();
        if (winner != null) {
            if (winner == this.playerType) {
                return WIN_SCORE - (MAX_DEPTH - depth); // 早く勝つほど高評価
            } else {
                return LOSE_SCORE + (MAX_DEPTH - depth); // 相手に早く負けるほど低評価
            }
        }
        if (depth == 0) {
            return evaluateBoard(game.getBoard(), game, this.playerType); // 評価関数
        }

        List<int[]> possibleMoves = getAllPossibleMoves(game);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                if (move[0] == 0) { // 駒の移動
                    int fromRow = move[1];
                    int fromCol = move[2];
                    int toRow = move[3];
                    int toCol = move[4];
                    
                    newGame.performMove(fromRow, fromCol, toRow, toCol);

                    int eval = minimax(newGame, depth - 1, alpha, beta, false);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) {
                        break; // βカット
                    }
                } else if (move[0] == 1) { // 駒の打ち込み
                    int pieceIndex = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    // ★修正箇所: 新しいインスタンスを作成せず、手駒リストから取得
                    Piece pieceToDrop = null;
                    Player currentPlayerInNewGame = (this.playerType == PlayerType.PLAYER1) ? newGame.getPlayerA() : newGame.getPlayerB();
                    if (currentPlayerInNewGame.getCapturedPieces().size() > pieceIndex) {
                        pieceToDrop = currentPlayerInNewGame.getCapturedPieces().get(pieceIndex);
                    }
                    if (pieceToDrop != null) {
                        newGame.performDrop(pieceToDrop, toRow, toCol);

                        int eval = minimax(newGame, depth - 1, alpha, beta, true);
                        maxEval = Math.max(maxEval, eval);
                        alpha = Math.max(alpha, eval);
                        if (beta <= alpha) {
                            break; // βカット
                        }
                    }
                }
            }
            return maxEval;
        } else { // minimizingPlayer
            int minEval = Integer.MAX_VALUE;
            for (int[] move : possibleMoves) {
                Game newGame = game.clone();
                if (move[0] == 0) { // 駒の移動
                    int fromRow = move[1];
                    int fromCol = move[2];
                    int toRow = move[3];
                    int toCol = move[4];

                    newGame.performMove(fromRow, fromCol, toRow, toCol);

                    int eval = minimax(newGame, depth - 1, alpha, beta, true);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) {
                        break; // αカット
                    }
                } else if (move[0] == 1) { // 駒の打ち込み
                    int pieceIndex = move[1];
                    int toRow = move[2];
                    int toCol = move[3];
                    // ★修正箇所: 新しいインスタンスを作成せず、手駒リストから取得
                    Piece pieceToDrop = null;
                    Player opponentPlayerInNewGame = (this.playerType == PlayerType.PLAYER1) ? newGame.getPlayerB() : newGame.getPlayerA();

                    if (opponentPlayerInNewGame.getCapturedPieces().size() > pieceIndex) {
                        pieceToDrop = opponentPlayerInNewGame.getCapturedPieces().get(pieceIndex);
                    }

                    if (pieceToDrop != null) {
                        newGame.performDrop(pieceToDrop, toRow, toCol);
                        int eval = minimax(newGame, depth - 1, alpha, beta, true);
                        minEval = Math.min(minEval, eval);
                        beta = Math.min(beta, eval);
                        if (beta <= alpha) {
                            break; // αカット
                        }
                    }
                }
            }
            return minEval;
        }
    }


    private int evaluateBoard(Board board, Game game, PlayerType evaluatingPlayerType) {
        int score = 0;
        PlayerType opponentPlayerType = (evaluatingPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        // 駒の価値の合計
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    if (piece.getOwner() == evaluatingPlayerType) {
                        score += getPieceValue(piece);
                        score += POSITION_VALUE[r][c]; // 位置ボーナス
                    } else {
                        score -= getPieceValue(piece);
                        score -= POSITION_VALUE[r][c]; // 相手の駒なので減点
                    }
                }
            }
        }

        // 持ち駒の価値の合計（打ち込みの機会）
        Player evaluatingPlayer = (evaluatingPlayerType == PlayerType.PLAYER1) ? game.getPlayerA() : game.getPlayerB();
        for (Piece p : evaluatingPlayer.getCapturedPieces()) {
            score += getPieceValue(p) / 2; // 持ち駒は盤上の駒より価値を低く見積もる
        }
        Player opponentPlayer = (evaluatingPlayerType == PlayerType.PLAYER1) ? game.getPlayerB() : game.getPlayerA();
        for (Piece p : opponentPlayer.getCapturedPieces()) {
            score -= getPieceValue(p) / 2;
        }

        // ライオンの安全度 (追加の評価項目)
        // 自分のライオンが危険にさらされていないか
        // 相手のライオンを攻撃できるか
        score += evaluateLionSafetyAndThreat(board, game, evaluatingPlayerType);


        // トライルールの考慮
        // 自分のライオンが敵陣の最終行に到達しているか
        // 相手のライオンが自陣の最終行に到達しているか
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion) {
                    if (piece.getOwner() == evaluatingPlayerType) {
                        // 自分のライオンがトライできる位置にいるか
                        if (evaluatingPlayerType == PlayerType.PLAYER1 && r == Board.ROWS - 1) { // Player1は下に行くほど進む
                            score += WIN_SCORE / 2; // トライの可能性を高く評価
                        } else if (evaluatingPlayerType == PlayerType.PLAYER2 && r == 0) { // Player2は上に行くほど進む
                            score += WIN_SCORE / 2; // トライの可能性を高く評価
                        }
                    } else { // 相手のライオン
                        // 相手のライオンがトライできる位置にいるか
                        if (opponentPlayerType == PlayerType.PLAYER1 && r == Board.ROWS - 1) {
                            score -= WIN_SCORE / 2; // 相手のトライの可能性を低く評価
                        } else if (opponentPlayerType == PlayerType.PLAYER2 && r == 0) {
                            score -= WIN_SCORE / 2; // 相手のトライの可能性を低く評価
                        }
                    }
                }
            }
        }


        // 詰みチェック（相手のライオンが詰んでいるか）
        if (game.isCheckmate(opponentPlayerType)) {
            score += WIN_SCORE; // 相手を詰ませたら勝利点
        }
        // 自分が詰まされていないか
        if (game.isCheckmate(evaluatingPlayerType)) {
            score -= WIN_SCORE; // 自分が詰まされたら敗北点
        }


        return score;
    }

    private int evaluateLionSafetyAndThreat(Board board, Game game, PlayerType evaluatingPlayerType) {
        int safetyScore = 0;
        PlayerType opponentPlayerType = (evaluatingPlayerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        int[] myLionPos = findLion(board, evaluatingPlayerType);
        int[] opponentLionPos = findLion(board, opponentPlayerType);

        if (myLionPos != null) {
            // 自分のライオンが王手されているか
            if (game.isKingInCheck(evaluatingPlayerType)) {
                safetyScore -= 500; // 王手されていると大きなペナルティ
            }

            // 自分のライオンを守る駒の数 (周囲8マス)
            int defenders = countDefenders(board, myLionPos[0], myLionPos[1], evaluatingPlayerType);
            safetyScore += defenders * 50; // 守る駒が多いほどボーナス
        }

        if (opponentLionPos != null) {
            // 相手のライオンが王手されているか
            if (game.isKingInCheck(opponentPlayerType)) {
                safetyScore += 500; // 相手が王手されているとボーナス
            }

            // 相手のライオンを攻撃できる駒の数
            int attackers = countAttackers(board, opponentLionPos[0], opponentLionPos[1], evaluatingPlayerType);
            safetyScore += attackers * 50; // 攻撃できる駒が多いほどボーナス
        }

        return safetyScore;
    }

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

    private int countDefenders(Board board, int row, int col, PlayerType playerType) {
        int count = 0;
        int[][] deltas = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, // 上下左右
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1} // 斜め
        };
        for (int[] d : deltas) {
            int r = row + d[0];
            int c = col + d[1];
            if (board.isValidCoordinate(r, c) && board.getPiece(r, c) != null && board.getPiece(r, c).getOwner() == playerType) {
                count++;
            }
        }
        return count;
    }

    private int countAttackers(Board board, int row, int col, PlayerType playerType) {
        int count = 0;
        PlayerType opponentType = (playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == row && move[1] == col) { // 攻撃対象が指定のマスなら
                            count++;
                            break; // 1つの駒が複数回攻撃できることはないのでbreak
                        }
                    }
                }
            }
        }
        return count;
    }


    // すべての合法手を生成するヘルパーメソッド
    // 戻り値の配列は {タイプ, 元の行, 元の列, 移動先の行, 移動先の列, (成りフラグ)}
    // タイプ: 0 = 移動, 1 = 打ち込み
    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> allPossibleMoves = new ArrayList<>();
        Board currentBoard = game.getBoard();
        PlayerType myPlayerType = game.getCurrentPlayer().getPlayerType();

        // 1. 駒の移動の合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = currentBoard.getPiece(r, c);
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> moves = piece.getPossibleMoves(r, c, currentBoard);
                    for (int[] move : moves) {
                        // 成りのチェック（ひよこが敵陣に入るとき）
                        boolean canPromote = false;
                        if (piece instanceof Hiyoko) {
                            if (myPlayerType == PlayerType.PLAYER1 && move[0] == Board.ROWS - 1) { // Player1が一番下の行に到達
                                canPromote = true;
                            } else if (myPlayerType == PlayerType.PLAYER2 && move[0] == 0) { // Player2が一番上の行に到達
                                canPromote = true;
                            }
                        }
                        
                        // 移動が合法で、かつ王手にならないかチェック
                        // GameクラスのisValidMoveAndNotIntoCheckメソッドを直接利用。
                        if (game.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                            allPossibleMoves.add(new int[]{0, r, c, move[0], move[1]}); // 成らない手
                            if (canPromote) {
                                allPossibleMoves.add(new int[]{0, r, c, move[0], move[1], 1}); // 成る手
                            }
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
                        // 手駒を打つ手は、特別な形式でリストに追加 { タイプ(1), 手駒リストのインデックス, 落とす行, 落とす列 }
                        allPossibleMoves.add(new int[]{1, i, r, c});
                    }
                }
            }
        }
        return allPossibleMoves;
    }

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
                Piece target2 = board.getPiece(move2[3], move2[4]); // 移動先の駒
                if (target2 != null && target2.getOwner() != playerType) {
                    score2 += getPieceValue(target2); // 駒を取れる場合、その駒の価値を加算
                }
            }
            // 打ち込みの手は、ここでは捕獲がないので0点（必要に応じて調整）

            return Integer.compare(score2, score1); // 降順にソート (より高いスコアの手を優先)
        });
    }

}