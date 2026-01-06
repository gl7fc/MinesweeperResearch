import java.util.*;

/**
 * ヒント集合の最小化
 * 同じ地雷配置から複数の異なる最小ヒント集合を生成可能
 */
public class PuzzleMinimizer {
    private int[] board;
    private final int[] originalBoard; // リセット用に元の盤面を保持
    private int n;
    private List<String> anslist;

    // コンストラクタ
    public PuzzleMinimizer(int[] board, int n) {
        this.originalBoard = Arrays.copyOf(board, n * n);
        this.board = Arrays.copyOf(board, n * n);
        this.n = n;
    }

    /**
     * 複数の異なる最小ヒント集合を生成する
     * 
     * @param count       生成したいパズル数
     * @param maxAttempts 最大試行回数
     * @return 生成されたパズルのリスト
     */
    public List<int[]> generateMultipleUniquePuzzles(int count, int maxAttempts) {
        List<int[]> results = new ArrayList<>();
        Set<Set<Integer>> generatedHintSets = new HashSet<>();

        int attempts = 0;
        while (results.size() < count && attempts < maxAttempts) {
            attempts++;

            // 盤面をリセット
            resetBoard();

            // 新しいパズルを生成
            int[] puzzle = minimizeHints();

            // ヒント集合を抽出
            Set<Integer> hintSet = extractHintSet(puzzle);

            // 重複チェック
            if (!generatedHintSets.contains(hintSet)) {
                generatedHintSets.add(hintSet);
                results.add(Arrays.copyOf(puzzle, puzzle.length));
                System.out.println("Generated unique puzzle " + results.size() + "/" + count
                        + " (hints: " + hintSet.size() + ")");
            } else {
                System.out.println("Duplicate detected, retrying... (attempt " + attempts + ")");
            }
        }

        if (results.size() < count) {
            System.out.println("Warning: Could only generate " + results.size()
                    + " unique puzzles after " + maxAttempts + " attempts");
        }

        return results;
    }

    /**
     * 複数の異なる最小ヒント集合を生成する（デフォルト試行回数）
     * 
     * @param count 生成したいパズル数
     * @return 生成されたパズルのリスト
     */
    public List<int[]> generateMultipleUniquePuzzles(int count) {
        // デフォルトで count * 10 回まで試行
        return generateMultipleUniquePuzzles(count, count * 10);
    }

    /**
     * 盤面を初期状態にリセット
     */
    public void resetBoard() {
        this.board = Arrays.copyOf(originalBoard, originalBoard.length);
        this.anslist = null;
    }

    /**
     * パズルからヒント集合（開示されているセルのインデックス）を抽出
     */
    private Set<Integer> extractHintSet(int[] puzzle) {
        Set<Integer> hints = new HashSet<>();
        for (int i = 0; i < puzzle.length; i++) {
            if (puzzle[i] >= 0) {
                hints.add(i);
            }
        }
        return hints;
    }

    /**
     * ヒント数を取得（static版）
     */
    public static int countHints(int[] puzzle) {
        int count = 0;
        for (int val : puzzle) {
            if (val >= 0)
                count++;
        }
        return count;
    }

    /**
     * 現在の盤面のヒント数を取得
     */
    public int getHintCount() {
        int count = 0;
        for (int val : board) {
            if (val >= 0)
                count++;
        }
        return count;
    }

    // 正解のリストを作成する
    private void makeAnslist() {
        anslist = new ArrayList<String>();
        for (int i = 0; i < n * n; i++) {
            if (board[i] == -1)
                anslist.add(i + "#1");
        }
    }

    public int[] minimizeHints() {
        int size = n * n;
        List<Integer> safeCells = new ArrayList<>();

        // 正解のリストを作成
        makeAnslist();

        // 地雷の無いセルを収集
        for (int i = 0; i < size; i++) {
            if (board[i] != -1)
                safeCells.add(i);
        }

        // シャッフル（毎回異なる順序で試行）
        Collections.shuffle(safeCells, new Random());

        // 順番にヒントを隠す
        for (int cell : safeCells) {
            int backup = board[cell];
            board[cell] = -1;

            // 正解のリストに隠したセルが地雷でないことを追加・ソート
            anslist.add(cell + "#0");
            Collections.sort(anslist);

            // 唯一解を推論できるか確認
            if (!isUniqueSolvable(board, n)) {
                // できなければ隠したヒントを戻す、正解のリストに追加したものを削除
                board[cell] = backup;
                anslist.remove(cell + "#0");
            }
        }

        return board;
    }

    // 解が一意に得られるか確認
    private boolean isUniqueSolvable(int[] board, int n) {
        ConstraintBuilder builder = new ConstraintBuilder(board, n);
        ConstraintBuilder.Data data = builder.buildConstraints();

        int[][] matrix = data.matrix;
        int[] constraint = data.constraint;
        int blanks = data.blanks;

        DancingLinks dlx = new DancingLinks(matrix, constraint);
        dlx.runSolver();

        int[] ans = dlx.getAnswer();

        // 解の出力・リスト化
        List<String> finalAnslist = new ArrayList<>();

        String[] rowLabels = builder.getRowLabels();
        for (int i = 0; i < ans.length; i++) {
            finalAnslist.add(rowLabels[ans[i]]);
        }

        Collections.sort(finalAnslist);

        int ansEqualBlanks = dlx.SolutionsCount(blanks);
        boolean ansEqualfans = listEqual(anslist, finalAnslist);

        if (ansEqualfans && ansEqualBlanks == 1) {
            return true;
        }
        return false;
    }

    private boolean listEqual(List<String> a, List<String> b) {
        if (a.size() == b.size()) {
            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).equals(b.get(i)))
                    return false;
            }
            return true;
        }
        return false;
    }
}