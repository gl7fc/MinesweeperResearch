public class Main {
    public static void main(String[] args) {
        int size = 5;
        int bombs = 10;
        int puzzles = 1;

        // サイズ・地雷数を指定してランダムなパズルを生成
        int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
        System.out.println("===== 初期盤面 (正解) =====");
        PuzzleGenerator.printBoard(board, size);

        for (int i = 0; i < puzzles; i++) {
            // パズル(問題)を生成
            PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
            int[] puzzle = pm.minimizeHints();

            System.out.println("\n===== 生成された問題 (盤面" + (i + 1) + ") =====");
            PuzzleGenerator.printBoard(puzzle, size);

            // ---------------------------------------------------
            // HintCountCalculator を使用して難易度を判定
            // ---------------------------------------------------
            System.out.println("\n--- 難易度解析実行中... ---");

            // puzzle: 問題, board: 正解, size: サイズ
            HintCountCalculator calculator = new HintCountCalculator(puzzle, board, size);

            // 計算実行
            calculator.calculate();

            // 結果取得
            int[] difficulties = calculator.getDifficultyMap();

            // 結果表示
            System.out.println("--- 解析結果 ---");
            System.out.println(" [数字]: そのセルを解くのに必要だったヒント数(k)");
            System.out.println(" [ . ]: 元から表示されていたヒント");
            System.out.println(" [ * ]: 論理的に確定できなかったセル\n");

            printAnalysis(puzzle, difficulties, size);
        }
    }

    private static void printAnalysis(int[] puzzleBoard, int[] difficulties, int size) {
        for (int i = 0; i < size * size; i++) {
            // 問題盤面で元々ヒント(0以上)だった場所
            if (puzzleBoard[i] >= 0) {
                System.out.printf(" . ");
            }
            // 空白セルだった場所
            else {
                int level = difficulties[i];
                if (level == -1) {
                    System.out.print(" * "); // 未解決
                } else {
                    System.out.printf(" %d ", level); // 難易度
                }
            }

            if ((i + 1) % size == 0) {
                System.out.println();
            }
        }
    }
}