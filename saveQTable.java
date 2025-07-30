public class saveQTable {
    public static void main(String[] args) {
	QLearn q = new QLearn("QLearn");
	// 学習
	q.trial(1000);
	q.saveQTable("qtable.dat");
    }
}
