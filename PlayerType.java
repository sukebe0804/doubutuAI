public enum PlayerType {
    PLAYER1, PLAYER2;

        // 栗
    public PlayerType opponent() {
	return this == PLAYER1 ? PLAYER2 : PLAYER1;
    }
}
