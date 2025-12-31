import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 盤面データをCSV形式で出力するユーティリティクラス
 * Python側でヒートマップを生成するためのデータ出力用
 */
public class BoardExporter {

    /**
     * 4つの盤面データをCSVファイルに出力する
     * 
     * @param filename          出力ファイル名
     * @param size              盤面サイズ (n x n)
     * @param completeBoard     正解盤面 (地雷=-1, それ以外=数字)
     * @param puzzleBoard       問題盤面 (未確定=-1, ヒント=数字)
     * @param techniqueLevels   Technique Level解析結果 (-1=未解決, 0=初期ヒント, 1-6=レベル)
     * @param kHintDifficulties k-Hint解析結果 (-1=未解決, 0=初期ヒント, 1-8=必要ヒント数)
     */
    public static void exportToCSV(
            String filename,
            int size,
            int[] completeBoard,
            int[] puzzleBoard,
            int[] techniqueLevels,
            int[] kHintDifficulties) {

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // ヘッダー行: メタデータ
            writer.println("# Minesweeper Analysis Data");
            writer.println("# size=" + size);
            writer.println();

            // セクション1: 正解盤面 (Complete Board)
            writer.println("[CompleteBoard]");
            writeBoard(writer, completeBoard, size);
            writer.println();

            // セクション2: 問題盤面 (Puzzle Board)
            writer.println("[PuzzleBoard]");
            writeBoard(writer, puzzleBoard, size);
            writer.println();

            // セクション3: Technique Level
            writer.println("[TechniqueLevel]");
            writeBoard(writer, techniqueLevels, size);
            writer.println();

            // セクション4: k-Hint Difficulty
            writer.println("[kHintDifficulty]");
            writeBoard(writer, kHintDifficulties, size);

            System.out.println("✅ Board data exported to: " + filename);

        } catch (IOException e) {
            System.err.println("❌ Failed to export board data: " + e.getMessage());
        }
    }

    /**
     * 1つの盤面をCSV形式で出力
     */
    private static void writeBoard(PrintWriter writer, int[] board, int size) {
        for (int row = 0; row < size; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < size; col++) {
                if (col > 0)
                    sb.append(",");
                sb.append(board[row * size + col]);
            }
            writer.println(sb.toString());
        }
    }

    /**
     * 簡易版: 単一の盤面をCSV出力
     */
    public static void exportSingleBoard(String filename, int[] board, int size, String label) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# " + label);
            writer.println("# size=" + size);
            writeBoard(writer, board, size);
            System.out.println("✅ " + label + " exported to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Failed to export: " + e.getMessage());
        }
    }
}