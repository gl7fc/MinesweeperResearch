import java.util.*;

public class HintDifficultySolver {

    int[] board;
    int size;
    int H; // ヒントセル総数
    boolean[] solved; // solved[i] = i番目セルは推論済み
    int[] needHints; // needHints[i] = 必要ヒント数
    List<Integer> blanks = new ArrayList<>(); // 空白セルのインデックス
    List<Integer> hints = new ArrayList<>(); // ヒントセルのインデックス

    // どのヒント集合からどのセルが推論されたかを記録
    Map<Integer, List<Integer>> cellToHints = new HashMap<>();

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

    /** メイン処理 */
    public void solve() {
        // 部分集合のサイズ k を 1 から H まで増やす
        for (int k = 1; k <= 1; k++) {
            System.out.println("\n=== k=" + k + " のヒント部分集合を試行中 ===");
            List<List<Integer>> subsets = enumerateSubsetsOfSizeK(hints, k);
            System.out.println("部分集合の数: " + subsets.size());

            int subsetCount = 0;
            for (List<Integer> subset : subsets) {
                subsetCount++;
                System.out.println(subset + "について");
                // --- 部分集合から ECP 行列を構築 --------------------------
                int[] copyOfBoard = Arrays.copyOf(board, board.length);

                // subset にないヒントを無効化
                for (int h : hints) {
                    if (!subset.contains(h)) {
                        copyOfBoard[h] = -2;
                    }
                }

                // subset に含まれるヒントの周囲セル以外の空白セルを無効化
                Set<Integer> neighborBlanks = new HashSet<>();
                for (int h : subset) {
                    List<Integer> neighbors = getNeighbors(h);
                    for (int nb : neighbors) {
                        if (board[nb] == -1) {
                            neighborBlanks.add(nb);
                        }
                    }
                }

                // 隣接していない空白セルを無効化
                for (int b : blanks) {
                    if (!neighborBlanks.contains(b)) {
                        copyOfBoard[b] = -2;
                    }
                }

                try {
                    ConstraintBuilder builder = new ConstraintBuilder(copyOfBoard, size);
                    ConstraintBuilder.Data data = builder.buildConstraints();

                    // 制約行列が空の場合はスキップ
                    if (data.matrix.length == 0 || data.matrix[0].length == 0) {
                        continue;
                    }

                    System.out.println("=== 制約行列 ===");
                    builder.printMatrixWithLabels(data.matrix);

                    System.out.println("=== 制約配列 ===");
                    for (int v : data.constraint)
                        System.out.print(v + " ");
                    System.out.println("");

                    // --- DLXを実行して推論可能セルを取り出す --------------------
                    DancingLinks dlx = new DancingLinks(data.matrix, data.constraint);

                    // // 有効な空白セルのリストを取得（-2でないもの）
                    // List<Integer> activeBlanks = new ArrayList<>();
                    // for (int b : blanks) {
                    // if (copyOfBoard[b] == -1) {
                    // activeBlanks.add(b);
                    // }
                    // }
                    // // dlx.setBlanks(activeBlanks); // DLXに空白セルリストを渡す

                    dlx.runSolver();
                    int[] ans = dlx.getAnswer();

                    System.out.println("=== 解答 ===");
                    String[] rowLabels = builder.getRowLabels();
                    for (int i = 0; i < ans.length; i++) {
                        System.out.print(rowLabels[ans[i]] + " ");
                    }
                    System.out.println("");

                    // 全解で共通するセルのみを推論可能とする
                    Set<Integer> deduced = dlx.getDeducedCells();

                    int solutionCount = dlx.getSolutionCount();

                    // --- 推論できるセルを必要ヒント数 k でマーク ----------------
                    for (int c : deduced) {
                        if (!solved[c]) {
                            solved[c] = true;
                            needHints[c] = k;
                            cellToHints.put(c, new ArrayList<>(subset));

                            // ヒント位置を座標形式で表示
                            System.out.print("  セル(" + (c / size) + "," + (c % size) + ") を推論 <- ");
                            System.out.print("ヒント[");
                            for (int i = 0; i < subset.size(); i++) {
                                int h = subset.get(i);
                                System.out.print("(" + (h / size) + "," + (h % size) + ")=" + board[h]);
                                if (i < subset.size() - 1)
                                    System.out.print(", ");
                            }
                            System.out.println("]  (解の数: " + solutionCount + ")");
                        }
                    }

                } catch (Exception e) {
                    // DLXでエラーが発生した場合はスキップ
                    System.err.println("  エラー発生: " + e.getMessage());
                    e.printStackTrace();
                    continue;
                }
            }

            // 全セル推論済みなら終了
            if (countUnsolved() == 0) {
                System.out.println("\n✅ 全セル推論完了!");
                return;
            }

            System.out.println("未推論セル数: " + countUnsolved());
        }

        System.out.println("\n⚠️ 全ヒントを使っても推論できないセルが残っています。");
    }

    // -------------------------------------------------------------
    // 下請け関数
    // -------------------------------------------------------------

    // まだ推論済みでない空白セルを数える
    int countUnsolved() {
        int count = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == -1 && !solved[i]) {
                count++;
            }
        }
        return count;
    }

    /** ヒント集合からサイズ k の部分集合を列挙（辞書順） */
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

    // デバッグ用: 盤面を視覚的に表示
    public void printBoard() {
        System.out.println("\n=== 盤面 ===");
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int idx = r * size + c;
                int val = board[idx];
                if (val == -1) {
                    System.out.print(" . ");
                } else {
                    System.out.print(" " + val + " ");
                }
            }
            System.out.println();
        }

        System.out.println("\n=== 必要ヒント数 ===");
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int idx = r * size + c;
                if (board[idx] == -1) {
                    int need = needHints[idx];
                    if (need == -1) {
                        System.out.print(" ? ");
                    } else {
                        System.out.print(" " + need + " ");
                    }
                } else {
                    System.out.print(" " + board[idx] + " ");
                }
            }
            System.out.println();
        }

        System.out.println("\n=== 推論の詳細 ===");
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int idx = r * size + c;
                if (board[idx] == -1 && cellToHints.containsKey(idx)) {
                    System.out.print("セル(" + r + "," + c + ") <- ");
                    List<Integer> hintList = cellToHints.get(idx);
                    System.out.print("[");
                    for (int i = 0; i < hintList.size(); i++) {
                        int h = hintList.get(i);
                        System.out.print("(" + (h / size) + "," + (h % size) + ")=" + board[h]);
                        if (i < hintList.size() - 1)
                            System.out.print(", ");
                    }
                    System.out.println("]");
                }
            }
        }
    }
}