public class Main {
    public static void main(String[] args) {
        int size = 8;
        int bombs = 26;
        int puzzles = 5;

        // サイズ・地雷数を指定してランダムなパズルを生成
        int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
        System.out.println("=====初期盤面=====");
        PuzzleGenerator.printBoard(board, size);

        // 明日やる
        // 一意解判定をしっかりやる
        // 解答出力を制約行列の行ではなく地雷のあるセル番号で出力する

        for (int i = 0; i < puzzles; i++) {
            PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
            int[] puzzle = pm.minimizeHints();

            System.out.println("");
            System.out.println("=====盤面" + (i + 1) + "=====");
            PuzzleGenerator.printBoard(puzzle, size);
        }
    }
}
