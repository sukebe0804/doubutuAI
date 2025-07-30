import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class AlphaBeta extends Player {
    // 駒の基本価値
    private static final int LION_VALUE = 100000;
    private static final int ELEPHANT_VALUE = 800;
    private static final int GIRAFFE_VALUE = 800;
    private static final int CHICK_VALUE = 200;
    private static final int HEN_VALUE = 400;
    
    // 評価パラメータ（優先順位明確化バージョン）
    private static final int TRY_BONUS = 15000;
    private static final int CAPTURE_BONUS = 3000;
    private static final int SAFE_CAPTURE_MULTIPLIER = 2;
    private static final int MOBILITY_RESTRICTION_BONUS = 120;
    private static final int COMPLETE_BLOCK_BONUS = 1500;
    private static final int SAFETY_BONUS = 50;
    private static final int MOBILITY_WEIGHT = 15;
    private static final int DEFENSIVE_POSITION_BONUS = 150;
    private static final int KING_PROTECTION_BONUS = 300;
    private static final int PIECE_COORDINATION_BONUS = 80;
    private static final int PIN_BONUS = 200;
    private static final int PINNED_PENALTY = 150; // 追加
    private static final int LION_PINNED_PENALTY = 300; // 追加
    private static final int OPPONENT_MOBILITY_PENALTY = 8; // 追加
    private static final int OPPONENT_TRY_THREAT = -1000;
    private static final int ELEPHANT_ACTIVITY_BONUS = 100;
    private static final int GIRAFFE_ACTIVITY_BONUS = 100;
    private static final int CENTRAL_CONTROL_BONUS = 50;
    private static final int LION_CORNER_PENALTY = 400;
    private static final int LION_EDGE_PENALTY = 200;
    private static final int LION_CENTER_BONUS = 100;
    private static final int OVEREXTENSION_PENALTY = 30; // 追加
    private static final int LION_OVEREXTENSION_PENALTY = 150; // 追加
    
    private static final int MAX_DEPTH = 4;
    
    private final LinkedList<int[]> tabuList = new LinkedList<>();
    private static final int TABU_LIST_SIZE = 3;

    public AlphaBeta(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
	game.setSilentMode(true);
        int[] tryMove = findTryMove(game);
        if (tryMove != null) {
            addToTabuList(tryMove);
            return tryMove;
        }

        List<int[]> allMoves = getAllPossibleMoves(game);
        List<int[]> legalMoves = new ArrayList<>();
        
        for (int[] move : allMoves) {
            Game simulatedGame = game.clone();
            applyMove(simulatedGame, move);
            if (!simulatedGame.isKingInCheck(getPlayerType())) {
                legalMoves.add(move);
            }
        }

        if (legalMoves.isEmpty()) {
            return null;
        }

        List<int[]> filteredMoves = legalMoves.stream()
            .filter(move -> !isTabuMove(move))
            .collect(Collectors.toList());
            
        if (filteredMoves.isEmpty()) {
            tabuList.clear();
            filteredMoves = new ArrayList<>(legalMoves);
        }
        
        int[] bestMove = selectBestMove(filteredMoves, game);
        if (bestMove != null) {
            addToTabuList(bestMove);
        }
	game.setSilentMode(false);
        return bestMove;
    }

    private int[] findTryMove(Game game) {
        Board board = game.getBoard();
        PlayerType player = getPlayerType();
        int tryRow = getTryRow(player);
        
        int[] lionPos = findLionPosition(board, player);
        if (lionPos == null) return null;
        
        if (lionPos[0] == tryRow) {
            return null;
        }
        
        Lion lion = (Lion)board.getPiece(lionPos[0], lionPos[1]);
        List<int[]> possibleMoves = lion.getPossibleMoves(lionPos[0], lionPos[1], board);
        
        for (int[] move : possibleMoves) {
            if (move[0] == tryRow) {
                Game simulatedGame = game.clone();
                simulatedGame.performMove(lionPos[0], lionPos[1], move[0], move[1]);
                if (!simulatedGame.isKingInCheck(player)) {
                    return new int[]{lionPos[0], lionPos[1], move[0], move[1]};
                }
            }
        }
        
        return null;
    }

    private int getTryRow(PlayerType player) {
        return (player == PlayerType.PLAYER1) ? 3 : 0;
    }

    private int[] selectBestMove(List<int[]> moves, Game game) {
        if (moves.isEmpty()) {
            return null;
        }

        int pieceCount = countPieces(game.getBoard());
        int dynamicDepth = (pieceCount < 6) ? MAX_DEPTH + 1 : MAX_DEPTH;
        
        int[] bestMove = moves.get(0);
        int bestValue = Integer.MIN_VALUE;
        
        game.setSilentMode(true);
        for (int[] move : moves) {
            Game newGame = game.clone();
            applyMove(newGame, move);
            
            int moveValue = evaluateMove(newGame, move);
            moveValue += alphaBeta(newGame, dynamicDepth-1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            
            if (moveValue > bestValue) {
                bestValue = moveValue;
                bestMove = move;
            }
        }
        game.setSilentMode(false);
        
        return bestMove;
    }

    private int evaluateMove(Game game, int[] move) {
        Board board = game.getBoard();
        PlayerType player = getPlayerType();
        PlayerType opponent = getOpponentPlayer(player);
        int score = 0;

        if (isTryMove(board, move, player)) {
            score += TRY_BONUS;
        }

        if (isCaptureMove(game, move)) {
            Piece target = board.getPiece(move[2], move[3]);
            score += getPieceValue(target) * SAFE_CAPTURE_MULTIPLIER;
            
            if (!isUnderAttack(board, move[2], move[3], player)) {
                score += CAPTURE_BONUS;
            }
        }

        score += evaluateOpponentRestriction(game, move, player);

        if (move[0] != -1 && board.getPiece(move[0], move[1]) instanceof Lion) {
            score += evaluateLionPosition(move[2], move[3], player);
        }

        score += evaluatePieceControl(board, player) * 0.6;
        score += evaluatePieceCooperation(board, player) * 0.5;
        score += evaluateDangerPatterns(board, player) * 0.6;
        score += evaluateMobility(board, player) * 0.7;
        score += evaluateDefensivePositions(board, player) * 0.5;
        score += evaluateKingProtection(board, player) * 0.6;
        score += evaluateOpponentTryThreat(board, player, opponent) * 0.7;
        score += evaluateCentralControl(board, player) * 0.6;

        return score;
    }

    private int evaluateOpponentRestriction(Game game, int[] move, PlayerType player) {
        Game simulatedGame = game.clone();
        applyMove(simulatedGame, move);
        Board newBoard = simulatedGame.getBoard();
        PlayerType opponent = getOpponentPlayer(player);
        
        int totalMobility = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = newBoard.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponent) {
                    totalMobility += piece.getPossibleMoves(r, c, newBoard).size();
                }
            }
        }
        
        int restrictionScore = (24 - totalMobility) * MOBILITY_RESTRICTION_BONUS;
        if (totalMobility == 0) {
            restrictionScore += COMPLETE_BLOCK_BONUS;
        }
        
        return restrictionScore;
    }

    private int evaluateLionPosition(int row, int col, PlayerType player) {
        int score = 0;
        
        if (player == PlayerType.PLAYER1) {
            if ((row == 0 && col == 0) || (row == 0 && col == 2)) {
                score -= LION_CORNER_PENALTY;
            } else if (row == 0) {
                score -= LION_EDGE_PENALTY;
            }
        } else {
            if ((row == 3 && col == 0) || (row == 3 && col == 2)) {
                score -= LION_CORNER_PENALTY;
            } else if (row == 3) {
                score -= LION_EDGE_PENALTY;
            }
        }
        
        if ((row == 1 || row == 2) && col == 1) {
            score += LION_CENTER_BONUS;
        }
        
        return score;
    }

    private int evaluatePieceControl(Board board, PlayerType player) {
        int controlScore = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    controlScore += piece.getPossibleMoves(r, c, board).size() * 10;
                }
            }
        }
        return controlScore;
    }

    private int evaluatePieceCooperation(Board board, PlayerType player) {
        int cooperationScore = 0;
        int[] lionPos = findLionPosition(board, player);
        
        if (lionPos != null) {
            for (int r = lionPos[0]-1; r <= lionPos[0]+1; r++) {
                for (int c = lionPos[1]-1; c <= lionPos[1]+1; c++) {
                    if (r >= 0 && r < Board.ROWS && c >= 0 && c < Board.COLS) {
                        if (r == lionPos[0] && c == lionPos[1]) continue;
                        
                        Piece piece = board.getPiece(r, c);
                        if (piece != null && piece.getOwner() == player) {
                            cooperationScore += 80;
                            if (piece instanceof Zou || piece instanceof Kirin) {
                                cooperationScore += 100;
                            }
                        }
                    }
                }
            }
        }
        return cooperationScore;
    }

    private int evaluateDangerPatterns(Board board, PlayerType player) {
        int penalty = 0;
        PlayerType opponent = getOpponentPlayer(player);
        
        int[] opponentLionPos = findLionPosition(board, opponent);
        if (opponentLionPos != null) {
            int distance = (player == PlayerType.PLAYER1) ? 
                opponentLionPos[0] : 3 - opponentLionPos[0];
            penalty -= (3 - distance) * 80;
        }
        
        penalty += evaluatePinnedPieces(board, player);
        
        return penalty;
    }

    private int evaluatePinnedPieces(Board board, PlayerType player) {
        int penalty = 0;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    if (isPinned(board, r, c, player)) {
                        penalty -= PINNED_PENALTY;
                        if (piece instanceof Lion) {
                            penalty -= LION_PINNED_PENALTY;
                        }
                    }
                }
            }
        }
        
        return penalty;
    }

    private boolean isPinned(Board board, int row, int col, PlayerType player) {
        int[] lionPos = findLionPosition(board, player);
        if (lionPos == null) return false;
        
        return (row == lionPos[0] || col == lionPos[1]);
    }

    private int evaluateMobility(Board board, PlayerType player) {
        int mobilityScore = 0;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    int moves = piece.getPossibleMoves(r, c, board).size();
                    mobilityScore += moves * MOBILITY_WEIGHT;
                    
                    if (piece instanceof Lion || piece instanceof Hiyoko) {
                        mobilityScore += moves * MOBILITY_WEIGHT / 2;
                    }
                }
            }
        }
        return mobilityScore;
    }

    private int evaluateDefensivePositions(Board board, PlayerType player) {
        int defenseScore = 0;
        int homeRow = (player == PlayerType.PLAYER1) ? 0 : 3;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    if (r == homeRow || (r == homeRow + (player == PlayerType.PLAYER1 ? 1 : -1))) {
                        defenseScore += DEFENSIVE_POSITION_BONUS;
                    }
                    
                    if ((r == 1 || r == 2) && (c == 1)) {
                        defenseScore += DEFENSIVE_POSITION_BONUS / 2;
                    }
                }
            }
        }
        return defenseScore;
    }

    private int evaluateKingProtection(Board board, PlayerType player) {
        int[] lionPos = findLionPosition(board, player);
        if (lionPos == null) return 0;
        
        int protectionScore = 0;
        int defenderCount = 0;
        
        for (int r = Math.max(0, lionPos[0]-1); r <= Math.min(Board.ROWS-1, lionPos[0]+1); r++) {
            for (int c = Math.max(0, lionPos[1]-1); c <= Math.min(Board.COLS-1, lionPos[1]+1); c++) {
                if (r == lionPos[0] && c == lionPos[1]) continue;
                
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    defenderCount++;
                    if (piece instanceof Zou || piece instanceof Kirin) {
                        protectionScore += KING_PROTECTION_BONUS / 2;
                    }
                }
            }
        }
        
        protectionScore += defenderCount * KING_PROTECTION_BONUS;
        return protectionScore;
    }

    private int evaluateOpponentTryThreat(Board board, PlayerType player, PlayerType opponent) {
        int[] opponentLionPos = findLionPosition(board, opponent);
        if (opponentLionPos == null) return 0;
        
        int threatScore = 0;
        int homeRow = getTryRow(player);
        
        int distance = Math.abs(opponentLionPos[0] - homeRow);
        threatScore += (3 - distance) * OPPONENT_TRY_THREAT / 3;
        
        if (opponentLionPos[0] == homeRow) {
            threatScore += OPPONENT_TRY_THREAT * 2;
        }
        
        return threatScore;
    }

    private int evaluateCentralControl(Board board, PlayerType player) {
        int control = 0;
        
        for (int[] pos : new int[][]{{1,1},{2,1}}) {
            Piece piece = board.getPiece(pos[0], pos[1]);
            if (piece != null && piece.getOwner() == player) {
                control += CENTRAL_CONTROL_BONUS;
            }
        }
        
        return control;
    }

    private int[] findLionPosition(Board board, PlayerType player) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion && piece.getOwner() == player) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private boolean isUnderAttack(Board board, int row, int col, PlayerType owner) {
        PlayerType opponent = getOpponentPlayer(owner);
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponent) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        if (move[0] == row && move[1] == col) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isTryMove(Board board, int[] move, PlayerType player) {
        if (move[0] == -1) return false;
        
        Piece piece = board.getPiece(move[0], move[1]);
        if (!(piece instanceof Lion)) return false;
        
        int tryRow = getTryRow(player);
        return move[2] == tryRow;
    }

    private boolean isCaptureMove(Game game, int[] move) {
        Board board = game.getBoard();
        Piece target = board.getPiece(move[2], move[3]);
        return target != null && target.getOwner() != getPlayerType();
    }

    private int getPieceValue(Piece piece) {
        if (piece instanceof Lion) return LION_VALUE;
        if (piece instanceof Zou) return ELEPHANT_VALUE;
        if (piece instanceof Kirin) return GIRAFFE_VALUE;
        if (piece instanceof Hiyoko && !piece.isPromoted()) return CHICK_VALUE;
        if (piece instanceof Hiyoko && piece.isPromoted()) return HEN_VALUE;
        if (piece.getSymbol().equals("ニ") || piece.getSymbol().equals("鶏")) return HEN_VALUE;
        return 0;
    }

    private List<int[]> getAllPossibleMoves(Game game) {
        List<int[]> moves = new ArrayList<>();
        Board board = game.getBoard();
        PlayerType current = getPlayerType();
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == current) {
                    for (int[] move : piece.getPossibleMoves(r, c, board)) {
                        moves.add(new int[]{r, c, move[0], move[1]});
                    }
                }
            }
        }
        
        List<Piece> captured = getCapturedPieces();
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
        
        return moves;
    }

    private void applyMove(Game game, int[] move) {
        if (move == null) return;
        
        if (move[0] == -1) {
            game.performDrop(getCapturedPieces().get(move[1]), move[2], move[3]);
        } else {
            game.performMove(move[0], move[1], move[2], move[3]);
        }
    }

    private PlayerType getOpponentPlayer(PlayerType player) {
        return player == PlayerType.PLAYER1 ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
    }

    private void addToTabuList(int[] move) {
        if (move == null) return;
        
        tabuList.addLast(move.clone());
        if (tabuList.size() > TABU_LIST_SIZE) {
            tabuList.removeFirst();
        }
    }

    private boolean isTabuMove(int[] move) {
        if (move == null) return false;
        return tabuList.stream().anyMatch(m -> Arrays.equals(m, move));
    }

    private int countPieces(Board board) {
        int count = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                if (board.getPiece(r, c) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private int alphaBeta(Game game, int depth, int alpha, int beta, boolean isMaximizingPlayer) {
        if (depth == 0 || game.isGameOver() != null) {
            return evaluateBoard(game.getBoard(), isMaximizingPlayer);
        }

        List<int[]> moves = getAllPossibleMoves(game);
        
        if (isMaximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                int eval = alphaBeta(newGame, depth-1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                Game newGame = game.clone();
                applyMove(newGame, move);
                int eval = alphaBeta(newGame, depth-1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private int evaluateBoard(Board board, boolean isMaximizingPlayer) {
        PlayerType player = isMaximizingPlayer ? getPlayerType() : getOpponentPlayer(getPlayerType());
        int score = 0;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null) {
                    score += (piece.getOwner() == player) ? 
                        getPieceValue(piece) : -getPieceValue(piece);
                }
            }
        }
        
        score += evaluatePieceControl(board, player) * 0.6;
        score += evaluateOpponentControl(board, player) * 0.8;
        score += evaluatePieceCooperation(board, player) * 0.5;
        score += evaluateOverExtension(board, player) * 0.7;
        score += evaluateDangerPatterns(board, player) * 0.6;
        score += evaluateMobility(board, player) * 0.7;
        score += evaluateDefensivePositions(board, player) * 0.5;
        score += evaluateKingProtection(board, player) * 0.6;
        score += evaluateOpponentTryThreat(board, player, getOpponentPlayer(player)) * 0.7;
        score += evaluateCentralControl(board, player) * 0.6;
        
        return score;
    }

    private int evaluateOpponentControl(Board board, PlayerType player) {
        int penalty = 0;
        PlayerType opponent = getOpponentPlayer(player);
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponent) {
                    int threatCount = piece.getPossibleMoves(r, c, board).size();
                    penalty -= threatCount * OPPONENT_MOBILITY_PENALTY;
                    
                    if ((player == PlayerType.PLAYER1 && r <= 1) || 
                        (player == PlayerType.PLAYER2 && r >= 2)) {
                        penalty -= 15;
                    }
                }
            }
        }
        
        return penalty;
    }

    private int evaluateOverExtension(Board board, PlayerType player) {
        int penalty = 0;
        int homeRow = (player == PlayerType.PLAYER1) ? 0 : 3;
        
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player) {
                    int distance = (player == PlayerType.PLAYER1) ? r : 3 - r;
                    if (distance > 2) {
                        penalty -= OVEREXTENSION_PENALTY * distance;
                        
                        if (piece instanceof Lion) {
                            penalty -= LION_OVEREXTENSION_PENALTY;
                        }
                    }
                }
            }
        }
        
        return penalty;
    }
}
