import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 盤面の1次元配列 → 制約行列・制約配列
public class ConstraintBuilder {

    // 定数定義 (HintCountCalculatorと合わせる)
    private static final int MINE = -1; // 未確定 (変数候補)
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定 (定数)

    // 制約行列と制約配列を返す用のクラス
    public class Data {
        public int[][] matrix;
        public int[] constraint;
        public int blanks;

        // コンストラクタ
        Data(int[][] matrix, int[] constraint, int blanks) {
            this.matrix = matrix;
            this.constraint = constraint;
            this.blanks = blanks;
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
            int val = board[i];

            // IGNORE(-2) と FLAGGED(-3) はリストに入れない
            // FLAGGEDは周囲チェック時に参照される
            if (val == IGNORE || val == FLAGGED) {
                continue;
            }

            if (val == MINE) {
                blanks.add(i); // 変数
            } else if (val >= 0) {
                hintCells.add(i); // ヒント
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
        int blankCount = blanks.size();
        int hintCount = hintCells.size();

        int totalRows = blankCount * 2;
        int totalCols = blankCount + hintCount * 2;

        if (totalRows == 0)
            totalRows = 1;
        if (totalCols == 0)
            totalCols = 1;

        int[][] matrix = new int[totalRows][totalCols];
        int[] constraint = new int[totalCols];

        if (blankCount > 0) {
            // 変数制約 (各空白は0か1のどちらか)
            for (int i = 0; i < blankCount; i++) {
                matrix[i * 2][i] = 1;
                matrix[i * 2 + 1][i] = 1;
                constraint[i] = 1;
            }

            // ヒント制約
            for (int i = 0; i < hintCount; i++) {
                int hintIdx = hintCells.get(i);
                int effectiveHintValue = board[hintIdx]; // ヒントの値

                int col = blankCount + i * 2;
                int variableCount = 0; // 周囲の変数(空白)数

                // 周囲8セルの番号を取得
                List<Integer> neighbors = getNeighbors(hintIdx);
                for (int nb : neighbors) {
                    int neighborVal = board[nb];

                    if (neighborVal == MINE) {
                        // 変数リストにあるか確認
                        int idx = blanks.indexOf(nb);
                        if (idx != -1) {
                            matrix[idx * 2][col] = 1; // 安全ならこっち
                            matrix[idx * 2 + 1][col + 1] = 1; // 地雷ならこっち
                            variableCount++;
                        }
                    }
                    // ★重要: 地雷確定セルがある場合、ヒント値を減らす
                    else if (neighborVal == FLAGGED) {
                        effectiveHintValue--;
                    }
                    // IGNOREは何もしない
                }

                if (effectiveHintValue < 0)
                    effectiveHintValue = 0; // 念のため

                // col: 安全であるべき数 = (変数の総数) - (必要な地雷数)
                // col+1: 地雷であるべき数 = (必要な地雷数)
                constraint[col] = variableCount - effectiveHintValue;
                constraint[col + 1] = effectiveHintValue;
            }
        }

        return new Data(matrix, constraint, blankCount);

    }

    // --- 表示用メソッド (既存のまま) ---
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
            if (i < rows.length)
                System.out.printf("%-8s", rows[i]);
            else
                System.out.printf("%-8s", "ROW" + i);
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
            writer.append(System.lineSeparator());

            System.out.println("✅ CSV出力完了: " + filename);
        } catch (IOException e) {
            System.err.println("❌ CSV出力中にエラーが発生しました: " + e.getMessage());
        }
    }

}