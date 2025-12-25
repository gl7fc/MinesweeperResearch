import java.util.Arrays;

/**
 * マインスイーパ難易度解析 メインクラス
 * 世代別解析(GenerationSolver) と 必要ヒント数解析(HintCountCalculator) を比較実行する.
 */
public class Main {
    public static void main(String[] args) {
        int size = 10;
        int bombs = 30; // 難易度調整 (少し簡単に)
        int puzzles = 1;

        // サイズ・地雷数を指定してランダムなパズルを生成
        int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
        // int[] board = {
        //         2, 2, 2, -1, 1, 1, 2, -1, 2, 1,
        //         -1, -1, 2, 1, 1, 1, -1, 2, 2, -1,
        //         -1, 4, 2, 1, 1, 2, 2, 3, 2, 2,
        //         -1, 3, 3, -1, 4, 3, -1, 2, -1, 1,
        //         2, 4, -1, -1, -1, -1, 2, 2, 1, 1,
        //         -1, 4, -1, 6, -1, 3, 1, 0, 0, 0,
        //         -1, 3, 2, -1, 3, 2, 0, 1, 2, 2,
        //         1, 2, 3, 5, -1, 2, 1, 3, -1, -1,
        //         0, 1, -1, -1, -1, 3, 3, -1, -1, 3,
        //         0, 1, 2, 3, 2, 2, -1, -1, 3, 1,
        // };

        System.out.println("===== 初期盤面 (正解) =====");
        PuzzleGenerator.printBoard(board, size);

        for (int i = 0; i < puzzles; i++) {
            // パズル(問題)を生成
            PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
            int[] puzzle = pm.minimizeHints();
            // int[] puzzle = {
            //         -1, 2, -1, -1, -1, 1, -1, -1, -1, 1,
            //         -1, -1, 2, -1, 1, -1, -1, 2, -1, -1,
            //         -1, 4, -1, 1, -1, 2, -1, 3, 2, 2,
            //         -1, 3, 3, -1, 4, 3, -1, -1, -1, -1,
            //         -1, 4, -1, -1, -1, -1, -1, 2, -1, 1,
            //         -1, 4, -1, -1, -1, -1, 1, -1, -1, 0,
            //         -1, 3, 2, -1, -1, 2, -1, -1, -1, 2,
            //         -1, -1, -1, 5, -1, 2, -1, 3, -1, -1,
            //         -1, -1, -1, -1, -1, 3, 3, -1, -1, -1,
            //         -1, 1, -1, -1, 2, 2, -1, -1, -1, 1
            // };

            System.out.println("\n===== 生成された問題 (盤面" + (i + 1) + ") =====");
            PuzzleGenerator.printBoard(puzzle, size);

            // ---------------------------------------------------
            // 1. HintCountCalculator を使用して難易度を判定 (既存)
            // ---------------------------------------------------
            System.out.println("\n--- [HintCountCalculator] 難易度解析実行中... ---");

            // puzzle: 問題, board: 正解, size: サイズ
            HintCountCalculator calculator = new HintCountCalculator(puzzle, board, size);

            // 計算実行
            calculator.calculate();

            // 結果取得
            int[] difficulties = calculator.getDifficultyMap();

            // 結果表示
            System.out.println("--- 解析結果 (k-Hint) ---");
            System.out.println(" [数字]: そのセルを解くのに必要だったヒント数(k)");
            printAnalysis(puzzle, difficulties, size);

            // ---------------------------------------------------
            // 2. TechniqueAnalyzer を使用して難易度を判定 (新規追加)
            // ---------------------------------------------------
            System.out.println("\n--- [TechniqueAnalyzer] テクニック解析実行中... ---");

            TechniqueAnalyzer analyzer = new TechniqueAnalyzer(puzzle, board, size);

            // 解析実行
            analyzer.analyze();

            // ★追加: CSV出力
            analyzer.exportLogToCSV("analysis_log_" + (i + 1) + ".csv");

            // 結果取得
            int[] taDifficulties = analyzer.getDifficultyMap();

            // 結果表示
            System.out.println("\n--- 解析結果 (Technique Level) ---");
            System.out.println(" [ . ]: 初期ヒント");
            System.out.println(" [ 1 ]: Lv1 (Base Hint - 単純な埋め込み)");
            System.out.println(" [ 2 ]: Lv2 (包含テクニック - 差分領域)");
            System.out.println(" [ 3 ]: Lv3 (共通テクニック - 共通部分の地雷数確定)");
            System.out.println(" [ 4 ]: Lv4 (背理法)");
            System.out.println(" [ * ]: 論理的に確定できなかったセル\n");

            printAnalysis(puzzle, taDifficulties, size);
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
                    System.out.printf(" %d ", level); // 難易度/レベル
                }
            }

            if ((i + 1) % size == 0) {
                System.out.println();
            }
        }
    }
}