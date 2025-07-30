import java.util.*;

public class QLearn extends Player {

    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.9;
    private static double EPSILON = 1.0;
    private static final double MIN_EPSILON = 0.05;

    private Map<String, Map<Integer, Double>> qTable = new HashMap<>();
    private Random rand = new Random();

    public QLearn(String name) {
        super(name);
    }

    private void decayEpsilon(int ep, int maxEpisodes) {
        EPSILON = Math.max(MIN_EPSILON, EPSILON * 0.995);
    }

    @Override
    public int[] chooseMove(Game game) {
        String state = encodeState(game);
        List<int[]> legalMoves = getLegalMoves(game);
        if (legalMoves == null || legalMoves.isEmpty()) return null;

        if (rand.nextDouble() < EPSILON) {
            return legalMoves.get(rand.nextInt(legalMoves.size()));
        } else {
            Map<Integer, Double> moveQMap = qTable.getOrDefault(state, new HashMap<>());
            int[] bestAction = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (int[] move : legalMoves) {
                //List<Integer> moveKey = Arrays.stream(move).boxed().toList();
		int moveKey = encodeMove(move);
                double q = moveQMap.getOrDefault(moveKey, 0.0);
                if (q > bestValue) {
                    bestValue = q;
                    bestAction = move;
                }
            }
            return bestAction != null ? bestAction : legalMoves.get(0);
        }
    }

    private void updateQ(String state, int[] move, double reward, String nextState, List<int[]> nextMoves) {
        int moveKey = encodeMove(move);
        Map<Integer, Double> qMap = qTable.computeIfAbsent(state, k -> new HashMap<>());
        double oldQ = qMap.getOrDefault(moveKey, 0.0);

        Map<Integer, Double> nextQMap = qTable.getOrDefault(nextState, new HashMap<>());
        double maxNextQ = 0.0;
        for (int[] nextMove : nextMoves) {
            int nextKey = encodeMove(nextMove);
            double q = nextQMap.getOrDefault(nextKey, 0.0);
            maxNextQ = Math.max(maxNextQ, q);
        }

        double newQ = oldQ + ALPHA * (reward + GAMMA * maxNextQ - oldQ);
        qMap.put(moveKey, newQ);
        qTable.put(state, qMap);
    }

    public void trial(int episodes) {
        QLearn playerA = this;
        RandomPlayer playerB = new RandomPlayer("Random");

        for (int ep = 0; ep < episodes; ep++) {
            if (ep % 10 == 0) {
                System.out.println("trial完了率 : " + (ep * 100 / episodes) + "%");
            }

            Game game = new Game(playerA, playerB);
            game.setSilentMode(true);
            PlayerType winner = null;
            int turnCount = 0;
            Map<String, Integer> stateCounts = new HashMap<>();

            while (true) {
                Player current = game.getCurrentPlayer();
                boolean isQLearn = current instanceof QLearn;
                String playerTag = current.getPlayerType().toString();
                String prevState = isQLearn ? ((QLearn) current).encodeState(game) + "_" + playerTag : "";

                turnCount++;
                stateCounts.put(prevState, stateCounts.getOrDefault(prevState, 0) + 1);

                int[] move = current.chooseMove(game);
                if (move == null) {
                    if (isQLearn) {
                        double finalReward = -200.0;
                        ((QLearn) current).updateQ(prevState, new int[]{0, 0, 0, 0}, finalReward, prevState, new ArrayList<>());
                    }
                    break;
                }

		Piece captured = null;
		if (move[0] != -1) {
		    captured = game.getBoard().getPiece(move[2], move[3]);
		}
                //Piece captured = game.getBoard().getPiece(move[2], move[3]);
                if (move[0] == -1) {
                    Piece pieceToDrop = current.getCapturedPieces().get(move[1]);
                    game.performDrop(pieceToDrop, move[2], move[3]);
                } else {
                    game.performMove(move[0], move[1], move[2], move[3]);
                }

                if (isQLearn) {
                    String nextState = ((QLearn) current).encodeState(game);
                    List<int[]> nextMoves = ((QLearn) current).getLegalMoves(game);
                    double reward = ((QLearn) current).getReward(game, captured);
                    if (turnCount > 78) reward -= 50.0;
                    if (turnCount >= 256) reward -= 100.0;
                    if (stateCounts.get(prevState) >= 4) {
                        reward -= 1000.0;
                        ((QLearn) current).updateQ(prevState, move, reward, nextState, nextMoves);
                        break;
                    }
                    ((QLearn) current).updateQ(prevState, move, reward, nextState, nextMoves);
                }

                if (turnCount >= 128) break;
                winner = game.isGameOver();
                if (winner != null) break;

                game.switchPlayer();
            }

            playerA.decayEpsilon(ep, episodes);
        }
    }

