import java.util.Arrays;

/**
 * マインスイーパ難易度解析 メインクラス
 * 世代別解析(GenerationSolver) と 必要ヒント数解析(HintCountCalculator) を比較実行する。
 */
public class Main {
    public static void main(String[] args) {
        int size = 8; // 盤面サイズ
        int bombs = 30; // 地雷数

        // 1. サイズ・地雷数を指定してランダムなパズルを生成
        int[] completeBoard = PuzzleGenerator.generatePuzzle(size, bombs); // 正解盤面

        System.out.println("===== 初期盤面 (正解) =====");
        PuzzleGenerator.printBoard(completeBoard, size);
        // 問題盤面を作成
        PuzzleMinimizer pm = new PuzzleMinimizer(completeBoard, size);
        int[] puzzleBoard = pm.minimizeHints();

        System.out.println("===== 生成された盤面 (問題) =====");
        PuzzleGenerator.printBoard(puzzleBoard, size);

        // ===================================================
        // 解析2: HintCountCalculator (必要情報量 k)
        // ===================================================
        System.out.println("\n------------------------------------------------");
        System.out.println("解析: HintCountCalculator (必要ヒント数 k)");
        System.out.println("------------------------------------------------");

        HintCountCalculator kCalculator = new HintCountCalculator(puzzleBoard, completeBoard, size);
        long startK = System.currentTimeMillis();
        kCalculator.calculate();
        long endK = System.currentTimeMillis();

        // 結果取得
        int[] kDifficulties = kCalculator.getDifficultyMap();

        System.out.println("実行時間: " + (endK - startK) + "ms");
        System.out.println(" [数字]: 必要ヒント数 (k)");
        System.out.println(" [ * ]: 論理的に確定不可");

        printAnalysis(puzzleBoard, kDifficulties, size);

        // ===================================================
        // 解析3: TechniqueAnalyzer (テクニックレベル判定) - Phase 2
        // ===================================================
        System.out.println("\n------------------------------------------------");
        System.out.println("解析: TechniqueAnalyzer (テクニックレベル判定)");
        System.out.println("------------------------------------------------");

        TechniqueAnalyzer tAnalyzer = new TechniqueAnalyzer(puzzleBoard, completeBoard, size);
        long startT = System.currentTimeMillis();
        tAnalyzer.analyze();
        long endT = System.currentTimeMillis();

        int[] tDifficulties = tAnalyzer.getDifficultyMap();

        System.out.println("実行時間: " + (endT - startT) + "ms");
        System.out.println(" [ 1 ]: Lv1-1 埋めるだけ");
        System.out.println(" [ 2 ]: Lv1-2 包含(確定)");
        System.out.println(" [ 5 ]: Lv2   背理法");
        System.out.println(" [ * ]: 現実装(Lv1-1)では解けなかった");

        printAnalysis(puzzleBoard, tDifficulties, size);
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