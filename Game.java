import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Game {
    private Board board;
    private Player humanPlayer;
    private RandomPlayer cpuPlayer;
    private Player currentPlayer;
    private Scanner scanner;

    // トライルール関連のフィールド
    private PlayerType trialPlayer = null; // トライしたプレイヤーのタイプを保持
    private int trialRow = -1;             // トライしたライオンの行座標
    private int trialCol = -1;             // トライしたライオンの列座標

    public Game() {
        board = new Board();
        humanPlayer = new Player("Player2"); // Human Player (後手)
        cpuPlayer = new RandomPlayer("Player1"); // CPU Player (先手)
        scanner = new Scanner(System.in);

        // プレイヤータイプを明示的に設定 (Player1がCPU、Player2が人間)
        humanPlayer.setPlayerType(PlayerType.PLAYER2);
        cpuPlayer.setPlayerType(PlayerType.PLAYER1);

        currentPlayer = cpuPlayer; // 最初に動くのはPlayer1 (CPU)
        initializeGame();
    }

    private void initializeGame() {
        // 標準的な初期配置 (Player1がCPU、Player2が人間)
        // PLAYER1 (CPU) の駒
        board.placePiece(new Kirin(PlayerType.PLAYER1), 0, 0);
        board.placePiece(new Lion(PlayerType.PLAYER1), 0, 1);
        board.placePiece(new Zou(PlayerType.PLAYER1), 0, 2);
        board.placePiece(new Hiyoko(PlayerType.PLAYER1), 1, 1);

        // PLAYER2 (人間) の駒
        board.placePiece(new Zou(PlayerType.PLAYER2), 3, 0);
        board.placePiece(new Lion(PlayerType.PLAYER2), 3, 1);
        board.placePiece(new Kirin(PlayerType.PLAYER2), 3, 2);
        board.placePiece(new Hiyoko(PlayerType.PLAYER2), 2, 1);
    }

    public void startGame() {
        // 勝者を示すフィールドを追加
        PlayerType winner = null;

        while (true) { // ループ条件を直接isGameOver()に依存させない
            board.printBoard(); // 盤面表示
            printCapturedPieces(); // 手駒の表示

            System.out.println("--- " + currentPlayer.getName() + "の番です ---");

            if (currentPlayer == humanPlayer) {
                handleHumanTurn();
            } else {
                handleCpuTurn();
            }

            // ゲーム終了判定を行い、勝者がいればループを抜ける
            // isGameOver() は勝利タイプを返すように変更
            winner = isGameOver();
            if (winner != null) {
                break; // 勝者が決定したらループを抜ける
            }
            
            switchPlayer(); // 勝者がいない場合のみプレイヤーを切り替える
        }
        
        // ゲームが終了したら勝者メッセージを表示
        if (winner == PlayerType.PLAYER1) {
            System.out.println("Player1(CPU)の勝利！");
        } else if (winner == PlayerType.PLAYER2) {
            System.out.println("Player2(あなた)の勝利！");
        }
        System.out.println("--- ゲーム終了 ---");
        scanner.close();
    }

    private void printCapturedPieces() {
        System.out.print("Player1(CPU)の手駒: ");
        if (cpuPlayer.getCapturedPieces().isEmpty()) {
            System.out.println("なし");
        } else {
            cpuPlayer.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
            System.out.println();
        }

        System.out.print("Player2(あなた)の手駒: ");
        if (humanPlayer.getCapturedPieces().isEmpty()) {
            System.out.println("なし");
        } else {
            humanPlayer.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
            System.out.println();
        }
    }

    private void handleHumanTurn() {
        boolean moveMade = false;
        while (!moveMade) {
            System.out.println("手を選んでください（移動: m, 打つ: d, 手駒一覧: c）: ");
            String choice = scanner.next();

            if (choice.equalsIgnoreCase("m")) {
                try {
                    System.out.print("動かす駒の行 (0-3) と列 (0-2) を入力してください (例: 1 1): ");
                    int fromRow = scanner.nextInt();
                    int fromCol = scanner.nextInt();

                    System.out.print("移動先の行 (0-3) と列 (0-2) を入力してください (例: 0 1): ");
                    int toRow = scanner.nextInt();
                    int toCol = scanner.nextInt();

                    moveMade = performMove(fromRow, fromCol, toRow, toCol);

                    if (!moveMade) {
                        System.out.println("その手は無効です。別の手を試してください。");
                    }
                } catch (java.util.InputMismatchException e) {
                    System.out.println("入力が不正です。数値を入力してください。");
                    scanner.next(); // 不正な入力を読み飛ばす
                }
            } else if (choice.equalsIgnoreCase("d")) {
                if (currentPlayer.getCapturedPieces().isEmpty()) {
                    System.out.println("手駒がありません。");
                    continue;
                }
                
                System.out.println("どの手駒を打ちますか？");
                for (int i = 0; i < currentPlayer.getCapturedPieces().size(); i++) {
                    System.out.print(i + ": " + currentPlayer.getCapturedPieces().get(i).getSymbol() + " ");
                }
                System.out.println();

                try {
                    int pieceIndex = scanner.nextInt();
                    if (pieceIndex < 0 || pieceIndex >= currentPlayer.getCapturedPieces().size()) {
                        System.out.println("無効な選択です。");
                        continue;
                    }
                    Piece pieceToDrop = currentPlayer.getCapturedPieces().get(pieceIndex);

                    System.out.print("打つ行 (0-3) と列 (0-2) を入力してください (例: 1 1): ");
                    int dropRow = scanner.nextInt();
                    int dropCol = scanner.nextInt();

                    moveMade = performDrop(pieceToDrop, dropRow, dropCol);

                    if (!moveMade) {
                        System.out.println("そこには打てません。別の手を試してください。");
                    }
                } catch (java.util.InputMismatchException e) {
                    System.out.println("入力が不正です。数値を入力してください。");
                    scanner.next(); // 不正な入力を読み飛ばす
                }
            } else if (choice.equalsIgnoreCase("c")) {
                printCapturedPieces();
            } else {
                System.out.println("無効な選択です。 'm' か 'd' を入力してください。");
            }
        }
    }

    private void handleCpuTurn() {
        // ランダムプレイヤーに手を選ばせる（移動と打つ手を含む）
        int[] move = ((RandomPlayer)cpuPlayer).chooseRandomMove(this);
        if (move != null) {
            if (move[0] == -1) { // 手駒を打つ手
                Piece pieceToDrop = cpuPlayer.getCapturedPieces().get(move[1]);
                System.out.println("Player1(CPU)は「" + pieceToDrop.getSymbol() + "」を " + move[2] + "," + move[3] + " に打ちます！");
                performDrop(pieceToDrop, move[2], move[3]);
            } else { // 駒の移動
                System.out.println("Player1(CPU)は " + move[0] + "," + move[1] + " から " + move[2] + "," + move[3] + " へ動かします！");
                performMove(move[0], move[1], move[2], move[3]);
            }
        }
    }

    /**
     * 駒を動かす処理
     */
    private boolean performMove(int fromRow, int fromCol, int toRow, int toCol) {
        Piece pieceToMove = board.getPiece(fromRow, fromCol);

        if (pieceToMove == null || pieceToMove.getOwner() != currentPlayer.getPlayerType()) {
            return false; // 駒がない、または自分の駒ではない
        }

        List<int[]> possibleMoves = pieceToMove.getPossibleMoves(fromRow, fromCol, board);
        boolean isValidMove = false;
        for (int[] move : possibleMoves) {
            if (move[0] == toRow && move[1] == toCol) {
                isValidMove = true;
                break;
            }
        }
        if (!isValidMove) {
            return false; // 不正な移動先
        }

        Piece capturedPiece = board.getPiece(toRow, toCol);
        if (capturedPiece != null) {
            // 相手の駒であれば手駒にする
            currentPlayer.addCapturedPiece(capturedPiece);
            System.out.println(currentPlayer.getName() + "は相手の「" + capturedPiece.getSymbol() + "」を捕獲しました！");
        }

        board.removePiece(fromRow, fromCol);
        board.placePiece(pieceToMove, toRow, toCol);

        // ひよこの成り判定
        if (pieceToMove instanceof Hiyoko && !pieceToMove.isPromoted()) {
            // 自分の駒が敵陣の一番奥に到達した場合に成る
            // Player1(CPU)の場合はrow=3、Player2(人間)の場合はrow=0
            if (currentPlayer.getPlayerType() == PlayerType.PLAYER1 && toRow == 3) {
                 pieceToMove.promote();
                 System.out.println(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！");
            } else if (currentPlayer.getPlayerType() == PlayerType.PLAYER2 && toRow == 0) {
                 pieceToMove.promote();
                 System.out.println(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！");
            }
        }
        return true;
    }

    /**
     * 手駒を打つ処理
     */
    private boolean performDrop(Piece pieceToDrop, int dropRow, int dropCol) { 
        if (!board.isValidCoordinate(dropRow, dropCol)) {
            return false;
        }
        if (!board.isEmpty(dropRow, dropCol)) {
            return false;
        }

        currentPlayer.removeCapturedPiece(pieceToDrop);
        board.placePiece(pieceToDrop, dropRow, dropCol);
        System.out.println(currentPlayer.getName() + "は手駒の「" + pieceToDrop.getSymbol() + "」を" + dropRow + "," + dropCol + "に打ちました！");
        return true;
    }

    /**
     * ゲームが終了したかどうかと、終了した場合の勝者（PlayerType）を返します。
     * ゲームが継続する場合は null を返します。
     */
    private PlayerType isGameOver() {
        boolean humanLionExists = false; // Player2 (人間) のライオンの存在
        boolean cpuLionExists = false;   // Player1 (CPU) のライオンの存在

        // ライオンの存在チェック
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion) {
                    if (piece.getOwner() == PlayerType.PLAYER2) { // Player2 (人間)
                        humanLionExists = true;
                    } else { // Player1 (CPU)
                        cpuLionExists = true;
                    }
                }
            }
        }

        // ライオンが捕獲された場合
        if (!cpuLionExists) { // Player1 (CPU) のライオンがいない = Player2 (人間) の勝利
            // System.out.println("Player1(CPU)のライオンが捕獲されました！ Player2(あなた)の勝利！"); // メッセージを削除
            return PlayerType.PLAYER2;
        }
        if (!humanLionExists) { // Player2 (人間) のライオンがいない = Player1 (CPU) の勝利
            // System.out.println("Player2(あなた)のライオンを捕獲しました！ Player1(CPU)の勝利！"); // メッセージを削除
            return PlayerType.PLAYER1;
        }

        // --- トライルールの見直し部分 ---

        // 前のターンでトライ状態に入ったプレイヤーがいるか
        if (trialPlayer != null) {
            // トライしたライオンがまだその位置にいて、かつそのプレイヤーのライオンであるかを確認
            Piece trialLion = board.getPiece(trialRow, trialCol);
            
            if (trialLion instanceof Lion && trialLion.getOwner() == trialPlayer) {
                // 相手のプレイヤータイプを取得
                PlayerType opponentType = (trialPlayer == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
                
                // 相手が次の手でライオンを確実に取れるかチェック
                if (canOpponentCaptureLion(opponentType, trialRow, trialCol)) {
                    // 相手がライオンを「確実に取れる」場合 -> トライしたプレイヤーの負け
                    System.out.println(trialPlayer.name() + "のライオンは敵陣に到達しましたが、相手に確実に取られるため敗北！");
                    PlayerType winner = (trialPlayer == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
                    trialPlayer = null; // トライ状態をリセット
                    trialRow = -1;
                    trialCol = -1;
                    return winner; // ゲーム終了、勝者を返す
                } else {
                    // 相手がライオンを「取れない」場合 -> トライしたプレイヤーの勝利
                    System.out.println(trialPlayer.name() + "のライオンが敵陣で生き残り、相手に取られないため勝利！");
                    PlayerType winner = trialPlayer;
                    trialPlayer = null; // トライ状態をリセット
                    trialRow = -1;
                    trialCol = -1;
                    return winner; // ゲーム終了、勝者を返す
                }
            } else {
                // ライオンが取られたか動かされたため、トライ状態をリセット
                System.out.println(trialPlayer.name() + "のライオンは敵陣で捕獲されたか移動しました。トライは無効になります。");
                trialPlayer = null;
                trialRow = -1;
                trialCol = -1;
            }
        }

        // 新たなトライの判定（現在のターンでライオンが敵陣に到達したか）
        // currentPlayerは現在の手番のプレイヤー
        
        // Player1 (CPU) のライオンが敵陣（row=3）に到達した場合
        if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
            for (int c = 0; c < 3; c++) {
                Piece piece = board.getPiece(3, c);
                if (piece instanceof Lion && piece.getOwner() == PlayerType.PLAYER1) {
                    // 新規のトライの場合のみ設定 (既にトライ状態であれば更新しない)
                    if (trialPlayer == null) {
                        trialPlayer = PlayerType.PLAYER1;
                        trialRow = 3;
                        trialCol = c;
                        System.out.println("Player1(CPU)のライオンが敵陣に到達しました！相手の次の手で取られなければ勝利です。");
                    }
                    return null; // ゲームはまだ終了しない
                }
            }
        }
        // Player2 (人間) のライオンが敵陣（row=0）に到達した場合
        else if (currentPlayer.getPlayerType() == PlayerType.PLAYER2) {
            for (int c = 0; c < 3; c++) {
                Piece piece = board.getPiece(0, c);
                if (piece instanceof Lion && piece.getOwner() == PlayerType.PLAYER2) {
                    // 新規のトライの場合のみ設定 (既にトライ状態であれば更新しない)
                    if (trialPlayer == null) {
                        trialPlayer = PlayerType.PLAYER2;
                        trialRow = 0;
                        trialCol = c;
                        System.out.println("Player2(あなた)のライオンが敵陣に到達しました！相手の次の手で取られなければ勝利です。");
                    }
                    return null; // ゲームはまだ終了しない
                }
            }
        }
        
        return null; // ゲームはまだ終了していない
    }

    /**
     * 指定された位置のライオンを、指定されたプレイヤーが次の手で捕獲できるかどうかを判定するヘルパーメソッド
     * このメソッドは、盤上の駒による直接的な捕獲の可能性のみをチェックします。
     * 手駒を打つことによる間接的な脅威（王手）は考慮しません。
     * @param opponentType 攻撃側のプレイヤータイプ
     * @param lionRow 捕獲対象のライオンの行座標
     * @param lionCol 捕獲対象のライオンの列座標
     * @return 相手が次の手でライオンを捕獲できる場合 true、できない場合 false
     */
    private boolean canOpponentCaptureLion(PlayerType opponentType, int lionRow, int lionCol) {
        // 盤上の相手の駒を全てチェック
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponentType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // もし駒の移動先にライオンの座標が含まれていれば、そのライオンは取られる可能性がある
                        if (move[0] == lionRow && move[1] == lionCol) {
                            return true; // 捕獲可能
                        }
                    }
                }
            }
        }

        // 動物将棋の手駒打ちでは直接相手の駒を取ることはできないため、
        // 手駒による捕獲可能性のチェックはここでは不要です。
        // （もし「手駒を打って王手をかける」というルールを導入する場合は、ここを拡張する必要があります）

        return false; // 捕獲不可能
    }

    private void switchPlayer() {
        currentPlayer = (currentPlayer == humanPlayer) ? cpuPlayer : humanPlayer;
    }

    // 他のクラスから盤やプレイヤー情報にアクセスするためのゲッターメソッド
    public Board getBoard() {
        return board;
    }
    
    public Player getHumanPlayer() {
        return humanPlayer;
    }

    public Player getCpuPlayer() {
        return cpuPlayer;
    }
}