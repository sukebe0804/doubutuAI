import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Game implements Cloneable {
    private Board board;
    private Scanner scanner;
    private boolean silentMode = false; // サイレントモードフラグ
    private Player currentPlayer;

    // PlayerAの指定
    private RandomPlayer PlayerA; // ランダムプレイヤー
    // private HumanPlayer PlayerA;

    // -----------------------------------------------------------------------

    // AIごとの変更点その1
    // 自分で作成した(AIname).javaのクラスのインスタンスをGame.javaで宣言.
    // 対戦時に使用するAIを切り替えるのに使用.
    // 作成者名をコードの隣に記述してほしい!
    // とりあえず今はRandomPlayerをPlayerAにしてるからPlayerBのとこに各自でかいとってー

    // PlayerBの指定
    // 例: 西岡の作成したAI(MinMax.java)の場合、以下のように記述する.
    // private 変数のデータ型 変数(ここはPlayerB固定)
    private MinMax PlayerB; // 西岡

    // -----------------------------------------------------------------------


    // トライルール関連のフィールド
    private PlayerType trialPlayer = null; // トライしたプレイヤーのタイプを保持
    private int trialRow = -1;             // トライしたライオンの行座標
    private int trialCol = -1;             // トライしたライオンの列座標
    
    public Game() {
        board = new Board();
        scanner = new Scanner(System.in);
        this.PlayerA = new RandomPlayer("RandomPlayer");
        // this.PlayerA = new HumanPlayer("Human");

        // -----------------------------------------------------------------------

        // AIごとの変更点その2
        // 例: 西岡の作成したAI(MinMax.java)の場合、以下のように記述する.
        // this.PlayerB = new 変数の型("作成したAIの名前")
        this.PlayerB = new MinMax("MinMax"); // 西岡

        // -----------------------------------------------------------------------

        System.out.println("PlayerA: " + PlayerA);
        System.out.println("PlayerB: " + PlayerB);

        PlayerA.setPlayerType(PlayerType.PLAYER1);
        PlayerB.setPlayerType(PlayerType.PLAYER2);

        currentPlayer = this.PlayerB; // PlayerA, or Bどちらを先手にするかはここで指定する.
        initializeGame();
    }

    private void initializeGame() {
        // PLAYER1の駒
        board.placePiece(new Kirin(PlayerType.PLAYER1), 0, 0);
        board.placePiece(new Lion(PlayerType.PLAYER1), 0, 1);
        board.placePiece(new Zou(PlayerType.PLAYER1), 0, 2);
        board.placePiece(new Hiyoko(PlayerType.PLAYER1), 1, 1);

        // PLAYER2の駒
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

            if (currentPlayer instanceof HumanPlayer) {
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
            System.out.println("PlayerAの勝利！");
        } else if (winner == PlayerType.PLAYER2) {
            System.out.println("PlayerBの勝利！");
        }
        System.out.println("--- ゲーム終了 ---");
        scanner.close();
    }

    private void printCapturedPieces() {
        System.out.print("PlayerAの手駒: ");
        // PlayerAはPlayerType.PLAYER1
        if (PlayerA.getCapturedPieces().isEmpty()) { // PlayerAのcapturedPiecesを表示
            System.out.println("なし");
        } else {
            PlayerA.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
            System.out.println();
        }

        System.out.print("PlayerBの手駒: ");
        // PlayerBはPlayerType.PLAYER2
        if (PlayerB.getCapturedPieces().isEmpty()) { // PlayerBのcapturedPiecesを表示
            System.out.println("なし");
        } else {
            PlayerB.getCapturedPieces().forEach(p -> System.out.print(p.getSymbol() + " "));
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

                    // 実際の手番でのperformMoveは、isKingInCheckによる合法性チェックを含まない
                    // そのため、ここではisValidMoveAndNotIntoCheckを呼び出して事前にチェックする
                    if (isValidMoveAndNotIntoCheck(currentPlayer.getPlayerType(), fromRow, fromCol, toRow, toCol)) {
                         moveMade = performMove(fromRow, fromCol, toRow, toCol);
                    } else {
                        System.out.println("その手は無効です。自分のライオンが王手になります。");
                    }

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

                    // 実際の手番でのperformDropは、isKingInCheckによる合法性チェックを含まない
                    // そのため、ここではisValidDropAndNotIntoCheckを呼び出して事前にチェックする
                    if (isValidDropAndNotIntoCheck(currentPlayer.getPlayerType(), pieceToDrop, dropRow, dropCol)) {
                        moveMade = performDrop(pieceToDrop, dropRow, dropCol);
                    } else {
                        System.out.println("そこには打てません。自分のライオンが王手になります。");
                    }

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
        // AIに手を選ばせる（移動と打つ手を含む）
        // 現在のプレイヤー（currentPlayer）のchooseMoveメソッドを呼び出す
        printIfNotSilent("AI is thinking...");
        int[] move = currentPlayer.chooseMove(this); // ここを修正

        if (move != null) {
            // CPUの実際の手番ではサイレントモードを無効にしてメッセージを表示
            setSilentMode(false); 
            if (move[0] == -1) { // 手駒を打つ手
                // chooseMove内で合法性チェック済みのため、ここではそのまま実行
                // currentPlayerの手駒リストから駒を取得
                Piece pieceToDrop = currentPlayer.getCapturedPieces().get(move[1]); // ここを修正
                System.out.println(currentPlayer.getName() + "は「" + pieceToDrop.getSymbol() + "」を " + move[2] + "," + move[3] + " に打ちます！");
                performDrop(pieceToDrop, move[2], move[3]);
            } else { // 駒の移動
                // chooseMove内で合法性チェック済みのため、ここではそのまま実行
                System.out.println(currentPlayer.getName() + "は" + move[0] + "," + move[1] + " から " + move[2] + "," + move[3] + " へ動かします！");
                performMove(move[0], move[1], move[2], move[3]);
            }
        } else {
            // AIが合法手を見つけられない場合（詰み、またはバグ）
            printIfNotSilent("AIは合法手を見つけられませんでした。");
        }
    }

    // 駒を動かす処理
    public boolean performMove(int fromRow, int fromCol, int toRow, int toCol) {
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
            printIfNotSilent(currentPlayer.getName() + "は相手の「" + capturedPiece.getSymbol() + "」を捕獲しました！");
        }

        board.removePiece(fromRow, fromCol);
        board.placePiece(pieceToMove, toRow, toCol);

        // ひよこの成り判定
        if (pieceToMove instanceof Hiyoko && !pieceToMove.isPromoted()) {
            // 自分の駒が敵陣の一番奥に到達した場合に成る
            // PlayerAの場合はrow=3、Player2(人間)の場合はrow=0
            if (currentPlayer.getPlayerType() == PlayerType.PLAYER1 && toRow == 3) {
                pieceToMove.promote();
                printIfNotSilent(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！");
            } else if (currentPlayer.getPlayerType() == PlayerType.PLAYER2 && toRow == 0) {
                pieceToMove.promote();
                printIfNotSilent(currentPlayer.getName() + "の「ひよこ」が「にわとり」に成りました！");
            }
        }
        return true;
    }

    // 手駒を打つ処理
    public boolean performDrop(Piece pieceToDrop, int dropRow, int dropCol) {
        if (!board.isValidCoordinate(dropRow, dropCol)) {
            return false;
        }
        if (!board.isEmpty(dropRow, dropCol)) {
            return false;
        }

        currentPlayer.removeCapturedPiece(pieceToDrop);
        board.placePiece(pieceToDrop, dropRow, dropCol);
        printIfNotSilent(currentPlayer.getName() + "は手駒の「" + pieceToDrop.getSymbol() + "」を" + dropRow + "," + dropCol + "に打ちました！");
        return true;
    }

    // プレイヤーの切り替え
    public void switchPlayer() { // private から public に変更
        if (currentPlayer == PlayerA) {
            currentPlayer = PlayerB;
        } else {
            currentPlayer = PlayerA;
        }
    }

    // 王手チェック
    public boolean isKingInCheck(PlayerType playerType) {
        int kingRow = -1;
        int kingCol = -1;

        // 自分のライオンの位置を見つける
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion && piece.getOwner() == playerType) {
                    kingRow = r;
                    kingCol = c;
                    break;
                }
            }
            if (kingRow != -1) break;
        }

        if (kingRow == -1) {
            // ライオンが盤面にない（既に取られている）場合は王手ではない
            // これはゲーム終了の条件で処理されるべき
            return false;
        }

        // 相手の駒が自分のライオンを攻撃しているかチェック
        PlayerType opponentPlayerType = (playerType == PlayerType.PLAYER1) ? PlayerType.PLAYER2 : PlayerType.PLAYER1;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == opponentPlayerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        if (move[0] == kingRow && move[1] == kingCol) {
                            return true; // 相手の駒がライオンを攻撃している
                        }
                    }
                }
            }
        }
        return false;
    }

    // 王手詰み判定（このメソッドはAIのロジックで利用される）
    public boolean isCheckmate(PlayerType playerType) {
        // 自分のライオンが王手であるかを確認
        if (!isKingInCheck(playerType)) {
            return false; // 王手でなければ詰みではない
        }

        // 王手から逃れる手があるかチェック
        // 1. 駒を動かして王手を回避する
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == playerType) {
                    List<int[]> possibleMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] move : possibleMoves) {
                        // この手を試してみて、王手にならないか確認
                        if (isValidMoveAndNotIntoCheck(playerType, r, c, move[0], move[1])) {
                            return false; // 王手から逃れる手が見つかった
                        }
                    }
                }
            }
        }

        // 2. 手駒を打って王手を回避する
        Player currentPlayerReference = null;
        if (playerType == PlayerType.PLAYER1) {
            currentPlayerReference = PlayerA;
        } else {
            currentPlayerReference = PlayerB;
        }

        for (Piece pieceToDrop : currentPlayerReference.getCapturedPieces()) {
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 空いているマスであり、かつその手が王手にならないかチェック
                    if (board.isEmpty(r, c) && isValidDropAndNotIntoCheck(playerType, pieceToDrop, r, c)) {
                        return false; // 王手から逃れる手が見つかった
                    }
                }
            }
        }

        return true; // 王手から逃れる手がない
    }

    public boolean isValidMoveAndNotIntoCheck(PlayerType playerType, int fromRow, int fromCol, int toRow, int toCol) {
        Game simulatedGame = this.clone();
        simulatedGame.setSilentMode(true);
        // ここではまだperformMoveが王手チェックを含まないため、先に試行し、その結果でチェック
        // simulateGameのcurrentPlayerを正しく設定
        simulatedGame.setCurrentPlayer((playerType == PlayerType.PLAYER1) ? simulatedGame.PlayerA : simulatedGame.PlayerB);
        boolean moveSuccessful = simulatedGame.performMove(fromRow, fromCol, toRow, toCol);
        
        return moveSuccessful && !simulatedGame.isKingInCheck(playerType);
    }

    public boolean isValidDropAndNotIntoCheck(PlayerType playerType, Piece pieceToDrop, int dropRow, int dropCol) {
        Game simulatedGame = this.clone();
        simulatedGame.setSilentMode(true);

        // クローンされたゲームのcurrentPlayerを正しく設定
        simulatedGame.setCurrentPlayer((playerType == PlayerType.PLAYER1) ? simulatedGame.PlayerA : simulatedGame.PlayerB);
        
        Piece clonedPieceToDrop = null;
        // オリジナルのpieceToDropと同じクラスとオーナーを持つ駒をcapturedPiecesから探す
        // simulatedGame.currentPlayer の手駒リストから探す
        for(Piece cp : simulatedGame.currentPlayer.getCapturedPieces()){
            if(cp.getClass() == pieceToDrop.getClass() && cp.getOwner() == pieceToDrop.getOwner()){
                clonedPieceToDrop = cp;
                break;
            }
        }
        if(clonedPieceToDrop == null) return false; // クローンされた駒が見つからない場合はエラーだが、通常は発生しないはず

        boolean dropSuccessful = simulatedGame.performDrop(clonedPieceToDrop, dropRow, dropCol);
        
        return dropSuccessful && !simulatedGame.isKingInCheck(playerType);
    }

    public void makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        Piece pieceToMove = board.getPiece(fromRow, fromCol);

        // 移動先に駒がある場合は捕獲
        Piece capturedPiece = board.getPiece(toRow, toCol);
        if (capturedPiece != null) {
            // 捕獲された駒を相手の手駒に追加
            // currentPlayer の情報ではなく、pieceToMove の owner に基づいて判断
            if (pieceToMove.getOwner() == PlayerType.PLAYER1) {
                PlayerA.addCapturedPiece(capturedPiece);
            } else {
                PlayerB.addCapturedPiece(capturedPiece);
            }
        }

        // 駒を移動
        board.removePiece(fromRow, fromCol);
        board.placePiece(pieceToMove, toRow, toCol);

        // 成りの判定 (ひよこのみ)
        if (pieceToMove instanceof Hiyoko) {
            // ひよこが敵陣最奥に到達した場合に成る
            if (pieceToMove.getOwner() == PlayerType.PLAYER1 && toRow == Board.ROWS - 1) { // Player1が敵陣最奥 (行3)
                ((Hiyoko) pieceToMove).promote();
            } else if (pieceToMove.getOwner() == PlayerType.PLAYER2 && toRow == 0) { // Player2が敵陣最奥 (行0)
                ((Hiyoko) pieceToMove).promote();
            }
        }
        
        // ライオンがトライしたかどうかのチェック (AIシミュレーション中は直接勝利判定には影響しないが、状態は更新)
        if (pieceToMove instanceof Lion) {
            if (pieceToMove.getOwner() == PlayerType.PLAYER1 && toRow == 0) { // Player1のライオンが敵陣の最奥に到達
                trialPlayer = PlayerType.PLAYER1;
                trialRow = toRow;
                trialCol = toCol;
            } else if (pieceToMove.getOwner() == PlayerType.PLAYER2 && toRow == Board.ROWS - 1) { // Player2のライオンが敵陣の最奥に到達
                trialPlayer = PlayerType.PLAYER2;
                trialRow = toRow;
                trialCol = toCol;
            } else {
                // トライ位置から離れたらリセット
                trialPlayer = null;
                trialRow = -1;
                trialCol = -1;
            }
        }
    }

    public void makeDrop(Piece pieceToDrop, int toRow, int toCol) {
        // 手駒リストから駒を削除
        // pieceToDrop の owner に基づいて、どちらのプレイヤーの手駒かを判断
        if (pieceToDrop.getOwner() == PlayerType.PLAYER1) {
            PlayerA.removeCapturedPiece(pieceToDrop);
        } else {
            PlayerB.removeCapturedPiece(pieceToDrop);
        }
        // 盤面に駒を配置
        board.placePiece(pieceToDrop, toRow, toCol);
    }


    // 王手、詰み、トライの判定と勝者決定
    public PlayerType isGameOver() {
        // 王手詰み判定
        if (isCheckmate(PlayerType.PLAYER1)) {
            printIfNotSilent("PlayerAが詰みました。");
            return PlayerType.PLAYER2; // Player1が詰んだのでPlayer2の勝ち
        }
        if (isCheckmate(PlayerType.PLAYER2)) {
            printIfNotSilent("PlayerBが詰みました。");
            return PlayerType.PLAYER1; // Player2が詰んだのでPlayer1の勝ち
        }

        // トライ判定（ライオンが敵陣の一番奥の行に到達）
        // Player1のライオンがPlayer2の陣地（row=3）に到達
        for (int col = 0; col < Board.COLS; col++) {
            Piece piece = board.getPiece(3, col); // row=3 はPlayer2の初期位置側
            if (piece instanceof Lion && piece.getOwner() == PlayerType.PLAYER1) {
                // そのライオンが王手でないかチェック
                if (!isKingInCheck(PlayerType.PLAYER1)) {
                     // トライルール: トライした直後に王手でなければ勝ち
                    printIfNotSilent("PlayerAのライオンが敵陣に到達しました！");
                    return PlayerType.PLAYER1;
                }
            }
        }
        // Player2のライオンがPlayer1の陣地（row=0）に到達
        for (int col = 0; col < Board.COLS; col++) {
            Piece piece = board.getPiece(0, col); // row=0 はPlayer1の初期位置側
            if (piece instanceof Lion && piece.getOwner() == PlayerType.PLAYER2) {
                // そのライオンが王手でないかチェック
                if (!isKingInCheck(PlayerType.PLAYER2)) {
                    // トライルール: トライした直後に王手でなければ勝ち
                    printIfNotSilent("PlayerBのライオンが敵陣に到達しました！");
                    return PlayerType.PLAYER2;
                }
            }
        }

        // どちらかのライオンが取られた場合
        boolean player1LionExists = false;
        boolean player2LionExists = false;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece instanceof Lion) {
                    if (piece.getOwner() == PlayerType.PLAYER1) {
                        player1LionExists = true;
                    } else if (piece.getOwner() == PlayerType.PLAYER2) {
                        player2LionExists = true;
                    }
                }
            }
        }

        if (!player1LionExists) {
            printIfNotSilent("PlayerAのライオンが捕獲されました。");
            return PlayerType.PLAYER2; // Player1のライオンがいないのでPlayer2の勝ち
        }
        if (!player2LionExists) {
            printIfNotSilent("PlayerBのライオンが捕獲されました。");
            return PlayerType.PLAYER1; // Player2のライオンがいないのでPlayer1の勝ち
        }
        
        return null; // ゲームがまだ終わっていない
    }

    public Board getBoard() {
        return board;
    }

    // サイレントモード設定メソッド
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    // サイレントモード時のメッセージ表示
    private void printIfNotSilent(String message) {
        if (!silentMode) {
            System.out.println(message);
        }
    }

    // クローン実装
    @Override
    public Game clone() {
        try {
            Game clonedGame = (Game) super.clone();
            clonedGame.board = this.board.clone(); // Boardのディープコピー
            clonedGame.scanner = new Scanner(System.in); // 新しいScannerを作成
            
            // Playerインスタンスもディープコピー
            // PlayerAとPlayerBの型に基づいてクローンを作成
            // PlayerAはRandomPlayerとして宣言されているため、RandomPlayerとしてクローン
            clonedGame.PlayerA = (RandomPlayer) this.PlayerA.clone();

            // ------------------------------------------------------------------

            // AIごとの変更点その3
            // PlayerBはMinMaxとして宣言されているため、MinMaxとしてクローン
            // 例: 西岡の作成したAI(MinMax.java)の場合、以下のように記述する.
            // clonedGame.PlayerB = (変数の型) this.PlayerB.clone();
            clonedGame.PlayerB = (MinMax) this.PlayerB.clone(); // 西岡

            // ------------------------------------------------------------------
            
            // currentPlayerの参照をクローンされたPlayerインスタンスに更新
            if (this.currentPlayer == this.PlayerA) {
                clonedGame.currentPlayer = clonedGame.PlayerA;
            } else {
                clonedGame.currentPlayer = clonedGame.PlayerB;
            }
            
            // トライルール関連のフィールドもコピー（プリミティブ型なのでそのままコピーでOK）
            clonedGame.trialPlayer = this.trialPlayer;
            clonedGame.trialRow = this.trialRow;
            clonedGame.trialCol = this.trialCol;


            return clonedGame;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Should not happen
        }
    }

    // クローンされたゲームのcurrentPlayerを設定するヘルパーメソッド
    // isValidMoveAndNotIntoCheck や isValidDropAndNotIntoCheck で必要
    public void setCurrentPlayer(Player player) {
        this.currentPlayer = player;
    }

    // 現在のプレイヤーを取得するメソッドを公開
    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    // PlayerAを取得するメソッド
    public Player getPlayerA() {
        return PlayerA;
    }

    // PlayerBを取得するメソッド
    public Player getPlayerB() {
        return PlayerB;
    }

    //  ---------------------ここから新メソッドを追加(西岡)------------------------
    public static void runSimulations(int numGames) {
        int player1Wins = 0;
        int player2Wins = 0;
        int draws = 0; // 引き分けをカウント

        System.out.println("--- シミュレーション開始 (全 " + numGames + " ゲーム) ---");

        for (int i = 0; i < numGames; i++) {
            System.out.println("ゲーム " + (i + 1) + " / " + numGames);
            Game game = new Game();
            game.setSilentMode(true); // シミュレーション中はサイレントモードを有効にする
            
            PlayerType winner = null;
            // 盤面の状態が同じ手数が連続した場合、引き分けと判定するためのカウンタ
            // ここでは簡易的に、ゲームが進行しない場合の無限ループを避けるための一時的な対策として、
            // 一定のターン数を超えたら引き分けと見なす
            int turnCount = 0;
            final int MAX_TURNS = 500; // 最大ターン数。これを超えたら引き分けと見なす

            while (winner == null && turnCount < MAX_TURNS) {
                game.handleCpuTurn();
                winner = game.isGameOver();
                if (winner == null) { // 勝者がまだ決まっていない場合のみプレイヤーを切り替える
                    game.switchPlayer();
                }
                turnCount++;
            }

            if (winner == PlayerType.PLAYER1) {
                player1Wins++;
            } else if (winner == PlayerType.PLAYER2) {
                player2Wins++;
            } else {
                draws++; // 最大ターン数を超過した場合は引き分け
            }
        }

        System.out.println("--- シミュレーション結果 ---");
        System.out.println("PlayerA (RandomPlayer) の勝利数: " + player1Wins);
        System.out.println("PlayerB (MinMax) の勝利数: " + player2Wins);
        System.out.println("引き分け数: " + draws);
        System.out.println("--- シミュレーション終了 ---");
    }
    //  ---------------------ここまで新メソッドを追加(西岡)------------------------
}