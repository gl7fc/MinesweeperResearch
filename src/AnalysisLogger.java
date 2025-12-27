import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析の過程を記録し , CSVファイルに出力するクラス
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
        String triggerCells; // ★追加: トリガーセル

        public LogEntry(int round, int step, int cellIdx, String result, int difficultyLevel,
                int regionId, String regionContent, String sourceHints, String triggerCells) {
            this.round = round;
            this.step = step;
            this.cellIdx = cellIdx;
            this.result = result;
            this.difficultyLevel = difficultyLevel;
            this.regionId = regionId;
            this.regionContent = regionContent;
            this.sourceHints = sourceHints;
            this.triggerCells = triggerCells;
        }

        public String toCSVString() {
            // ★修正: NULL値（-1や空文字）を適切に出力
            String stepStr = (step == 0) ? "" : String.valueOf(step);
            String resultStr = result.isEmpty() ? "" : result;
            String levelStr = (difficultyLevel < 0) ? "" : String.valueOf(difficultyLevel);
            String regionIdStr = (regionId < 0) ? "" : String.valueOf(regionId);

            // カンマを含む可能性のあるフィールドはダブルクォートで囲む
            return String.format("%d,%s,%d,%s,%s,%s,\"%s\",\"%s\",\"%s\"",
                    round, stepStr, cellIdx, resultStr, levelStr, regionIdStr,
                    regionContent, sourceHints, triggerCells);
        }
    }

    private List<LogEntry> logs;
    private int stepCounter;

    public AnalysisLogger() {
        this.logs = new ArrayList<>();
        this.stepCounter = 0;
    }

    /**
     * ラウンドが変わるたびにステップカウンターをリセットするかどうかは要件次第ですが ,
     * ここでは「ラウンド内連番」として管理するためにリセットメソッドを用意します.
     */
    public void startNewRound() {
        this.stepCounter = 0;
    }

    /**
     * ★追加: 初期ヒントセルをログに記録する（Round 0, 他はNULL）
     */
    public void logHint(int cellIdx) {
        // Round=0, Step=0, CellIndex=cellIdx, 他はNULL
        logs.add(new LogEntry(0, 0, cellIdx, "", -1, -1, "", "", ""));
    }

    /**
     * 確定ステップをログに記録する（後方互換性のため維持）
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints) {
        logStep(round, cellIdx, result, level, regionId, regionContent, sourceHints, "");
    }

    /**
     * ★追加: 確定ステップをログに記録する（Trigger付き）
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints, String triggerCells) {
        this.stepCounter++;
        logs.add(new LogEntry(round, stepCounter, cellIdx, result, level,
                regionId, regionContent, sourceHints, triggerCells));
    }

    /**
     * 蓄積されたログをCSVファイルに出力する
     */
    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // ヘッダー行（★Trigger列を追加）
            writer.write(
                    "Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints,TriggerCells\n");

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