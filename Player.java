import java.util.ArrayList;
import java.util.List;

public abstract class Player implements Cloneable { // インターフェースから抽象クラスに変更
    protected String name;
    protected PlayerType playerType;
    protected List<Piece> capturedPieces;

    public Player(String name) {
        this.name = name;
        this.capturedPieces = new ArrayList<>();
    }

    public String getName() {
        return name;
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

    public void setPlayerType(PlayerType playerType) {
        this.playerType = playerType;
    }

    // AIが手を決定するための抽象メソッド (MinMaxが実装)
    public abstract int[] chooseMove(Game game); 

    @Override
    public Player clone() { // cloneメソッドをここで実装
        try {
            Player cloned = (Player) super.clone();
            // 手駒リストのディープコピー
            cloned.capturedPieces = new ArrayList<>();
            for (Piece p : this.capturedPieces) {
                cloned.capturedPieces.add(p.clone()); // Piece も clone できるように
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}