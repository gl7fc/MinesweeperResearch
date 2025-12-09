import java.util.*;

// ヒント集合の最小化
public class PuzzleMinimizer {
    int[] board;
    private int n;
    private Map<Integer, Integer> answerState; // <セルインデックス, 状態(0=Safe, 1=Mine)>
    // 1. 正解のリストを作成(DLXで得られる解の形式に合わせる)
    // 2. ヒント数字を隠すたびに正解のリストを更新, ソート
    // 3. DLXで得られた解のリストを作成,ソート -> finalAnswerListで良さそう
    // 4. 正解のリストとDLXで得られた解のリストを比較

    // コンストラクタ
    public PuzzleMinimizer(int[] board, int n) {
        this.board = Arrays.copyOf(board, n * n);
        this.n = n;
    }

    // 正解の状態を作成する
    private void makeAnswerState() {
        answerState = new HashMap<>();
        for (int i = 0; i < n * n; i++) {
            if (board[i] == -1) {
                answerState.put(i, 1); // 地雷
            }
            // ヒントセルは状態マップに含めない（推論対象外）
        }
    }

    public int[] minimizeHints() {
        int size = n * n;
        List<Integer> hintCells = new ArrayList<>(); // ヒントセルを入れる

        // 正解の状態を作成
        makeAnswerState();

        // ヒントセルを収集
        for (int i = 0; i < size; i++) {
            if (board[i] != -1) {
                hintCells.add(i);
            }
        }

        // シャッフル
        Collections.shuffle(hintCells, new Random());

        System.out.println("=== ヒント最小化開始 ===");
        System.out.println("初期ヒント数: " + hintCells.size());

        int removed = 0;
        // 順番にヒントを隠す
        for (int cell : hintCells) {
            int backup = board[cell];
            board[cell] = -1;

            // 正解の状態を更新（隠したセルが安全セルになる）
            answerState.put(cell, 0);

            // 唯一解を推論できるか確認
            if (!isUniqueSolvable(board, n)) {
                // できなければ隠したヒントを戻す
                board[cell] = backup;
                answerState.remove(cell); // ヒントは状態マップから削除
            } else {
                removed++;
                System.out.println("セル(" + (cell / n) + "," + (cell % n) + ") のヒント " + backup + " を削除 [残りヒント数: "
                        + (hintCells.size() - removed) + "]");
            }
        }

        System.out.println("=== ヒント最小化完了 ===");
        System.out.println("削除したヒント数: " + removed);
        System.out.println("最終ヒント数: " + (hintCells.size() - removed));

        return board;
    }

    // 解が一意に得られるか確認
    private boolean isUniqueSolvable(int[] board, int n) {
        try {
            ConstraintBuilder builder = new ConstraintBuilder(board, n);
            ConstraintBuilder.Data data = builder.buildConstraints();

            int[][] matrix = data.matrix;
            int[] constraint = data.constraint;

            // 制約行列が空の場合
            if (matrix.length == 0 || matrix[0].length == 0) {
                return false;
            }

            // 空白セルのリストを取得
            // ConstraintBuilderが作成した順序で取得
            List<Integer> blanks = new ArrayList<>();
            for (int i = 0; i < board.length; i++) {
                if (board[i] == -1) {
                    blanks.add(i);
                }
            }

            DancingLinks dlx = new DancingLinks(matrix, constraint);
            dlx.setBlanks(blanks); // 空白セルリストを設定
            dlx.runSolver();

            // 解の数を確認
            int solutionCount = dlx.getSolutionCount();
            if (solutionCount != 1) {
                return false; // 解が0個または2個以上
            }

            // 解の状態を取得
            Map<Integer, Integer> solvedState = dlx.getDeducedState();

            // 正解の状態と一致するか確認
            return stateMatches(solvedState);

        } catch (Exception e) {
            System.err.println("エラー発生 in isUniqueSolvable: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // 解の状態が正解と一致するか確認
    private boolean stateMatches(Map<Integer, Integer> solvedState) {
        // すべての空白セル（元の盤面で地雷だったセル）について確認
        for (Map.Entry<Integer, Integer> entry : answerState.entrySet()) {
            int cell = entry.getKey();
            int expectedState = entry.getValue();

            // 解に含まれていない場合は不一致
            if (!solvedState.containsKey(cell)) {
                return false;
            }

            // 状態が一致しない場合は不一致
            int actualState = solvedState.get(cell);
            if (actualState != expectedState) {
                return false;
            }
        }

        // すべてのセルで状態が一致
        return true;
    }

    // 最小化された盤面を取得
    public int[] getMinimizedBoard() {
        return board;
    }

    // ヒント数をカウント
    public int countHints() {
        int count = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] != -1) {
                count++;
            }
        }
        return count;
    }

    // 盤面を表示
    public void printBoard() {
        System.out.println("\n=== 最小化された盤面 ===");
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int idx = r * n + c;
                int val = board[idx];
                if (val == -1) {
                    System.out.print(" . ");
                } else {
                    System.out.print(" " + val + " ");
                }
            }
            System.out.println();
        }
        System.out.println("ヒント数: " + countHints());
    }
}