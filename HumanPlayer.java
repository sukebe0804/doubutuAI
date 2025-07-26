import java.util.List; // 必要に応じてインポート



public class HumanPlayer extends Player {

    public HumanPlayer(String name) {
        super(name);
    }

    @Override
    public int[] chooseMove(Game game) {
        // HumanPlayer はユーザー入力で手を選ぶため、このメソッドは通常呼ばれません。
        // もし呼ばれる場合は、Scannerなどで入力を促すロジックが必要です。
        // 今回のゲームの実行フローでは、GameクラスのhandleHumanTurn()が直接ユーザー入力を処理します。
        // そのため、ここではnullを返すか、例外をスローするのが適切です。
        // nullを返すことで、AIのchooseMoveとは異なることを示します。
        return null;
    }

    @Override
    public HumanPlayer clone() {
        // Player抽象クラスのclone()メソッドを呼び出すことで、
        // capturedPiecesのディープコピーも自動的に行われます。
        HumanPlayer cloned = (HumanPlayer) super.clone();
        // HumanPlayer固有のフィールドがあればここでディープコピーを追加
        return cloned;
    }
}