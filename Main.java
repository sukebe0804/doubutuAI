import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        HumanPlayer PlayerB = new HumanPlayer("あなた");
        
        // Scannerをmainメソッド内で宣言し、ゲーム終了まで閉じない
        Scanner scanner = new Scanner(System.in); 
        System.out.println("どのAIと戦いますか？\n以下の選択肢から一つ選択してください.\n（西岡AI: 1, 五籐AI: 2, 加藤AI: 3, 宮田AI: 4, 栗政AI: 5）: ");

        String choice = scanner.next();
        
        Player PlayerA = null; 

        if (choice.equalsIgnoreCase("1")) {
            PlayerA = new MinMax("西岡AI");
        } else if (choice.equalsIgnoreCase("2")) {
            PlayerA = new AI_gj("五籐AI");
        } else if (choice.equalsIgnoreCase("3")) {
            QLearn qlearnPlayer = new QLearn("加藤AI");
            qlearnPlayer.loadQTable("qtable.dat");
            PlayerA = qlearnPlayer; 
        } else if (choice.equalsIgnoreCase("4")) {
            PlayerA = new AlphaBeta("宮田AI");
        } else if (choice.equalsIgnoreCase("5")) {
            PlayerA = new ABPlayer("栗政AI");
        } else {
            System.out.println("無効な選択です。適切な値を入力してください。");
            scanner.close(); // 無効な選択の場合はここで閉じて終了
            return; 
        }

		System.out.println("先手or後手を選択してください（先手: 1, 後手: 2）: ");
        int firstPlayerChoice;
        try {
            firstPlayerChoice = scanner.nextInt();
        } catch (java.util.InputMismatchException e) {
            System.out.println("入力が不正です。数値を入力してください。");
            scanner.close();
            return;
        }
        
        Player firstPlayer = null;
        if (firstPlayerChoice == 1) {
            firstPlayer = PlayerB;
        } else if (firstPlayerChoice == 2) {
            firstPlayer = PlayerA;
        } else {
            System.out.println("無効な選択です。1か2を入力してください。");
            scanner.close();
            return;
        }
        
        if (PlayerA != null) {
            Game game = new Game(PlayerA, PlayerB, firstPlayer); 
            game.startGame(); 
        } else {
            System.out.println("AIプレイヤーが選択されなかったため、ゲームを開始できません。");
        }

        scanner.close(); 
    }
}