import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析の過程を記録し , CSVファイルに出力するクラス
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

    public void startNewRound() {
        this.stepCounter = 0;
    }

    public void logInitialHint(int cellIdx, int hintValue) {
        this.stepCounter++;
        logs.add(new LogEntry(0, stepCounter, cellIdx, "HINT(" + hintValue + ")",
                0, -1, "", "", "", "", 0));
    }

    public void logStep(int round, int cellIdx, String result, int level,
            int regionId, String regionContent, String sourceHints,
            String triggerCells, String parentSnapshot, int generationDepth) {
        this.stepCounter++;
        logs.add(new LogEntry(round, stepCounter, cellIdx, result, level,
                regionId, regionContent, sourceHints,
                triggerCells, parentSnapshot, generationDepth));
    }

    public void exportToCSV(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(
                    "Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints,TriggerCells,ParentSnapshot,GenerationDepth\n");
            for (LogEntry entry : logs) {
                writer.write(entry.toCSVString() + "\n");
            }
            System.out.println("✅ Analysis log exported to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to export CSV: " + e.getMessage());
        }
    }
}