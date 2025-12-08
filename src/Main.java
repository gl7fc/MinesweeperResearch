public class Main {
    public static void main(String[] args) {
        int[] board = { -1, -1, -1, -1, 3, -1, -1, 4, -1, -1, 4, -1, 2, 2, -1, 1 };
        int size = 4;

        HintDifficultySolver solver = new HintDifficultySolver(board, size);
        solver.printBoard(); // 初期盤面表示
        solver.solve();
        solver.printBoard(); // 結果表示

        int[] needHints = solver.getHintRequired();
        System.out.println("\n必要ヒント数 (空白セルごと):");
        for (int i = 0; i < needHints.length; i++) {
            if (board[i] == -1) {
                System.out.println("セル " + i + " : " +
                        (needHints[i] == -1 ? "推論不可" : needHints[i]));
            }
        }

    }
}