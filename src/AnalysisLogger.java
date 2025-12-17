import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析の過程を記録し、CSVファイルに出力するクラス
 */
public class AnalysisLogger {

    // ログ1行分のデータを保持する内部クラス
    private static class LogEntry {
        int round;
        int step;
        int cellIdx;
        String result; // "SAFE" or "MINE"
        int difficultyLevel;
        int regionId;
        String regionContent;
        String sourceHints;

        public LogEntry(int round, int step, int cellIdx, String result, int difficultyLevel,
                int regionId, String regionContent, String sourceHints) {
            this.round = round;
            this.step = step;
            this.cellIdx = cellIdx;
            this.result = result;
            this.difficultyLevel = difficultyLevel;
            this.regionId = regionId;
            this.regionContent = regionContent;
            this.sourceHints = sourceHints;
        }

        public String toCSVString() {
            // カンマを含む可能性のあるフィールドはダブルクォートで囲む
            return String.format("%d,%d,%d,%s,%d,%d,\"%s\",\"%s\"",
                    round, step, cellIdx, result, difficultyLevel, regionId, regionContent, sourceHints);
        }
    }

    private List<LogEntry> logs;
    private int stepCounter;

    public AnalysisLogger() {
        this.logs = new ArrayList<>();
        this.stepCounter = 0;
    }

    /**
     * ラウンドが変わるたびにステップカウンターをリセットするかどうかは要件次第ですが、
     * ここでは「ラウンド内連番」として管理するためにリセットメソッドを用意します。
     */
    public void startNewRound() {
        this.stepCounter = 0;
    }

    /**
     * 確定ステップをログに記録する
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints) {
        this.stepCounter++;
        logs.add(new LogEntry(round, stepCounter, cellIdx, result, level,
                regionId, regionContent, sourceHints));
    }

    /**
     * 蓄積されたログをCSVファイルに出力する
     */
    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // ヘッダー行
            writer.write("Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints\n");

            // データ行
            for (LogEntry entry : logs) {
                writer.write(entry.toCSVString() + "\n");
            }

            System.out.println("✅ Analysis log exported to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to export CSV: " + e.getMessage());
        }
    }
}