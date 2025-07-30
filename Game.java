import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Random;
import java.util.HashMap; // 追加
import java.util.Map;     // 追加

public class Game implements Cloneable {
    private Board board;
    private Scanner scanner;
    private boolean silentMode = false; // サイレントモードフラグ
    private Player currentPlayer;
    private PlayerType forcedWinner = null;
    private int turnNumber; // ターン数カウンター
    private Map<String, Integer> positionHistory; // ★追加：局面の履歴
    private static final int SENTE_NICHI_TE = 4; // ★追加：千日手判定の回数

    private Player PlayerA; // 変更:強制プレイヤー型
    private Player PlayerB; // 変更:同様
    
    // トライルール関連のフィールド
    private PlayerType trialPlayer = null; // トライしたプレイヤーのタイプを保持
    private int trialRow = -1;             // トライしたライオンの行座標
    private int trialCol = -1;             // トライしたライオンの列座標
    
    public Game() {}
    
    public Game(Player A, Player B) {
        board = new Board();
        scanner = new Scanner(System.in);
	setPlayers(A, B); // 変更:セットメソットでPlayerA, Bを自動設定

        System.out.println("PlayerA: " + PlayerA);
        System.out.println("PlayerB: " + PlayerB);

        PlayerA.setPlayerType(PlayerType.PLAYER1);
        PlayerB.setPlayerType(PlayerType.PLAYER2);

        currentPlayer = this.PlayerA; // PlayerA, or Bどちらを先手にするかはここで指定する.
        initializeGame();
        this.turnNumber = 0; // ターン数カウンターの初期化
        this.positionHistory = new HashMap<>(); // ★追加：局面履歴の初期化
    }
    
    // 削除:不要なコンストラクタ(OLearnでのMainで使用していたもの)
    
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

            this.turnNumber++; // ターン数をインクリメント

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
            
            // ★追加：千日手チェックをここに追加
            // 手番を切り替える前に、現在の局面が千日手になっていないかを確認する
            if (checkSennichite()) {
                printIfNotSilent("千日手により引き分け！"); // サイレントモードの場合は表示されない
                winner = null; // 引き分けを示す
                break; 
            }

