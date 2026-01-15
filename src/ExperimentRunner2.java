import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 実験をバッチ実行するメインクラス（改良版）
 * 
 * 実験構造:
 * 地雷配置 1〜LAYOUTS → パズル 1〜PUZZLES_PER_LAYOUT
 * 各パズルで HintCountCalculator + TechniqueAnalyzer を実行
 * 
 * 出力構造:
 * results_yyMMdd_HHmmss/
 * ├── summary.csv
 * └── layout_001/
 * ├── Data/
 * │ ├── solution.txt
 * │ ├── puzzle_01.txt
 * │ ├── puzzle_01_heatmap.csv
 * │ ├── puzzle_01_analysis_log.csv
 * │ └── ...
 * └── Puzzle/
 * ├── puzzle_01_board.png
 * ├── puzzle_01_inference_graph.png
 * └── ...
 */
public class ExperimentRunner2 {

    // ===========================================
    // 実験パラメータ
    // ===========================================
    private static final int SIZE = 10;
    private static final int MINE_COUNT = 30;
    private static final int LAYOUTS = 5;
    private static final int PUZZLES_PER_LAYOUT = 2;

    // ===========================================
    // ディレクトリ設定
    // ===========================================
    private static final String OUTPUT_DIR = generateOutputDir();
    private static final String PYTHON_SCRIPT_DIR = "src/scripts/";

