import java.util.List; // Listを使うため

public abstract class Piece {
    protected PlayerType owner; // どちらのプレイヤーの駒か
    protected boolean isPromoted; // 成り駒かどうか

    public Piece(PlayerType owner) {
        this.owner = owner;
        this.isPromoted = false;
    }

    public PlayerType getOwner() {
        return owner;
    }

    public boolean isPromoted() {
        return isPromoted;
    }

    public void promote() {
        this.isPromoted = true;
    }

    /**
     * 駒の成り状態を解除します。
     */
    public void unPromote() {
        this.isPromoted = false;
    }

    /**
     * 駒の所有者を設定します。
     * @param owner 新しい所有者
     */
    public void setOwner(PlayerType owner) {
        this.owner = owner;
    }

    // 各駒が移動可能な相対的な座標のリストを返す抽象メソッド
    // これを各具象駒クラスで実装する
    public abstract List<int[]> getPossibleMoves(int currentRow, int currentCol, Board board);

    // 盤面表示用のシンボル
    public abstract String getSymbol();
}