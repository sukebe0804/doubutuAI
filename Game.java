//動物将棋のゲーム全体の管理

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Game {
    private Board board;
    private Player humanPlayer;
    private RandomPlayer cpuPlayer;
    private Player currentPlayer;
    private Scanner scanner;

    public Game() {
        board = new Board();
        // プレイヤー名を標準的なものに変更
        humanPlayer = new Player("Player1"); // Human Player
        cpuPlayer = new RandomPlayer("Player2"); // CPU Player
        scanner = new Scanner(System.in);

        // PlayerTypeを明示的に設定
        // humanPlayerをPLAYER1に、cpuPlayerをPLAYER2にする場合は以下を調整
        // 現在は humanPlayerがPLAYER2, cpuPlayerがPLAYER1なので、これをPLAYER1, PLAYER2に合わせる
        humanPlayer.setPlayerType(PlayerType.PLAYER2); // Human PlayerをPLAYER1に設定
        cpuPlayer.setPlayerType(PlayerType.PLAYER1);   // CPU PlayerをPLAYER2に設定


        currentPlayer = humanPlayer; // 最初に動くのはPlayer1 (人間)
        initializeGame();
    }

    private void initializeGame() {
        // 標準的な初期配置
        // PLAYER1 (人間) の駒
        board.placePiece(new Kirin(PlayerType.PLAYER1), 0, 0);
        board.placePiece(new Lion(PlayerType.PLAYER1), 0, 1);
        board.placePiece(new Zou(PlayerType.PLAYER1), 0, 2);
        board.placePiece(new Hiyoko(PlayerType.PLAYER1), 1, 1);

        // PLAYER2 (CPU) の駒
        board.placePiece(new Zou(PlayerType.PLAYER2), 3, 0);
        board.placePiece(new Lion(PlayerType.PLAYER2), 3, 1);
        board.placePiece(new Kirin(PlayerType.PLAYER2), 3, 2);
        board.placePiece(new Hiyoko(PlayerType.PLAYER2), 2, 1);
    }

    public void startGame() {
        while (!isGameOver()) {
            board.printBoard(); // 盤面表示
            printCapturedPieces(); // 手駒の表示

            System.out.println("--- " + currentPlayer.getName() + "の番です ---"); // 「でござる」を「です」に変更

            if (currentPlayer == humanPlayer) {
                handleHumanTurn();
            } else {
                handleCpuTurn();
            }

            if (!isGameOver()) {
                switchPlayer();
            }
        }
        System.out.println("--- ゲーム終了 ---"); // メッセージを標準的に変更
        scanner.close();
    }

    private void printCapturedPieces() {
        // プレイヤー名を標準的なものに変更
        System.out.print("Player1の手駒: ");
        if (humanPlayer.getCapturedPieces().isEmpty()) {
            System.out.println("なし");
        } else {
            humanPlayer.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
            System.out.println();
        }

        System.out.print("Player2の手駒: ");
        if (cpuPlayer.getCapturedPieces().isEmpty()) {
            System.out.println("なし");
        } else {
            cpuPlayer.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
            System.out.println();
        }
    }

    private void handleHumanTurn() {
        boolean moveMade = false;
        while (!moveMade) {
            System.out.println("手を選んでください（移動: m, 打つ: d, 手駒一覧: c）: "); // メッセージを標準的に変更
            String choice = scanner.next();

            if (choice.equalsIgnoreCase("m")) {
                // 駒を動かす処理
                try {
                    System.out.print("動かす駒の行 (0-3) と列 (0-2) を入力してください (例: 1 1): "); // メッセージを標準的に変更
                    int fromRow = scanner.nextInt();
                    int fromCol = scanner.nextInt();

                    System.out.print("移動先の行 (0-3) と列 (0-2) を入力してください (例: 0 1): "); // メッセージを標準的に変更
                    int toRow = scanner.nextInt();
                    int toCol = scanner.nextInt();

                    moveMade = performMove(fromRow, fromCol, toRow, toCol);

                    if (!moveMade) {
                        System.out.println("その手は無効です。別の手を試してください。"); // メッセージを標準的に変更
                    }
                } catch (java.util.InputMismatchException e) {
                    System.out.println("入力が不正です。数値を入力してください。"); // メッセージを標準的に変更
                    scanner.next(); // 不正な入力を読み飛ばす
                }
            } else if (choice.equalsIgnoreCase("d")) {
                // 手駒を打つ処理
                if (currentPlayer.getCapturedPieces().isEmpty()) {
                    System.out.println("手駒がありません。"); // メッセージを標準的に変更
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
                        System.out.println("無効な選択です。"); // メッセージを標準的に変更
                        continue;
                    }
                    Piece pieceToDrop = currentPlayer.getCapturedPieces().get(pieceIndex);

                    System.out.print("打つ行 (0-3) と列 (0-2) を入力してください (例: 1 1): "); // メッセージを標準的に変更
                    int dropRow = scanner.nextInt();
                    int dropCol = scanner.nextInt();

                    moveMade = performDrop(pieceToDrop, dropRow, dropCol);

                    if (!moveMade) {
                        System.out.println("そこには打てません。別の手を試してください。"); // メッセージを標準的に変更
                    }
                } catch (java.util.InputMismatchException e) {
                    System.out.println("入力が不正です。数値を入力してください。"); // メッセージを標準的に変更
                    scanner.next(); // 不正な入力を読み飛ばす
                }
            } else if (choice.equalsIgnoreCase("c")) {
                // 手駒一覧表示
                printCapturedPieces();
            } else {
                System.out.println("無効な選択です。 'm' か 'd' を入力してください。"); // メッセージを標準的に変更
            }
        }
    }

    private void handleCpuTurn() {
        System.out.println("Player2が考え中です..."); // メッセージを標準的に変更
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Player2の思考が中断されました！"); // メッセージを標準的に変更
        }

        // ランダムプレイヤーに手を選ばせる（移動と打つ手を含む）
        int[] move = ((RandomPlayer)currentPlayer).chooseRandomMove(this);
        if (move != null) {
            // move[0] == -1 は手駒を打つ手を示す特殊な値
            if (move[0] == -1) { // 手駒を打つ手
                Piece pieceToDrop = cpuPlayer.getCapturedPieces().get(move[1]); // move[1]は手駒のインデックス
                System.out.println("Player2は「" + pieceToDrop.getSymbol() + "」を " + move[2] + "," + move[3] + " に打ちます！"); // メッセージを標準的に変更
                performDrop(pieceToDrop, move[2], move[3]);
            } else { // 駒の移動
                System.out.println("Player2は " + move[0] + "," + move[1] + " から " + move[2] + "," + move[3] + " へ動かします！"); // メッセージを標準的に変更
                performMove(move[0], move[1], move[2], move[3]);
            }
        } else {
            System.out.println("Player2、動ける駒も打てる手駒もありません..."); // メッセージを標準的に変更
            // この場合、ゲームが膠着状態になる可能性もあるため、千日手などのロジックも必要になる
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
            System.out.println(currentPlayer.getName() + "は相手の「" + capturedPiece.getSymbol() + "」を捕獲しました！"); // メッセージを標準的に変更
        }

        board.removePiece(fromRow, fromCol);
        board.placePiece(pieceToMove, toRow, toCol);

        // ひよこの成り判定
        if (pieceToMove instanceof Hiyoko && !pieceToMove.isPromoted()) {
            // 自分の駒が敵陣（相手のスタートライン）の一番奥に到達した場合に成る
            if (currentPlayer.getPlayerType() == PlayerType.PLAYER1 && toRow == 3) {
                 pieceToMove.promote();
                 System.out.println(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！"); // メッセージを標準的に変更
            } else if (currentPlayer.getPlayerType() == PlayerType.PLAYER2 && toRow == 0) {
                 pieceToMove.promote();
                 System.out.println(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！"); // メッセージを標準的に変更
            }
        }
        return true;
    }

    /**
     * 手駒を打つ処理
     */
    private boolean performDrop(Piece pieceToDrop, int dropRow, int dropCol) { 
        // 盤上に打とうとしている手駒オブジェクト、手駒を打つ先の行座標・列座標

        // 1. 指定されたマスが盤の範囲内か
        // 範囲外の場合、falseを返す
        if (!board.isValidCoordinate(dropRow, dropCol)) {
            return false;
        }
        // 2. 指定されたマスが空いているか
        // 空きマスでなかった場合、falseを返す
        if (!board.isEmpty(dropRow, dropCol)) {
            return false;
        }

        // ここまでの処理で指定された位置は
        // 「盤の範囲内で、かつ空いているマス」であることが証明される

        // 3. 手駒をリストから削除し、盤に配置
        currentPlayer.removeCapturedPiece(pieceToDrop); // 現在の手番のプレイヤーの手駒から、今から盤に打つ駒を削除
        board.placePiece(pieceToDrop, dropRow, dropCol); // placePieceメソッドを呼び出し、引数通りの場所に配置
        System.out.println(currentPlayer.getName() + "は手駒の「" + pieceToDrop.getSymbol() + "」を" + dropRow + "," + dropCol + "に打ちました！"); // メッセージを標準的に変更
        return true;
    }


    private boolean isGameOver() { // 勝利条件の設定・判定
        // 初期状態の設定
        boolean humanLionExists = false;
        boolean cpuLionExists = false;

        // ライオンの存在チェック
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion) { //変数を型名の箱に代入可能な場合trueを返す
                    if (piece.getOwner() == PlayerType.PLAYER1) {
                        humanLionExists = true;
                    } else {
                        cpuLionExists = true;
                    }
                }
            }
        }

        // 敵味方のライオンがいない = false
        if (!humanLionExists) {
            System.out.println("Player1のライオンが捕獲されました！ Player2の勝利！");
            return true;
        }
        if (!cpuLionExists) {
            System.out.println("Player2のライオンを捕獲しました！ Player1の勝利！");
            return true;
        }

        // ライオンの敵陣到達（トライルール）の判定
        // プレイヤー1 (人間) が勝つ条件: 自分のライオンが相手の初期位置の行（row=3）に到達した場合
        // 盤の奥の行（row=3）にPlayer1のライオンがいたら勝利
        if (board.getPiece(3,0) instanceof Lion && board.getPiece(3,0).getOwner() == PlayerType.PLAYER1 ||
            board.getPiece(3,1) instanceof Lion && board.getPiece(3,1).getOwner() == PlayerType.PLAYER1 ||
            board.getPiece(3,2) instanceof Lion && board.getPiece(3,2).getOwner() == PlayerType.PLAYER1) {
             System.out.println("Player1のライオンが敵陣に到達しました！ Player1の勝利！"); // メッセージを標準的に変更
             return true;
        }
        // プレイヤー2 (CPU) が勝つ条件: 自分のライオンが相手の初期位置の行（row=0）に到達した場合
        // 盤の奥の行（row=0）にPlayer2のライオンがいたら勝利
        if (board.getPiece(0,0) instanceof Lion && board.getPiece(0,0).getOwner() == PlayerType.PLAYER2 ||
            board.getPiece(0,1) instanceof Lion && board.getPiece(0,1).getOwner() == PlayerType.PLAYER2 ||
            board.getPiece(0,2) instanceof Lion && board.getPiece(0,2).getOwner() == PlayerType.PLAYER2) {
             System.out.println("Player2のライオンが敵陣に到達しました！ Player2の勝利！"); // メッセージを標準的に変更
             return true;
        }

        return false;
    }

    private void switchPlayer() {
        currentPlayer = (currentPlayer == humanPlayer) ? cpuPlayer : humanPlayer;
    }

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