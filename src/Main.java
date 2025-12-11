import java.util.Arrays;

/**
 * マインスイーパ難易度解析 メインクラス
 * 世代別解析(GenerationSolver) と 必要ヒント数解析(HintCountCalculator) を比較実行する。
 */
public class Main {
    public static void main(String[] args) {
        int size = 5; // 盤面サイズ
        int bombs = 10; // 地雷数

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
        // 解析1: GenerationSolver (世代別 / テクニック難易度)
        // ===================================================
        System.out.println("\n------------------------------------------------");
        System.out.println("解析1: GenerationSolver (推論テクニック深度)");
        System.out.println("------------------------------------------------");

        GenerationSolver genSolver = new GenerationSolver(puzzleBoard, completeBoard, size);
        long startGen = System.currentTimeMillis();
        genSolver.analyze();
        long endGen = System.currentTimeMillis();

        int[] genDifficulties = genSolver.getDifficultyMap();

        System.out.println("実行時間: " + (endGen - startGen) + "ms");
        System.out.println(" [ 0 ]: 見たまま (Gen 0)");
        System.out.println(" [ 1 ]: 差分・定石 (Gen 1)");
        System.out.println(" [ 2+]: 連鎖・応用 (Gen 2+)");
        System.out.println(" [ * ]: このソルバーでは解けない (背理法領域)");

        printAnalysis(puzzleBoard, genDifficulties, size, false);

        // ===================================================
        // 解析2: HintCountCalculator (必要ヒント数 k)
        // ===================================================
        System.out.println("\n------------------------------------------------");
        System.out.println("解析2: HintCountCalculator (必要情報量 k)");
        System.out.println("------------------------------------------------");

        HintCountCalculator kCalculator = new HintCountCalculator(puzzleBoard, completeBoard, size);
        long startK = System.currentTimeMillis();
        kCalculator.calculate();
        long endK = System.currentTimeMillis();

        // 結果取得
        int[] kDifficulties = kCalculator.getDifficultyMap();

        System.out.println("実行時間: " + (endK - startK) + "ms");
        System.out.println(" [数字]: 必要ヒント数 (k)");
        System.out.println(" [ * ]: 論理的に確定不可 (運ゲー)");

        printAnalysis(puzzleBoard, kDifficulties, size, true);
    }

    private static void printAnalysis(int[] puzzleBoard, int[] difficulties, int size,
            boolean showAsteriskForUnsolved) {
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