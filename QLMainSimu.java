//Gameクラスのインスタンスを作成し,
//startGame()メソッドを読み出すことでゲームを開始

public class QLMainSimu {
    public static void main(String[] args) {
	QLearn trialedQLearn = new QLearn("QLearn");
        trialedQLearn.trial(10000);  // 学習
        // クラス名 変数名 = new クラス名();
        // Game game = new Game(); // ゲームのインスタンスを作成
        // game.startGame(); // ゲーム開始
        Game.QLrunSimulations(100, trialedQLearn); // AI同士の対戦を100回シミュレーション
    }
}
