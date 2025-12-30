import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析の過程を記録し , CSVファイルに出力するクラス
 * 
 * ★追加機能: 親子関係追跡情報の出力
 * - TriggerCells: このセルを確定させた直接の原因となったRegionのトリガーセル
 * - ParentSnapshot: 確定元Regionの親スナップショット
 * - GenerationDepth: 確定元Regionの派生深度
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
        // ★追加フィールド
        String triggerCells; // このセル確定の元となったRegion更新のトリガー
        String parentSnapshot; // 確定元Regionの親スナップショット
        int generationDepth; // 確定元Regionの派生深度

        public LogEntry(int round, int step, int cellIdx, String result, int difficultyLevel,
                int regionId, String regionContent, String sourceHints,
                String triggerCells, String parentSnapshot, int generationDepth) {
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
        }

        public String toCSVString() {
            // カンマを含む可能性のあるフィールドはダブルクォートで囲む
            return String.format("%d,%d,%d,%s,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",%d",
                    round, step, cellIdx, result, difficultyLevel, regionId,
                    regionContent, sourceHints, triggerCells, parentSnapshot, generationDepth);
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
     * 確定ステップをログに記録する（従来版、後方互換）
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints) {
        // 従来のメソッドは新しいメソッドに委譲（親子関係は空）
        logStep(round, cellIdx, result, level, regionId, regionContent, sourceHints, "", "", 0);
    }

    /**
     * ★追加: 親子関係情報付きの確定ステップをログに記録する
     */
    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints,
            String triggerCells, String parentSnapshot, int generationDepth) {
        this.stepCounter++;
        logs.add(new LogEntry(round, stepCounter, cellIdx, result, level,
                regionId, regionContent, sourceHints,
                triggerCells, parentSnapshot, generationDepth));
    }

    /**
     * 蓄積されたログをCSVファイルに出力する
     */
    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // ★ヘッダー行（拡張版）
            writer.write(
                    "Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints,TriggerCells,ParentSnapshot,GenerationDepth\n");

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