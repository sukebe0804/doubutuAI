import java.util.ArrayList;
import java.util.List;

public class AlphaBeta extends Player {
    // 駒の基本価値（危険性評価用）
    private static final int LION_VALUE = 10000;
    private static final int ELEPHANT_VALUE = 200;
    private static final int GIRAFFE_VALUE = 200;
    private static final int CHICK_VALUE = 80;
    private static final int HEN_VALUE = 100;
    private static final int TRIAL_BONUS = 500;  // トライ成功時のボーナス
    private static final int TRIAL_THREAT_BONUS = 200; // トライ位置近くにいる場合のボーナス

    // 危険性評価パラメータ（従来通り維持）
    private static final double DANGER_PENALTY_RATIO = 1.5;
    
    // ライオン接近ペナルティ（追加）
    private static final int LION_PROXIMITY_PENALTY = 50;

    // コンストラクタを追加
    public AlphaBeta(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        List<int[]> moves = getAllPossibleMoves(game);
        if (moves.isEmpty()) return null;

        int[] bestMove = null;
        int bestValue = Integer.MIN_VALUE;

        for (int[] move : moves) {
            Game newGame = game.clone();
            applyMove(newGame, move);
            
            // 従来通りの危険性評価を適用
            int value = evaluate(newGame);
            
            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
        }
        return bestMove;
    }

    /**
     * 駒の危険性を評価（従来のロジックを維持）
     */
    private int evaluate(Game game) {
        Board board = game.getBoard();
        int score = 0;
        
        // 盤面の駒評価
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    int value = getPieceValue(piece);
                    if (piece.getOwner() != this.getPlayerType()) value = -value;
                    score += value;
                }
            }
        }
        
        // 危険性評価（変更なし）
        score += evaluateDanger(board);

	score += evaluateTrialCondition(game);
        
        return score;
    }
    private int evaluateTrialCondition(Game game) {
        int trialScore = 0;
        Board board = game.getBoard();
        
        // 自ライオンの位置を探す
        int myLionRow = -1, myLionCol = -1;
        int enemyLionRow = -1, enemyLionCol = -1;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion) {
                    if (piece.getOwner() == this.getPlayerType()) {
                        myLionRow = r;
                        myLionCol = c;
                    } else {
                        enemyLionRow = r;
                        enemyLionCol = c;
                    }
                }
            }
        }
        
        // 自ライオンが敵陣の端にいるかチェック（トライ位置）
        if (myLionRow != -1) {
            // PLAYER1の場合はrow=3、PLAYER2の場合はrow=0が敵陣の端
            if ((this.getPlayerType() == PlayerType.PLAYER1 && myLionRow == 3) ||
                (this.getPlayerType() == PlayerType.PLAYER2 && myLionRow == 0)) {
                
                // トライ位置にいる場合のボーナス
                trialScore += TRIAL_BONUS;
                
                // さらに、王手でない場合（即勝利）は非常に高いスコアを返す
                if (!game.isKingInCheck(this.getPlayerType())) {
                    trialScore += 10000; // 即勝利の場合は非常に高いスコア
                }
            }
            
            // トライ位置に近づいている場合のボーナス
            int targetRow = (this.getPlayerType() == PlayerType.PLAYER1) ? 3 : 0;
            int distanceToTrial = Math.abs(myLionRow - targetRow);
            trialScore += (3 - distanceToTrial) * TRIAL_THREAT_BONUS;
        }
        
        // 敵ライオンが自陣の端に近づいている場合のペナルティ
        if (enemyLionRow != -1) {
            int ourHomeRow = (this.getPlayerType() == PlayerType.PLAYER1) ? 0 : 3;
            int enemyDistance = Math.abs(enemyLionRow - ourHomeRow);
            trialScore -= (3 - enemyDistance) * TRIAL_THREAT_BONUS;
        }
        
        return trialScore;
    }

    /**
     * 駒が攻撃されているかチェック（従来通り）
     */
    private int evaluateDanger(Board board) {
        int danger = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == this.getPlayerType()) {
                    if (isUnderAttack(board, r, c)) {
                        danger += getDangerPenalty(piece);
                    }
                }
            }
        }
        return danger;
    }

    // 以下は従来のメソッドをそのまま保持
    private int getDangerPenalty(Piece piece) {
        return (int)(-getPieceValue(piece) * DANGER_PENALTY_RATIO);
    }

    private boolean isUnderAttack(Board board, int r, int c) {
        PlayerType opponent = getOpponentPlayerType();
        for (int tr = 0; tr < Board.ROWS; tr++) {
            for (int tc = 0; tc < Board.COLS; tc++) {
                Piece attacker = board.getPiece(tr, tc);
                if (attacker != null && attacker.getOwner() == opponent) {
                    for (int[] move : attacker.getPossibleMoves(tr, tc, board)) {
                        if (move[0] == r && move[1] == c) return true;
                    }
                }
            }
        }
        return false;
    }

    private int getPieceValue(Piece piece) {
        switch (piece.getSymbol()) {
            case "ラ": case "獅": return LION_VALUE;
            case "ゾ": case "象": return ELEPHANT_VALUE;
            case "キ": case "麒": return GIRAFFE_VALUE;
            case "ヒ": case "ひ": return piece.isPromoted() ? HEN_VALUE : CHICK_VALUE;
            case "ニ": case "鶏": return HEN_VALUE;
            default: return 0;
        }
    }

    
    private boolean canPromote(Piece piece) {
        String sym = piece.getSymbol();
        return sym.equals("ヒ") || sym.equals("キ") || sym.equals("ゾ");
    }

    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> moves = new ArrayList<>();
        Board board = game.getBoard();
        PlayerType current = this.getPlayerType();
        
        // 盤上の駒の移動
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == current) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        Piece target = board.getPiece(move[0], move[1]);
                        if (target == null || target.getOwner() != current) {
                            moves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }

        // 手駒を打つ手
        if (game.getCurrentPlayer().equals(this)) {
            List<Piece> captured = this.getCapturedPieces();
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
        }
        
        return moves;
    }

    private void applyMove(Game game, int[] move) {
        if (move[0] == -1) {
            game.performDrop(this.getCapturedPieces().get(move[1]), move[2], move[3]);
        } else {
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }

    private PlayerType getOpponentPlayerType() {
        return (this.getPlayerType() == PlayerType.PLAYER1) ? 
               PlayerType.PLAYER2 : PlayerType.PLAYER1;
    }

    private int getLionProximityPenalty(Board board, int r, int c) {
        int penalty = 0;
        for (int lr = 0; lr < Board.ROWS; lr++) {
            for (int lc = 0; lc < Board.COLS; lc++) {
                Piece lion = board.getPiece(lr, lc);
                if (isEnemyLion(lion)) {
                    int distance = Math.abs(r - lr) + Math.abs(c - lc);
                    if (distance <= 2) {
                        penalty += LION_PROXIMITY_PENALTY * (3 - distance);
                    }
                }
            }
        }
        return penalty;
    }

    private boolean isEnemyLion(Piece piece) {
        return piece != null && 
               (piece.getSymbol().equals("獅") || piece.getSymbol().equals("ラ")) &&
               piece.getOwner() != this.getPlayerType();
    }
}
