import java.util.*;

public class Main {
    public static void main(String[] args) {
        int size = 10;
        int bombs = 30;
        int puzzles = 10;

        // パズル生成
        int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
        System.out.println("===== 初期盤面 (正解) =====");
        PuzzleGenerator.printBoard(board, size);

        for (int i = 0; i < puzzles; i++) {
            // 問題生成
            PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
            int[] puzzle = pm.minimizeHints();

            System.out.println("\n===== 生成された問題 (盤面" + (i + 1) + ", ヒント数=" + pm.getHintCount() + ") =====");
            PuzzleGenerator.printBoard(puzzle, size);

            // // --- HintCountCalculator 解析 ---
            // System.out.println("\n--- [HintCountCalculator] 難易度解析実行中... ---");
            // HintCountCalculator calculator = new HintCountCalculator(puzzle, board,
            // size);
            // calculator.calculate();
            // int[] kHintDifficulties = calculator.getDifficultyMap();

            // System.out.println("--- 解析結果 (k-Hint) ---");
            // printAnalysis(puzzle, kHintDifficulties, size);

            // // --- TechniqueAnalyzer 解析 ---
            // System.out.println("\n--- [TechniqueAnalyzer] テクニック解析実行中... ---");
            // TechniqueAnalyzer analyzer = new TechniqueAnalyzer(puzzle, board, size);
            // analyzer.analyze();
            // int[] taDifficulties = analyzer.getDifficultyMap();

            // System.out.println("\n--- 解析結果 (Technique Level) ---");
            // printAnalysis(puzzle, taDifficulties, size);

            // // --- ヒートマップ用データ出力 ---
            // System.out.println("\n--- Exporting data for heatmap... ---");
            // BoardExporter.exportToCSV(
            // "analysis_data_" + (i + 1) + ".csv",
            // size,
            // board, // 正解盤面
            // puzzle, // 問題盤面
            // taDifficulties, // Technique Level
            // kHintDifficulties // k-Hint
            // );
        }
    }

    private static void printAnalysis(int[] puzzleBoard, int[] difficulties, int size) {
        for (int i = 0; i < size * size; i++) {
            if (puzzleBoard[i] >= 0) {
                System.out.print(" . ");
            } else {
                int level = difficulties[i];
                if (level == -1) {
                    System.out.print(" * ");
                } else {
                    System.out.printf(" %d ", level);
                }
            }
            if ((i + 1) % size == 0) {
                System.out.println();
            }
        }
    }
}

// ===========================================================
// ヒートマップ生成コマンド
// ===========================================================
// Java実行後:
// python heatmap_generator.py analysis_data_1.csv heatmap_1.png
