import java.io.*;
import java.util.*;

/**
 * 実験をバッチ実行するメインクラス
 * 
 * 実験構造:
 * 地雷数 M → 地雷配置 1〜N → 最小化試行 1〜T
 * 各試行で HintCountCalculator + TechniqueAnalyzer を実行
 */
public class ExperimentRunner {

    // ===========================================
    // 実験パラメータ（小規模テスト用）
    // ===========================================
    private static final int SIZE = 10; // 盤面サイズ
    private static final int[] MINE_COUNTS = { 30 }; // 地雷数（本番: {15, 20, 25, 30}）
    private static final int LAYOUTS_PER_MINE = 1; // 地雷配置数（本番: 100）
    private static final int TRIALS_PER_LAYOUT = 10; // 試行数（本番: 20）

    // 出力ディレクトリ
    private static final String OUTPUT_DIR = "experiment_results_02/";

    // ===========================================
    // メイン処理
    // ===========================================
    public static void main(String[] args) {
        System.out.println("=== 実験開始 ===");
        System.out.println("盤面サイズ: " + SIZE + "x" + SIZE);
        System.out.println("地雷数: " + Arrays.toString(MINE_COUNTS));
        System.out.println("配置数/地雷数: " + LAYOUTS_PER_MINE);
        System.out.println("試行数/配置: " + TRIALS_PER_LAYOUT);
        System.out.println();

        // 出力ディレクトリ作成
        createDirectories();

        // 全結果を格納するリスト
        List<ExperimentResult> allResults = new ArrayList<>();

        // 地雷数ループ
        for (int mineCount : MINE_COUNTS) {
            System.out.println("========================================");
            System.out.println("地雷数: " + mineCount);
            System.out.println("========================================");

            String mineDir = OUTPUT_DIR + "/mines_" + mineCount;
            new File(mineDir).mkdirs();

            // 地雷配置ループ
            for (int layoutId = 1; layoutId <= LAYOUTS_PER_MINE; layoutId++) {
                System.out.println("\n--- 地雷配置 #" + layoutId + " ---");

                // 地雷配置を生成（この配置を固定して複数回最小化）
                int[] board = PuzzleGenerator.generatePuzzle(SIZE, mineCount);

                String layoutDir = mineDir + "/layout_" + String.format("%03d", layoutId);
                new File(layoutDir).mkdirs();

                // 正解盤面を保存
                saveBoardToFile(layoutDir + "/solution.txt", board, SIZE);

                // 試行ループ
                for (int trialId = 1; trialId <= TRIALS_PER_LAYOUT; trialId++) {
                    System.out.print("  Trial " + trialId + "... ");

                    String trialDir = layoutDir + "/trial_" + String.format("%02d", trialId);
                    new File(trialDir).mkdirs();

                    // パズル最小化
                    PuzzleMinimizer pm = new PuzzleMinimizer(board, SIZE);
                    int[] puzzle = pm.minimizeHints();
                    int hintCount = pm.getHintCount();

                    // 問題盤面を保存
                    saveBoardToFile(trialDir + "/puzzle.txt", puzzle, SIZE);

                    // 解析実行 & 結果取得
                    ExperimentResult result = runAnalysis(
                            board, puzzle, SIZE,
                            mineCount, layoutId, trialId, hintCount,
                            trialDir);

                    allResults.add(result);

                    System.out.println("ヒント数=" + hintCount + ", Lv_max=" + result.lvMax);
                }
            }
        }

        // サマリーCSV出力
        saveSummaryCSV(OUTPUT_DIR + "/summary.csv", allResults);

        System.out.println("\n=== 実験完了 ===");
        System.out.println("総サンプル数: " + allResults.size());
        System.out.println("出力先: " + OUTPUT_DIR);
    }