            switchPlayer(); // 勝者がいない場合のみプレイヤーを切り替える
        }
        
        // ゲームが終了したら勝者メッセージを表示
        if (winner == PlayerType.PLAYER1) {
            System.out.println("PlayerAの勝利！");
        } else if (winner == PlayerType.PLAYER2) {
            System.out.println("PlayerBの勝利！");
        } else { // winner が null の場合（引き分け）
            System.out.println("--- ゲームは引き分けです ---");
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
        int[] move = null;

        // 最初の2ターン（各プレイヤーの最初の1手）のみ、ライオンを除外したランダムな手を指す
        if (turnNumber <= 4) {
            move = getRandomLegalMove(currentPlayer);
            if (move == null) {
                printIfNotSilent("ランダムな合法手を見つけられませんでした。AIが詰んだ可能性があります。");
                if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
                    this.forcedWinner = PlayerType.PLAYER2;
                } else {
                    this.forcedWinner = PlayerType.PLAYER1;
                }
                return;
            }
        } else {
            // 3ターン目以降は通常のAIロジックで手を選ぶ
            move = currentPlayer.chooseMove(this);
        }

        if (move != null) {
            // CPUの実際の手番ではサイレントモードを無効にしてメッセージを表示
            setSilentMode(false); 
            if (move[0] == -1) { // 手駒を打つ手
                // chooseMove内で合法性チェック済みのため、ここではそのまま実行
                // currentPlayerの手駒リストから駒を取得
                // move[1]はcapturedPiecesリストのインデックス
                // ※注意: QLearnのchooseMoveで直接Pieceオブジェクトを渡している場合、
                // ここでcapturedPieces.get(move[1])が正しいか確認してください。
                // getRandomLegalMoveではcapturedPiecesのインデックスを渡しているので問題ありません。
                Piece pieceToDrop = currentPlayer.getCapturedPieces().get(move[1]);
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
            // 合法手が見つからなかったプレイヤーの負けにする
            // 現在のプレイヤーの相手が勝者となる
            if (currentPlayer.getPlayerType() == PlayerType.PLAYER1) {
                this.forcedWinner = PlayerType.PLAYER2; // Player1が合法手なし → Player2の勝ち
            } else {
                this.forcedWinner = PlayerType.PLAYER1; // Player2が合法手なし → Player1の勝ち
            }
            // メソッドを終了
            return; 
        }
    }

    // ランダムな合法手を取得するメソッド
    private int[] getRandomLegalMove(Player player) {
        List<int[]> legalMoves = new ArrayList<>();

        // 駒の移動による合法手の収集
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.getOwner() == player.getPlayerType()) {
                    // ★修正：ターン数が1または2（初手）の場合、かつ駒がライオンの場合はスキップ
                    if (turnNumber <= 2 && piece instanceof Lion) {
                        continue; // ライオンの移動は除外
                    }

                    List<int[]> possiblePieceMoves = piece.getPossibleMoves(r, c, board);
                    for (int[] toCoord : possiblePieceMoves) {
                        // この移動が王手にならないかチェック
                        // クローンしたゲームでシミュレーションし、王手にならないことを確認
                        if (isValidMoveAndNotIntoCheck(player.getPlayerType(), r, c, toCoord[0], toCoord[1])) {
                            // 移動: {fromRow, fromCol, toRow, toCol}
                            legalMoves.add(new int[]{r, c, toCoord[0], toCoord[1]});
                        }
                    }
                }
            }
        }

        // 手駒を打つ合法手の収集
        // ※手駒にはライオンはないので、ここでの特別な除外処理は不要
        for (int i = 0; i < player.getCapturedPieces().size(); i++) {
            Piece capturedPiece = player.getCapturedPieces().get(i);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    // 盤面が空いており、かつその打ち手が王手にならないかチェック
                    if (board.isEmpty(r, c)) {
                        // クローンしたゲームでシミュレーションし、王手にならないことを確認
                        if (isValidDropAndNotIntoCheck(player.getPlayerType(), capturedPiece, r, c)) {
                            // ドロップ: {-1, 手駒インデックス, dropRow, dropCol}
                            legalMoves.add(new int[]{-1, i, r, c});
                        }
                    }
                }
            }
        }

        if (legalMoves.isEmpty()) {
            return null; // 合法手がない
        }

        // ランダムに1つ選択
        Random rand = new Random();
        return legalMoves.get(rand.nextInt(legalMoves.size()));
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
        // もし強制的に勝者が設定されていたら、その勝者を返す
        if (this.forcedWinner != null) {
            return this.forcedWinner;
        }
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

    // ★追加：局面を文字列化するヘルパーメソッド
    private String getCurrentBoardState() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece == null) {
                    sb.append("NN"); // No Piece
                } else {
                    sb.append(piece.getSymbol()); // 例: 'L1', 'H2'など
                    sb.append(piece.getOwner() == PlayerType.PLAYER1 ? "1" : "2");
                    sb.append(piece.isPromoted() ? "P" : "U"); // Promoted or Unpromoted
                }
            }
        }
        // 手駒の情報も局面の一部として含める (順序を固定することで一意性を保証)
        List<Piece> p1Captured = new ArrayList<>(PlayerA.getCapturedPieces());
        List<Piece> p2Captured = new ArrayList<>(PlayerB.getCapturedPieces());
        p1Captured.sort((p1, p2) -> p1.getSymbol().compareTo(p2.getSymbol()));
        p2Captured.sort((p1, p2) -> p1.getSymbol().compareTo(p2.getSymbol()));

        sb.append("A手駒:");
        p1Captured.forEach(p -> sb.append(p.getSymbol()).append(p.getOwner() == PlayerType.PLAYER1 ? "1" : "2").append(p.isPromoted() ? "P" : "U"));
        sb.append("B手駒:");
        p2Captured.forEach(p -> sb.append(p.getSymbol()).append(p.getOwner() == PlayerType.PLAYER1 ? "1" : "2").append(p.isPromoted() ? "P" : "U"));
        
        // 現在のプレイヤーも局面の一部に含める (手番も考慮するため)
        sb.append("Turn:");
        sb.append(currentPlayer.getPlayerType() == PlayerType.PLAYER1 ? "1" : "2");

        return sb.toString();
    }

    // ★追加：千日手チェックメソッド
    public boolean checkSennichite() {
        String currentState = getCurrentBoardState();
        positionHistory.put(currentState, positionHistory.getOrDefault(currentState, 0) + 1);
        
        // // 千日手判定のログ (デバッグ用)
        // if (!silentMode) {
        //     System.out.println("局面ハッシュ: " + currentState.hashCode() + " -> 出現回数: " + positionHistory.get(currentState));
        // }

        return positionHistory.get(currentState) >= SENTE_NICHI_TE;
    }


    // クローン実装
    @Override
    public Game clone() {
        try {
            Game clonedGame = (Game) super.clone();
            clonedGame.board = this.board.clone(); // Boardのディープコピー
            clonedGame.scanner = new Scanner(System.in); // 新しいScannerを作成
            // 変更:クローン作成も型参照メソット（後述）を使用し自動化
	    clonedGame.PlayerA = this.castPlayer(PlayerA).clone(); 
            clonedGame.PlayerB = this.castPlayer(PlayerB).clone();

            
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
            clonedGame.turnNumber = this.turnNumber; // ターン数もコピー
            clonedGame.positionHistory = new HashMap<>(this.positionHistory); // ★追加：局面履歴もコピー

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
    // 更新:引数の追加
    public static void runSimulations(int numGames, Player A, Player B) {
        int player1Wins = 0;
        int player2Wins = 0;
        int draws = 0; // 引き分けをカウント

        System.out.println("--- シミュレーション開始 (全 " + numGames + " ゲーム) ---");

        for (int i = 0; i < numGames; i++) {
            System.out.println("ゲーム " + (i + 1) + " / " + numGames);
            Game game = new Game(A, B); // 変更:コンストラクタに応じて変更
            game.setSilentMode(true); // シミュレーション中はサイレントモードを有効にする
            
            PlayerType winner = null;
            // 盤面の状態が同じ手数が連続した場合、引き分けと判定するためのカウンタ
            // ここでは簡易的に、ゲームが進行しない場合の無限ループを避けるための一時的な対策として、
            // 一定のターン数を超えたら引き分けと見なす
            int turnCount = 0;
            final int MAX_TURNS = 100; // 最大ターン数。これを超えたら引き分けと見なす

            while (winner == null && turnCount < MAX_TURNS) {
                game.turnNumber = turnCount + 1; // シミュレーション中のturnNumberも更新
                game.handleCpuTurn();
                winner = game.isGameOver();
                if (winner == null) { // 勝者がまだ決まっていない場合のみプレイヤーを切り替える
                    // ★追加：シミュレーション中も千日手チェック
                    if (game.checkSennichite()) {
                        winner = null; // 引き分け
                        break;
                    }
                    game.switchPlayer();
                }
                turnCount++;
            }

            if (winner == PlayerType.PLAYER1) {
                player1Wins++;
            } else if (winner == PlayerType.PLAYER2) {
                player2Wins++;
            } else {
                draws++; // 最大ターン数超過または千日手による引き分け
            }
        }
        

        System.out.println("--- シミュレーション結果 ---");
        System.out.println("PlayerAの勝利数: " + player1Wins);
        System.out.println("PlayerBの勝利数: " + player2Wins);
        System.out.println("引き分け数: " + draws);
        System.out.println("--- シミュレーション終了 ---");
    }
    //  ---------------------ここまで新メソッドを追加(西岡)------------------------

    //  ---------------------ここから新メソッドを追加(加藤)------------------------
    // 削除:使用しないメソットの削除
    // 追加:相手のPlayerを返すメソッド
    public Player getOpponent(Player player) {
	return (player == PlayerA) ? PlayerB : PlayerA;
    }

    // 追加:Playerをセットするメソッド
    public void setPlayers(Player A, Player B) {
	this.PlayerA = castPlayer(A);
	this.PlayerB = castPlayer(B);
    }

    // 追加:Player内の型を参照するメソッド
    private Player castPlayer(Player p) {
	if (p instanceof QLearn) {
	    return (QLearn) p;
	} else if (p instanceof AlphaBeta) {
	    return (AlphaBeta) p;
	} else if (p instanceof MinMax) {
	    return (MinMax) p;
	    //} else if (p instanceof ABPlayer) { //栗政くんの入れたら外して
	    //return (ABPlayer) p;
	} else if (p instanceof AI_gj) {
	    return (AI_gj) p;
	} else if (p instanceof RandomPlayer) {
	    return (RandomPlayer) p;
	} else if (p instanceof HumanPlayer) {
	    return (HumanPlayer) p; // その他のPlayer型そのまま返す
	} else {
	    return p;
	}
    }
    //  ---------------------ここまで新メソッドを追加(加藤)------------------------

    // ----------------------ここから新メソッド追加（栗政）-------------------------
    public PlayerType getCurrentPlayerType() {
        return currentPlayer.getPlayerType();
    }
    // -------------------------ここまで新メソッド追加-----------------------------
}
