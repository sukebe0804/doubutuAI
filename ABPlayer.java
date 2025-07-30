import java.util.List;

public class ABPlayer extends Player {

    private static final int MAX_DEPTH = 5; // 探索の深さ調整可能

    public ABPlayer(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        // Gameから現在の状態をGameStateに変換（状態コピー用）
        GameState rootState = new GameState(game);

        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;

        List<int[]> legalMoves = rootState.getAllLegalMoves(this.playerType);
        for (int[] move : legalMoves) {
            GameState nextState = rootState.clone();
            nextState.makeMove(move);

            int score = minimax(nextState, MAX_DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (depth == 0 || state.isGameOver()) {
            return evaluate(state);
        }

        PlayerType currentPlayer = maximizingPlayer ? this.playerType : this.playerType.opponent();

        List<int[]> legalMoves = state.getAllLegalMoves(currentPlayer);
        if (legalMoves.isEmpty()) {
            // 手がないなら負け
            return maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : legalMoves) {
                GameState nextState = state.clone();
                nextState.makeMove(move);
                int eval = minimax(nextState, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break; // β枝刈り
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : legalMoves) {
                GameState nextState = state.clone();
                nextState.makeMove(move);
                int eval = minimax(nextState, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break; // α枝刈り
            }
            return minEval;
        }
    }

    // 評価関数の例（シンプルに駒の価値合計）
    private int evaluate(GameState state) {
        int score = 0;
        for (Piece p : state.getPieces()) {
            int value = getPieceValue(p);
            if (p.getOwner() == this.playerType) {
                score += value;
            } else {
                score -= value;
            }
        }
        return score;
    }

    private int getPieceValue(Piece p) {
        if (p instanceof Lion) return 10000;
        if (p instanceof Kirin) return 50;
        if (p instanceof Zou) return 400;
        if (p instanceof Hiyoko) {
            return p.isPromoted() ? 350 : 100;
        }
        return 0;
    }
}
