//Gameクラスのインスタンスを作成し,
//startGame()メソッドを読み出すことでゲームを開始

public class MainSimu {
    public static void main(String[] args) {
        // クラス名 変数名 = new クラス名();
        // Game game = new Game(); // ゲームのインスタンスを作成
        // game.startGame(); // ゲーム開始
        // 変更:ここに使いたい変数の型を指定
	// QLearnを実行する際は別途学習関数を読み出す必要あり
	// こんな感じ ↓
	// QLearn PlayerA = new QLearn("QLearn");
	// PlayerA.trial(1000);
	    RandomPlayer PlayerA = new RandomPlayer("Random");
	    // MinMax PlayerB = new MinMax("MinMax");
        AI_gj PlayerB = new AI_gj("AI_gj");
        Game game = new Game(PlayerA, PlayerB); // ゲームのインスタンスを作
        Game.runSimulations(100, PlayerA, PlayerB); // AI同士の対戦を100回シミュレーション
    }
}
