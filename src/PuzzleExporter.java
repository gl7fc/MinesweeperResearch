import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * パズルの盤面をテキストファイルに出力するクラス
 */
public class PuzzleExporter {

    /**
     * 正解盤面とヒント最小化済盤面をテキストファイルに出力する
     * 
     * @param filename 出力ファイル名
     * @param board    正解盤面（地雷=-1、それ以外=周囲の地雷数）
     * @param puzzle   ヒント最小化済盤面（ヒント=数値、未公開=-1）
     * @param size     盤面の1辺のサイズ
     */
    public static void exportPuzzle(String filename, int[] board, int[] puzzle, int size) {
        // 地雷数をカウント
        int mineCount = 0;
        for (int val : board) {
            if (val == -1) {
                mineCount++;
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // ヘッダー情報
            writer.println("// Size: " + size + "x" + size);
            writer.println("// Mines: " + mineCount);
            writer.println();

            // 正解盤面
            writer.println("int[] board = {");
            writeArray(writer, board, size);
            writer.println("};");
            writer.println();

            // ヒント最小化済盤面
            writer.println("int[] puzzle = {");
            writeArray(writer, puzzle, size);
            writer.println("};");

            System.out.println("Puzzle exported to: " + filename);

        } catch (IOException e) {
            System.err.println("Failed to export puzzle: " + e.getMessage());
        }
    }

    /**
     * 配列を整形して出力する
     */
    private static void writeArray(PrintWriter writer, int[] array, int size) {
        for (int i = 0; i < array.length; i++) {
            // 行の先頭
            if (i % size == 0) {
                writer.print("    ");
            }

            // 値を出力（-1は右寄せ、それ以外は2桁分スペース確保）
            writer.printf("%2d", array[i]);

            // カンマと区切り
            if (i < array.length - 1) {
                writer.print(", ");
            }

            // 行の終わり
            if ((i + 1) % size == 0) {
                writer.println();
            }
        }
    }

    /**
     * 正解盤面のみを出力する（デバッグ用）
     */
    public static void exportBoard(String filename, int[] board, int size) {
        int mineCount = 0;
        for (int val : board) {
            if (val == -1) {
                mineCount++;
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("// Size: " + size + "x" + size);
            writer.println("// Mines: " + mineCount);
            writer.println();

            writer.println("int[] board = {");
            writeArray(writer, board, size);
            writer.println("};");

            System.out.println("Board exported to: " + filename);

        } catch (IOException e) {
            System.err.println("Failed to export board: " + e.getMessage());
        }
    }
}