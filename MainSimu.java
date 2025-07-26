//Gameクラスのインスタンスを作成し,
//startGame()メソッドを読み出すことでゲームを開始

public class MainSimu {
    public static void main(String[] args) {
        // クラス名 変数名 = new クラス名();
        // Game game = new Game(); // ゲームのインスタンスを作成
        // game.startGame(); // ゲーム開始
        Game.runSimulations(100); // AI同士の対戦を100回シミュレーション
    }
}