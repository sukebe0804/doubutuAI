//Gameクラスのインスタンスを作成し,
//startGame()メソッドを読み出すことでゲームを開始

public class Main {
    public static void main(String[] args) {
        // クラス名 変数名 = new クラス名();
	// 変更:ここに使いたい変数の型を指定
	// QLearnを実行する際は別途学習関数を読み出す必要あり
	// こんな感じ ↓
	// QLearn PlayerA = new QLearn("QLearn");
	// PlayerA.trial(1000);
	RandomPlayer PlayerA = new RandomPlayer("Random");
	RandomPlayer PlayerB = new RandomPlayer("Random");
        Game game = new Game(PlayerA, PlayerB); // ゲームのインスタンスを作成
        game.startGame(); // ゲーム開始
    }
}
