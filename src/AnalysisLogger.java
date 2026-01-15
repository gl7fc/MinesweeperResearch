import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析の過程を記録し , CSVファイルに出力するクラス
 * 
 * ★機能:
 * - TriggerCells: このセルを確定させた直接の原因となったRegionのトリガーセル
 * - ParentSnapshot: 確定元Regionの親スナップショット
 * - GenerationDepth: 確定元Regionの派生深度
 * - Height: グラフ可視化用の高さ（Lv1は横移動、Lv2-6は縦移動）
 */
public class AnalysisLogger {

    private static class LogEntry {
        int round;
        int step;
        int cellIdx;
        String result;
        int difficultyLevel;
        int regionId;
        String regionContent;
        String sourceHints;
        String triggerCells;
        String parentSnapshot;
        int generationDepth;
        int height;

        public LogEntry(int round, int step, int cellIdx, String result, int difficultyLevel,
                int regionId, String regionContent, String sourceHints,
                String triggerCells, String parentSnapshot, int generationDepth, int height) {
            this.round = round;
            this.step = step;
            this.cellIdx = cellIdx;
            this.result = result;
            this.difficultyLevel = difficultyLevel;
            this.regionId = regionId;
            this.regionContent = regionContent;
            this.sourceHints = sourceHints;
            this.triggerCells = triggerCells;
            this.parentSnapshot = parentSnapshot;
            this.generationDepth = generationDepth;
            this.height = height;
        }

        public String toCSVString() {
            return String.format("%d,%d,%d,%s,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",%d,%d",
                    round, step, cellIdx, result, difficultyLevel, regionId,
                    regionContent, sourceHints, triggerCells, parentSnapshot, generationDepth, height);
        }
    }

    private List<LogEntry> logs;
    private int stepCounter;

    public AnalysisLogger() {
        this.logs = new ArrayList<>();
        this.stepCounter = 0;
    }

    public void startNewRound() {
        this.stepCounter = 0;
    }

    /**
     * 初期ヒントをログに記録する（Round 0, Depth 0, Height 0）
     */
    public void logInitialHint(int cellIdx, int hintValue) {
        this.stepCounter++;
        logs.add(new LogEntry(
                0, // Round
                stepCounter, // Step
                cellIdx, // CellIndex
                "HINT(" + hintValue + ")", // Result
                0, // DifficultyLevel
                -1, // RegionID
                "", // RegionContent
                "", // SourceHints
                "", // TriggerCells
                "", // ParentSnapshot
                0, // GenerationDepth
                0 // Height
        ));
    }

    /**
     * 確定ステップをログに記録する（Height付き）
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints,
            String triggerCells, String parentSnapshot, int generationDepth, int height) {
        this.stepCounter++;
        logs.add(new LogEntry(round, stepCounter, cellIdx, result, level,
                regionId, regionContent, sourceHints,
                triggerCells, parentSnapshot, generationDepth, height));
    }

    /**
     * 蓄積されたログをCSVファイルに出力する
     */
    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(
                    "Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints,TriggerCells,ParentSnapshot,GenerationDepth,Height\n");

            for (LogEntry entry : logs) {
                writer.write(entry.toCSVString() + "\n");
            }

            System.out.println("✅ Analysis log exported to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to export CSV: " + e.getMessage());
        }
    }
}