    /**
     * 出力ディレクトリ名を生成 (results_yyMMdd_HHmmss/)
     */
    private static String generateOutputDir() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd_HHmmss");
        return "results_" + now.format(formatter) + "/";
    }

    // ===========================================
    // メイン処理
    // ===========================================
    public static void main(String[] args) {
        System.out.println("=== 実験開始 ===");
        System.out.println("盤面サイズ: " + SIZE + "x" + SIZE);
        System.out.println("地雷数: " + MINE_COUNT);
        System.out.println("地雷配置数: " + LAYOUTS);
        System.out.println("パズル数/配置: " + PUZZLES_PER_LAYOUT);
        System.out.println("出力先: " + OUTPUT_DIR);
        System.out.println();

        // 出力ディレクトリ作成
        new File(OUTPUT_DIR).mkdirs();

        // 全結果を格納するリスト
        List<ExperimentResult> allResults = new ArrayList<>();

        // 地雷配置ループ
        for (int layoutId = 1; layoutId <= LAYOUTS; layoutId++) {
            System.out.println("========================================");
            System.out.println("地雷配置 #" + layoutId);
            System.out.println("========================================");

            // 地雷配置を生成
            int[] board = PuzzleGenerator.generatePuzzle(SIZE, MINE_COUNT);

            // ディレクトリ作成
            String layoutDir = OUTPUT_DIR + "layout_" + String.format("%03d", layoutId);
            String dataDir = layoutDir + "/Data";
            String puzzleDir = layoutDir + "/Puzzle";
            new File(dataDir).mkdirs();
            new File(puzzleDir).mkdirs();

            // 正解盤面を保存
            saveBoardToFile(dataDir + "/solution.txt", board, SIZE);

            // パズルループ
            for (int puzzleId = 1; puzzleId <= PUZZLES_PER_LAYOUT; puzzleId++) {
                System.out.print("  Puzzle " + puzzleId + "... ");

                String puzzlePrefix = "puzzle_" + String.format("%02d", puzzleId);

                // パズル最小化
                PuzzleMinimizer pm = new PuzzleMinimizer(board, SIZE);
                int[] puzzle = pm.minimizeHints();
                int hintCount = countHints(puzzle);

                // 問題盤面を保存
                saveBoardToFile(dataDir + "/" + puzzlePrefix + ".txt", puzzle, SIZE);

                // 解析実行 & 結果取得
                ExperimentResult result = runAnalysis(
                        board, puzzle, SIZE,
                        layoutId, puzzleId, hintCount,
                        dataDir, puzzlePrefix);

                allResults.add(result);

                // 可視化生成
                generateVisualizations(
                        dataDir + "/" + puzzlePrefix + "_heatmap.csv",
                        dataDir + "/" + puzzlePrefix + "_analysis_log.csv",
                        puzzleDir,
                        puzzlePrefix);

                System.out.println("ヒント数=" + hintCount + ", Lv_max=" + result.lvMax);
            }
        }

        // サマリーCSV出力
        saveSummaryCSV(OUTPUT_DIR + "summary.csv", allResults);

        System.out.println("\n=== 実験完了 ===");
        System.out.println("総サンプル数: " + allResults.size());
        System.out.println("出力先: " + OUTPUT_DIR);
    }

    // ===========================================
    // 解析実行
    // ===========================================
    private static ExperimentResult runAnalysis(
            int[] board, int[] puzzle, int size,
            int layoutId, int puzzleId, int hintCount,
            String dataDir, String puzzlePrefix) {

        ExperimentResult result = new ExperimentResult();
        result.mineCount = MINE_COUNT;
        result.layoutId = layoutId;
        result.trialId = puzzleId;
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

        // Analysis Log
        analyzer.exportLogToCSV(dataDir + "/" + puzzlePrefix + "_analysis_log.csv");

        // ヒートマップ用データ
        saveHeatmapData(dataDir + "/" + puzzlePrefix + "_heatmap.csv",
                size, board, puzzle, lvMap, kHintMap);

        return result;
    }

    // ===========================================
    // 可視化生成
    // ===========================================
    private static void generateVisualizations(
            String heatmapDataPath,
            String analysisLogPath,
            String puzzleDir,
            String puzzlePrefix) {

        // 盤面画像生成
        String boardOutput = puzzleDir + "/" + puzzlePrefix + "_board.png";
        runPythonScript(PYTHON_SCRIPT_DIR + "board_exporter.py", heatmapDataPath, boardOutput);

        // 推論グラフ生成
        String graphOutput = puzzleDir + "/" + puzzlePrefix + "_graph.png";
        runPythonScript(PYTHON_SCRIPT_DIR + "InferenceGraph.py", analysisLogPath, graphOutput);
    }

    /**
     * Pythonスクリプトを実行
     */
    private static void runPythonScript(String scriptPath, String inputFile, String outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath, inputFile, outputFile);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("  ⚠️  Python script failed: " + scriptPath);
                System.err.println("     " + output.toString());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("  ⚠️  Failed to run Python script: " + scriptPath);
        }
    }

    // ===========================================
    // 統計計算
    // ===========================================
    private static void computeKHintStats(ExperimentResult result, int[] kHintMap, int[] puzzle) {
        int max = 0;
        int sum = 0;
        int count = 0;

        for (int i = 0; i < kHintMap.length; i++) {
            if (puzzle[i] >= 0)
                continue;

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

    private static void computeTechniqueStats(ExperimentResult result, int[] lvMap, int[] puzzle) {
        int max = 0;
        int sum = 0;
        int count = 0;
        int unsolved = 0;

        for (int i = 0; i < lvMap.length; i++) {
            if (puzzle[i] >= 0)
                continue;

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
    // ユーティリティ
    // ===========================================
    private static int countHints(int[] puzzle) {
        int count = 0;
        for (int val : puzzle) {
            if (val >= 0)
                count++;
        }
        return count;
    }

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

    private static void saveHeatmapData(String filename, int size,
            int[] board, int[] puzzle,
            int[] lvMap, int[] kHintMap) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("index,row,col,solution,puzzle,technique_level,k_hint");
            for (int i = 0; i < size * size; i++) {
                int row = i / size;
                int col = i % size;
                writer.printf("%d,%d,%d,%d,%d,%d,%d%n",
                        i, row, col, board[i], puzzle[i], lvMap[i], kHintMap[i]);
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save heatmap data: " + e.getMessage());
        }
    }

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
}