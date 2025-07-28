import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.*;

public class QLearn extends Player{

    private static final double ALPHA = 0.1;   // 学習率
    private static final double GAMMA = 0.9;   // 割引率
    private static final double EPSILON = 0.1; // ε-greedy探索率

    private Map<String, double[]> qTable = new HashMap<>();
    private Random rand = new Random();

    public QLearn(String name) {
	super(name);
	this.rand = new Random();
    }

    // ε-greedyで行動選択
    @Override
    public int[] chooseMove(Game game) {
	String state = encodeState(game);
	List<int[]> legalMoves = getLegalMoves(game);

	if (legalMoves == null || legalMoves.isEmpty()) {
        return null; // 打てる手がない場合の処理
	}
	
        if (rand.nextDouble() < EPSILON) {
            return legalMoves.get(rand.nextInt(legalMoves.size())); // ランダム行動
        } else {
            double[] qValues = qTable.getOrDefault(state, new double[legalMoves.size()]);
            int bestAction = 0;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < legalMoves.size(); i++) {
                if (qValues[i] > bestValue) {
                    bestValue = qValues[i];
                    bestAction = i;
                }
            }
            return legalMoves.get(bestAction);
        }
    }

    // Q値の更新
    private void updateQ(String state, int action, double reward, String nextState, int nextActionSize) {
        double[] qValues = qTable.getOrDefault(state, new double[nextActionSize]);
        double oldQ = qValues[action];

        double[] nextQValues = qTable.getOrDefault(nextState, new double[nextActionSize]);
        double maxNextQ = Arrays.stream(nextQValues).max().orElse(0.0);

        double newQ = oldQ + ALPHA * (reward + GAMMA * maxNextQ - oldQ);
        qValues[action] = newQ;

        qTable.put(state, qValues);
    }

    // 学習ループ
    public void trial(int episodes) {
        for (int ep = 0; ep < episodes; ep++) {
            Game game = new Game();
	    PlayerType winner = null;
            while (true) {
                String state = encodeState(game);
                List<int[]> legalMoves = getLegalMoves(game);
                int[] move = chooseMove(game);
		if (move == null) {
		    break; // 合法手がない＝終了
		}
		
                game.makeMove(move[0], move[1], move[2], move[3]);
		
                double reward = getReward(game);
                String nextState = encodeState(game);
		
		int moveIndex = legalMoves.indexOf(move);
                updateQ(state, moveIndex, reward, nextState, legalMoves.size());
	        winner = game.isGameOver();
                if (!(winner == null)) break;
            }
        }
    }

    public List<int[]> getLegalMoves(Game game) {
	List<int[]> allLegalMoves = new ArrayList<>();
	PlayerType myPlayerType = this.getPlayerType();
	// 1. 駒の移動に関する合法手を収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = game.getBoard().getPiece(r, c);
                if (piece != null && piece.getOwner() == myPlayerType) {
                    List<int[]> movesForPiece = piece.getPossibleMoves(r, c, game.getBoard());
                    for (int[] move : movesForPiece) {
                        if (game.isValidMoveAndNotIntoCheck(myPlayerType, r, c, move[0], move[1])) {
                            allLegalMoves.add(new int[]{r, c, move[0], move[1]});
                        }
                    }
                }
            }
        }
        // 2. 手駒を打つ合法手を収集
        List<Piece> captured = this.getCapturedPieces();
        for (int i = 0; i < captured.size(); i++) {
            Piece pieceToDrop = captured.get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (game.getBoard().isEmpty(r, c) && game.isValidDropAndNotIntoCheck(myPlayerType, pieceToDrop, r, c)) {
                        allLegalMoves.add(new int[]{-1, i, r, c});
                    }
                }
            }
        }
        if (allLegalMoves.isEmpty()) {
            return new ArrayList<>(); // 動かせる手も打てる手駒もない
        }
	return allLegalMoves;
    }

    public String encodeState(Game game) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                sb.append(game.getBoard().getPiece(r, c));
            }
        }
	sb.append(game.getCurrentPlayer() == game.getPlayerA() ? "1" : "0");
        return sb.toString();
    }

    public double getReward(Game game) {
	PlayerType winner = game.isGameOver();
	if (winner == null) return 0.0;
	if (winner == PlayerType.PLAYER1) {
	    return 1.0;
	} else {
	    return -1.0;
	}
    }

    @Override
    public QLearn clone() {
        return (QLearn) super.clone();
    }
}
