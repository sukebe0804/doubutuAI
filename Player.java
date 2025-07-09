import java.util.ArrayList;
import java.util.List;

public class Player {
    private String name;
    private PlayerType playerType; // このプレイヤーがどちらの駒を扱うか
    private List<Piece> capturedPieces; // 手駒

    public Player(String name) {
        this.name = name;
        this.capturedPieces = new ArrayList<>(); // 手駒を格納するための空のリストを初期化
        // GameクラスでPlayerTypeを正しく設定することを推奨
        // 現状の簡易的な判定方法:
        // この処理はすでにGame.javaで実装済み
        // if (name.equals("player")) {
        //     this.playerType = PlayerType.PLAYER1;
        // } else if (name.equals("enemy")) {
        //     this.playerType = PlayerType.PLAYER2;
        // } else {
        //     System.out.println("kokokko");
        // }
    }

    public String getName() {
        return name; //nameを外部から取得
    }

    public List<Piece> getCapturedPieces() {
        return capturedPieces;
    }

    public void addCapturedPiece(Piece piece) {
        // 奪った駒は、自分のPlayerTypeに合わせて向きを変え、成り状態を解除
        piece.setOwner(this.playerType);
        piece.unPromote();
        this.capturedPieces.add(piece);
    }

    public void removeCapturedPiece(Piece piece) {
        this.capturedPieces.remove(piece);
    }

    public PlayerType getPlayerType() {
        return playerType;
    }

    // PlayerTypeを設定するsetter（Gameクラスから設定するために必要）
    public void setPlayerType(PlayerType playerType) {
        this.playerType = playerType;
    }
}