    public List<int[]> getLegalMoves(Game game) {
        List<int[]> allLegalMoves = new ArrayList<>();
        PlayerType myPlayerType = this.getPlayerType();

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

        return allLegalMoves;
    }

    private int encodeMove(int[] move) {
	return (move[0] + 1) << 12 | (move[1] + 1) << 8 | (move[2] + 1) << 4 | (move[3] + 1);
    }

    private int[] decodeMove(int moveCode) {
	return new int[] {
	    (moveCode >> 12) - 1,
	    ((moveCode >> 8) & 0xF) - 1,
	    ((moveCode >> 4) & 0xF) - 1,
	    (moveCode & 0xF) - 1
	};
    }

    public String encodeState(Game game) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = game.getBoard().getPiece(r, c);
                sb.append(p == null ? "." : p.getSymbol().charAt(0));
            }
        }
        return sb.toString();
    }

    private double getReward(Game game, Piece capturedPiece) {
        PlayerType winner = game.isGameOver();
        PlayerType myType = this.getPlayerType();
        double reward = 0.0;

        if (winner != null) return (winner == myType) ? 100.0 : -100.0;
        if (capturedPiece != null) reward += getPieceValue(capturedPiece);

        reward += 1.0 * evaluateMaterial(game, myType);
        reward += 0.5 * evaluateKingSafety(game, myType);
        reward += 2.5 * evaluateHandPieceValue(game, myType);
        reward += 1.0 * evaluatePiecePosition(game, myType);

        return reward;
    }

    private double evaluateHandPieceValue(Game game, PlayerType myType) {
        double score = 0.0;
        List<Piece> hand = (myType == this.getPlayerType()) ? this.getCapturedPieces() : game.getOpponent(this).getCapturedPieces();
        for (Piece p : hand) score += getPieceValue(p);
        return score;
    }

    private double evaluatePiecePosition(Game game, PlayerType myType) {
        double score = 0.0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = game.getBoard().getPiece(r, c);
                if (p != null && p.getOwner() == myType) {
                    double val = getPieceValue(p);
                    int distance = (myType == PlayerType.PLAYER1) ? r : (Board.ROWS - 1 - r);
                    score += val * (1 + distance * 0.1);
                }
            }
        }
        return score;
    }

    private double getPieceValue(Piece piece) {
        if (piece == null) return 0;
        switch (piece.getSymbol()) {
	case "獅", "ラ" -> {
	    return 5.0;
	}
	case "象", "ゾ", "麒", "キ" -> {
	    return 3.0;
	}
	case "鶏", "に" -> {
	    return 2.0;
	}
	case "ひ", "ヒ" -> {
	    return 1.0;
	}
	default -> {
	    return 0.0;
	}
        }
    }

    private double evaluateMaterial(Game game, PlayerType myType) {
        double score = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = game.getBoard().getPiece(r, c);
                if (p != null) {
                    double value = getPieceValue(p);
                    score += (p.getOwner() == myType) ? value : -value;
                }
            }
        }
        for (Piece p : getCapturedPieces()) score += getPieceValue(p);
        return score;
    }

    private double evaluateKingSafety(Game game, PlayerType myType) {
        int safety = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = game.getBoard().getPiece(r, c);
                if (p != null && ("獅".equals(p.getSymbol()) || "ラ".equals(p.getSymbol())) && p.getOwner() == myType) {
                    int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                    for (int[] d : dirs) {
                        int nr = r + d[0], nc = c + d[1];
                        if (0 <= nr && nr < Board.ROWS && 0 <= nc && nc < Board.COLS) {
                            Piece neighbor = game.getBoard().getPiece(nr, nc);
                            if (neighbor != null && neighbor.getOwner() == myType) safety++;
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
