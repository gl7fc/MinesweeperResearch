import java.util.*;

// ヒント集合の最小化
public class PuzzleMinimizer {
    int[] board;
    private int n;
    private List<String> anslist;

    // 1. 正解のリストを作成(DLXで得られる解の形式に合わせる)
    // 2. ヒント数字を隠すたびに正解のリストを更新, ソート
    // 3. DLXで得られた解のリストを作成,ソート -> finalAnswerListで良さそう
    // 4. 正解のリストとDLXで得られた解のリストを比較

    // コンストラクタ
    public PuzzleMinimizer(int[] board, int n) {
        this.board = Arrays.copyOf(board, n * n);
        this.n = n;
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
        List<Integer> safeCells = new ArrayList<>(); // 地雷のないセルを入れる

        // 正解のリストを作成
        makeAnslist();

        // 地雷の無いセルを収集
        for (int i = 0; i < size; i++) {
            if (board[i] != -1)
                safeCells.add(i);
        }

        // シャッフル
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
                // できなければ隠したヒントを戻す, 正解のリストに追加したものを削除
                board[cell] = backup;
                anslist.remove(cell + "#0");
            }
        }

        return board;
    }

    // 解が一意に得られるか確認
    private boolean isUniqueSolvable(int[] board, int n) {
        ConstraintBuilder builder = new ConstraintBuilder(board, n); // 制約行列, 制約配列を作成
        ConstraintBuilder.Data data = builder.buildConstraints();

        int[][] matrix = data.matrix;
        int[] constraint = data.constraint;
        int blanks = data.blanks;

        // 制約行列をCSV出力(デバッグ用)
        // builder.exportToCSV("const.csv", matrix, constraint);

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