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
	    Board board = game.getBoard();
	    PlayerType winner = null;
	    boolean firstPlayer = false;
	    Player PlayerA = game.getPlayerA();
	    Player PlayerB = game.getPlayerB();
            while (true) {
		if (firstPlayer) {
		    firstPlayer = false;
		} else {
		    firstPlayer = true;
		}
                String state = encodeState(game);
                List<int[]> legalMoves = getLegalMoves(game);
                int[] move = chooseMove(game);
		if (move == null) {
		    break; // 合法手がない＝終了
		}
		Piece capturedPiece = board.getPiece(move[2], move[3]);
		
                game.makeMove(move[0], move[1], move[2], move[3]);
		
                double reward = getReward(game, PlayerA, PlayerB, firstPlayer, capturedPiece);
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

    // 報酬関数
    public double getReward(Game game, Player A, Player B, boolean isPlayer, Piece movedPiece) {
	double reward = 0.0;
	PlayerType winner = game.isGameOver();

        // 勝敗報酬
	if (winner != null) {
	    if ((isPlayer && winner == PlayerType.PLAYER1) || (!isPlayer && winner == PlayerType.PLAYER2)) {
		return 1.0;  // 勝ち
	    } else {
		return -1.0; // 負け
	    }
	}

	// 駒を取ったときの報酬
	if (movedPiece != null) {
	    reward += getPieceValue(movedPiece);
	}

	// 駒の価値差を評価
	int materialBalance = evaluateMaterial(game, isPlayer ? PlayerType.PLAYER1 : PlayerType.PLAYER2);
	reward += 0.05 * materialBalance;

	// 玉の安全性（例：周囲に味方駒が多いほど良い）
	reward += 0.03 * evaluateKingSafety(game, isPlayer ? PlayerType.PLAYER1 : PlayerType.PLAYER2);

	return reward;
    }

    // 駒の価値を定義（仮に：ライオン=5, ぞう=3, きりん=3, ひよこ=1）
    private int getPieceValue(Piece piece) {
	if (piece == null) return 0;
	switch (piece.getSymbol()) {
        case "獅":
	case "ラ":
	    return 5;
	case "象":
	case "ゾ":
	    return 3;
	case "麒":
	case "キ":
	    return 3;
	case "鶏":
	case "に":
	    return 2;
	case "ひ":
	case "ヒ":
	    return 1;
	default: return 0;
	}
    }

    // 自駒と敵駒の価値の差を返す
    private int evaluateMaterial(Game game, PlayerType myType) {
	int score = 0;
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece p = game.getBoard().getPiece(r, c);
		if (p != null) {
		    int value = getPieceValue(p);
		    score += (p.getOwner() == myType) ? value : -value;
		}
	    }
	}
	// 持ち駒も含めて評価
	for (Piece p : getCapturedPieces()) {
	    score += getPieceValue(p);
	}
	return score;
    }

    // 玉の安全性を評価（例：味方駒に囲まれてるほど良い）
    private int evaluateKingSafety(Game game, PlayerType myType) {
	int safety = 0;
	for (int r = 0; r < Board.ROWS; r++) {
	    for (int c = 0; c < Board.COLS; c++) {
		Piece p = game.getBoard().getPiece(r, c);
		if (p != null && (p.getSymbol() == "獅" || p.getSymbol() == "ラ") && p.getOwner() == myType) {
		    int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
		    for (int[] d : directions) {
			int nr = r + d[0], nc = c + d[1];
			if (0 <= nr && nr < Board.ROWS && 0 <= nc && nc < Board.COLS) {
			    Piece neighbor = game.getBoard().getPiece(nr, nc);
			    if (neighbor != null && neighbor.getOwner() == myType) {
				safety++;
			    }
			}
		    }
		}
	    }
	}
	return safety;
    }


    @Override
    public QLearn clone() {
        return (QLearn) super.clone();
    }
}
