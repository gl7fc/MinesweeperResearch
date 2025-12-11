import java.util.Arrays;
import java.util.Map;

/**
 * Lv.1 テクニック解析テスト用のメインクラス
 * ファイル名: Main_v2.java
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

        System.out.println(puzzleBoard);

        System.out.println("===== 生成された盤面 (問題) =====");
        PuzzleGenerator.printBoard(puzzleBoard, size);

        // ===================================================
        // 解析2: HintCountCalculator (必要ヒント数 k)
        // ===================================================
        // System.out.println("\n--- HintCount(k) 難易度解析実行中... ---");

        // puzzle: 問題, board: 正解, size: サイズ
        HintCountCalculator kCalculator = new HintCountCalculator(puzzleBoard, completeBoard, size);

        // 計算実行
        kCalculator.calculate();

        // 結果取得
        int[] kDifficulties = kCalculator.getDifficultyMap();

        // ===================================================
        // 結果表示
        // ===================================================
        System.out.println("\n===== 解析結果比較 =====");

        // 2. HintCount(k) の結果
        System.out.println("\n[HintCount(k) 必要ヒント数]");
        System.out.println(" [数字]: そのセルを解くのに必要だったヒント数(k)");
        System.out.println(" [ . ]: 元から表示されていたヒント");
        System.out.println(" [ * ]: 論理的に確定できなかったセル\n");

        printKAnalysis(puzzleBoard, kDifficulties, size);
    }

    private static void printKAnalysis(int[] puzzleBoard, int[] difficulties, int size) {
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