    // ===========================================
    // 解析実行
    // ===========================================
    private static ExperimentResult runAnalysis(
            int[] board, int[] puzzle, int size,
            int mineCount, int layoutId, int trialId, int hintCount,
            String outputDir) {

        ExperimentResult result = new ExperimentResult();
        result.mineCount = mineCount;
        result.layoutId = layoutId;
        result.trialId = trialId;
        result.hintCount = hintCount;

        // -----------------------------------------
        // HintCountCalculator 解析
        // -----------------------------------------
        HintCountCalculator calculator = new HintCountCalculator(puzzle, board, size);
        calculator.calculate();
        int[] kHintMap = calculator.getDifficultyMap();

        // k-Hint統計を計算
        computeKHintStats(result, kHintMap, puzzle);

        // -----------------------------------------
        // TechniqueAnalyzer 解析
        // -----------------------------------------
        TechniqueAnalyzer analyzer = new TechniqueAnalyzer(puzzle, board, size);
        analyzer.analyze();
        int[] lvMap = analyzer.getDifficultyMap();

        // Technique統計を計算
        computeTechniqueStats(result, lvMap, puzzle);

        // -----------------------------------------
        // ファイル出力
        // -----------------------------------------

        // Analysis Log (TechniqueAnalyzerのログ)
        analyzer.exportLogToCSV(outputDir + "/analysis_log.csv");

        // ヒートマップ用データ
        saveHeatmapData(outputDir + "/heatmap_data.csv",
                size, board, puzzle, lvMap, kHintMap);

        return result;
    }

    // ===========================================
    // 統計計算
    // ===========================================

    /**
     * k-Hint統計を計算
     */
    private static void computeKHintStats(ExperimentResult result, int[] kHintMap, int[] puzzle) {
        int max = 0;
        int sum = 0;
        int count = 0;

        for (int i = 0; i < kHintMap.length; i++) {
            if (puzzle[i] >= 0)
                continue; // 初期ヒントはスキップ

            int k = kHintMap[i];
            if (k >= 1 && k <= 8) {
                result.kCounts[k - 1]++;
                max = Math.max(max, k);
                sum += k;
                count++;
            }
        }

        result.kMax = max;
        result.kAvg = (count > 0) ? (double) sum / count : 0;
    }

    /**
     * Technique統計を計算
     */
    private static void computeTechniqueStats(ExperimentResult result, int[] lvMap, int[] puzzle) {
        int max = 0;
        int sum = 0;
        int count = 0;
        int unsolved = 0;

        for (int i = 0; i < lvMap.length; i++) {
            if (puzzle[i] >= 0)
                continue; // 初期ヒントはスキップ

            int lv = lvMap[i];
            if (lv >= 1 && lv <= 6) {
                result.lvCounts[lv - 1]++;
                max = Math.max(max, lv);
                sum += lv;
                count++;
            } else if (lv == -1) {
                unsolved++;
            }
        }

        result.lvMax = max;
        result.lvAvg = (count > 0) ? (double) sum / count : 0;
        result.unsolvedCount = unsolved;
    }

    // ===========================================
    // ファイル出力
    // ===========================================

    /**
     * サマリーCSVを出力
     */
    private static void saveSummaryCSV(String filename, List<ExperimentResult> results) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println(ExperimentResult.getCsvHeader());
            for (ExperimentResult r : results) {
                writer.println(r.toCsvRow());
            }
            System.out.println("✅ Summary CSV saved: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to save summary CSV: " + e.getMessage());
        }
    }

    /**
     * ヒートマップ用データをCSV出力
     */
    private static void saveHeatmapData(String filename, int size,
            int[] board, int[] puzzle,
            int[] lvMap, int[] kHintMap) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // ヘッダー
            writer.println("index,row,col,solution,puzzle,technique_level,k_hint");

            // データ
            for (int i = 0; i < size * size; i++) {
                int row = i / size;
                int col = i % size;
                writer.printf("%d,%d,%d,%d,%d,%d,%d%n",
                        i, row, col,
                        board[i], // 正解盤面
                        puzzle[i], // 問題盤面
                        lvMap[i], // Technique Level
                        kHintMap[i] // k-Hint
                );
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save heatmap data: " + e.getMessage());
        }
    }

    /**
     * 盤面をテキストファイルに保存
     */
    private static void saveBoardToFile(String filename, int[] board, int size) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (int i = 0; i < board.length; i++) {
                writer.print(board[i]);
                if ((i + 1) % size == 0) {
                    writer.println();
                } else {
                    writer.print(",");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save board: " + e.getMessage());
        }
    }

    /**
     * 出力ディレクトリを作成
     */
    private static void createDirectories() {
        new File(OUTPUT_DIR).mkdirs();
    }
}