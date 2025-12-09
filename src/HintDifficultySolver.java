import java.util.*;

public class HintDifficultySolver {

    int[] board;
    int size;
    int H; // ヒントセル総数
    boolean[] solved; // solved[i] = i番目セルは推論済み
    int[] needHints; // needHints[i] = 必要ヒント数
    List<Integer> blanks = new ArrayList<>(); // 空白セルのインデックス
    List<Integer> hints = new ArrayList<>(); // ヒントセルのインデックス

    public HintDifficultySolver(int[] board, int size) {
        this.board = board;
        this.size = size;

        for (int i = 0; i < board.length; i++) {
            if (board[i] == -1)
                blanks.add(i);
            else
                hints.add(i);
        }

        solved = new boolean[size * size];
        needHints = new int[size * size];
        Arrays.fill(needHints, -1);

        H = hints.size();
    }

    // メイン処理
    // ヒント集合のすべての部分集合についてその部分集合から推論できるセルを特定
    public void solve() {
        // 部分集合のサイズ k を 1 から H まで増やす
        for (int k = 1; k <= 1; k++) {
            List<List<Integer>> subsets = enumerateSubsetsOfSizeK(hints, k); // 大きさkの部分集合を列挙
            System.out.println(subsets);

            for (List<Integer> subset : subsets) {
                // --- 部分集合から ECP 行列を構築 --------------------------
                int[] copyOfBoard = Arrays.copyOf(board, board.length);
                int[][] grid; // 制約行列
                int[] constraint; // 制約配列

                // subset にないヒントを無効化
                for (int h : hints) {
                    if (!subset.contains(h)) {
                        copyOfBoard[h] = -2;
                    }
                }

                // subset に含まれるヒントの周囲セル以外の空白セルを無効化
                Set<Integer> neighborBlanks = new HashSet<>();
                // subset に含まれるヒントの周囲セルの空白セルを集める
                for (int h : subset) {
                    List<Integer> neighbors = getNeighbors(h); // 盤面サイズに応じて隣接セルを取得
                    for (int nb : neighbors) {
                        if (board[nb] == -1) { // 空白セルのみ
                            neighborBlanks.add(nb);
                        }
                    }
                }

                // 隣接していない空白セルを無効化
                for (int b : blanks) {
                    if (!neighborBlanks.contains(b)) {
                        copyOfBoard[b] = -2; // 無効化
                    }
                }

                // --- 現在有効なセルのみからDLXで推論 --------------------
                ConstraintBuilder builder = new ConstraintBuilder(copyOfBoard, size);

                // builder.enableOnlyHints(subset);
                builder.buildConstraints();

                ConstraintBuilder.Data data = builder.buildConstraints();

                grid = data.matrix;
                constraint = data.constraint;

                // --- 表示 --------------------
                // System.out.println("=== 制約行列 ===");
                // builder.printMatrixWithLabels(data.matrix);

                // System.out.println("=== 制約配列 ===");
                // for (int v : data.constraint)
                // System.out.print(v + " ");
                // System.out.println("");

                // --- DLXを実行して推論可能セルを取り出す --------------------
                DancingLinks dlx = new DancingLinks(grid, constraint);
                // List<Integer> deduced = dlx.solveAndGetDeducedCells();
                // System.out.println(deduced);

                dlx.runSolver();

                // --- DLXの解答をセル番号に変換, --------------------
                int[] solvedRows = dlx.getAnswer();
                Set<Integer> deduced = new HashSet<>();
                for (int r : solvedRows) {
                    int cellIdx = r / 2; // ConstraintBuilder の行が空白セル2行分なので /2 でインデックスに変換
                    if (board[cellIdx] == -1)
                        deduced.add(cellIdx); // 推論済みにする
                }

                System.out.println(deduced);

                // --- 推論できるセルを必要ヒント数 k でマーク ----------------
                boolean updated = false;
                for (int c : deduced) {
                    if (!solved[c]) {
                        solved[c] = true;
                        needHints[c] = k;
                        updated = true;
                    }
                }

                // 全セル推論済みなら終了
                if (countUnsolved() == 0) {
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 下請け関数
    // -------------------------------------------------------------

    // まだ推論済みでない空白セルを数える
    int countUnsolved() {
        int count = 0;
        for (boolean b : solved) // solved配列を巡回
            if (!b)
                count++;
        return count;
    }

    // ヒント集合からサイズ k の部分集合を列挙（辞書順）
    public static <T> List<List<T>> enumerateSubsetsOfSizeK(List<T> list, int k) {
        List<List<T>> res = new ArrayList<>();
        backtrack(list, k, 0, new ArrayList<>(), res);
        return res;
    }

    private static <T> void backtrack(List<T> list, int k, int start,
            List<T> cur, List<List<T>> res) {
        if (cur.size() == k) {
            res.add(new ArrayList<>(cur));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            cur.add(list.get(i));
            backtrack(list, k, i + 1, cur, res);
            cur.remove(cur.size() - 1);
        }
    }

    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    // -------------------------------------------------------------
    // 結果取得
    // -------------------------------------------------------------
    public int[] getHintRequired() {
        return needHints;
    }
}