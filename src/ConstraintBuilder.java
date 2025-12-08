import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConstraintBuilder {
    // 制約行列と制約配列を返す用のクラス
    public class Data {
        public int[][] matrix;
        public int[] constraint;

        // コンストラクタ
        Data(int[][] matrix, int[] constraint) {
            this.matrix = matrix;
            this.constraint = constraint;
        }
    }

    // ConstraintBuilder のフィールド
    private int[] board; // 盤面
    private int size; // 盤面サイズ
    private List<Integer> blanks; // 空白セルリスト
    private List<Integer> hintCells; // ヒントセルリスト

    // コンストラクタ
    public ConstraintBuilder(int[] board, int size) {
        this.board = board;
        this.size = size;
        this.blanks = new ArrayList<>();
        this.hintCells = new ArrayList<>();
        findCells(); // 空白セルとヒントセルを分類
    }

    // 空白セルとヒントセルを探索してリスト化
    private void findCells() {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == -1) {
                blanks.add(i); // 空白セル
            } else if (board[i] == -2) { // 無視するセル
                // 何もしない
            } else {
                hintCells.add(i); // ヒントセル
            }
        }
    }

    // 周囲8セルの番号を返す, 盤面外は除外
    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    // 制約行列・制約配列を生成
    public Data buildConstraints() {
        // findCells(); // 空白セルとヒントセルを分類

        int blankCount = blanks.size(); // 空白セルの個数
        int hintCount = hintCells.size(); // ヒントセルの個数

        int totalRows = blankCount * 2; // 制約行列の行数
        int totalCols = blankCount + hintCount * 2; // 制約行列の列数

        int[][] matrix = new int[totalRows][totalCols];
        int[] constraint = new int[totalCols];

        // セル制約部分
        for (int i = 0; i < blankCount; i++) {
            matrix[i * 2][i] = 1; // #0
            matrix[i * 2 + 1][i] = 1; // #1
            constraint[i] = 1; // 制約配列は常に1
        }

        // 地雷数制約部分
        for (int i = 0; i < hintCount; i++) {
            int hintIdx = hintCells.get(i); // ヒントのセル番号
            int hintValue = board[hintIdx]; // ヒントの値
            int col = blankCount + i * 2;
            int bCount = 0;

            // 周囲8セルの番号を取得
            List<Integer> neighbors = getNeighbors(hintIdx);
            for (int nb : neighbors) {
                // 周囲8セルのうち空白のセルについて処理
                if (board[nb] == -1) {
                    int idx = blanks.indexOf(nb);
                    if (idx != -1) {
                        matrix[idx * 2][col] = 1;
                        matrix[idx * 2 + 1][col + 1] = 1;
                        bCount++; // 空白セルの数をカウント
                    }
                }
            }

            constraint[col] = bCount - hintValue;
            constraint[col + 1] = hintValue;
        }

        return new Data(matrix, constraint);

    }

    // (表示用) 列ラベルを生成
    private String[] getColumnLabels() {
        List<String> labels = new ArrayList<>();
        for (int idx : blanks)
            labels.add(String.valueOf(idx));
        for (int i = 0; i < hintCells.size(); i++) {
            int idx = hintCells.get(i);
            int hintValue = board[idx];
            labels.add(idx + "#0(" + hintValue + ")");
            labels.add(idx + "#1(" + hintValue + ")");
        }
        return labels.toArray(new String[0]);
    }

    // (表示用) 行ラベルを生成
    public String[] getRowLabels() {
        List<String> labels = new ArrayList<>();
        for (int idx : blanks) {
            labels.add(idx + "#0");
            labels.add(idx + "#1");
        }
        return labels.toArray(new String[0]);
    }

    // 制約行列の表示
    public void printMatrixWithLabels(int[][] matrix) {
        String[] cols = getColumnLabels();
        String[] rows = getRowLabels();

        // 列ラベル出力
        System.out.print("        ");
        for (String c : cols)
            System.out.printf("%-14s", c);
        System.out.println();

        // 行ごとに出力
        for (int i = 0; i < matrix.length; i++) {
            System.out.printf("%-8s", rows[i]);
            for (int j = 0; j < matrix[0].length; j++) {
                System.out.printf("%-14d", matrix[i][j]);
            }
            System.out.println();
        }
    }

    // CSV出力
    public void exportToCSV(String filename, int[][] matrix, int[] constraint) {
        String[] cols = getColumnLabels();
        String[] rows = getRowLabels();

        try (FileWriter writer = new FileWriter(filename)) {
            // 1行目: 列ラベル
            writer.append(" ,");
            for (int j = 0; j < cols.length; j++) {
                writer.append(cols[j]);
                if (j < cols.length - 1)
                    writer.append(",");
            }
            writer.append("\n");

            // 各行の出力
            for (int i = 0; i < matrix.length; i++) {
                writer.append(rows[i]).append(",");
                for (int j = 0; j < matrix[i].length; j++) {
                    writer.append(String.valueOf(matrix[i][j]));
                    if (j < matrix[i].length - 1)
                        writer.append(",");
                }
                writer.append("\n");
            }

            // 制約配列を最下行に出力
            writer.append("constraint,");
            for (int j = 0; j < constraint.length; j++) {
                writer.append(String.valueOf(constraint[j]));
                if (j < constraint.length - 1)
                    writer.append(",");
            }
            writer.append("\n");

            System.out.println("✅ CSV出力完了: " + filename);
        } catch (IOException e) {
            System.err.println("❌ CSV出力中にエラーが発生しました: " + e.getMessage());
        }
    }

    public void enableOnlyHints(List<Integer> subset) {
        Set<Integer> subsetSet = new HashSet<>(subset);
        // hintCells は ConstraintBuilder 内にあるヒントセルリスト
        for (int hintIdx : hintCells) {
            if (!subsetSet.contains(hintIdx)) {
                // 無効化する方法の例: ヒント値を -1 にする
                board[hintIdx] = -2; // -2 で「無効ヒント」を表す
            }
        }
    }
}