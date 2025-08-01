import java.util.List;

public abstract class Piece implements Cloneable {
    protected PlayerType owner;
    protected boolean isPromoted;

    public Piece(PlayerType owner) {
        this.owner = owner;
        this.isPromoted = false;
    }

    public PlayerType getOwner() {
        return owner;
    }

    public void setOwner(PlayerType newOwner) {
        this.owner = newOwner;
    }

    public boolean isPromoted() {
        return isPromoted;
    }

    public void promote() {
        this.isPromoted = true;
    }

    public void unPromote() {
        this.isPromoted = false;
    }

    public abstract List<int[]> getPossibleMoves(int currentRow, int currentCol, Board board);

    public abstract String getSymbol();

    @Override
    public Piece clone() {
        try {
            Piece cloned = (Piece) super.clone();
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}