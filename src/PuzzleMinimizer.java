import java.util.*;

public class PuzzleMinimizer {
    int[] board;
    private int n;
    private List<String> anslist;
    private int hintCount; // 最小化後のヒント数

    public PuzzleMinimizer(int[] board, int n) {
        this.board = Arrays.copyOf(board, n * n);
        this.n = n;
        this.hintCount = 0;
    }

    // ヒント数を取得
    public int getHintCount() {
        return hintCount;
    }

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

        makeAnslist();

        for (int i = 0; i < size; i++) {
            if (board[i] != -1)
                safeCells.add(i);
        }

        Collections.shuffle(safeCells, new Random());

        for (int cell : safeCells) {
            int backup = board[cell];
            board[cell] = -1;

            anslist.add(cell + "#0");
            Collections.sort(anslist);

            if (!isUniqueSolvable(board, n)) {
                board[cell] = backup;
                anslist.remove(cell + "#0");
            }
        }

        // ヒント数をカウントしてフィールドに保存
        hintCount = 0;
        for (int i = 0; i < size; i++) {
            if (board[i] >= 0) {
                hintCount++;
            }
        }

        return board;
    }

    private boolean isUniqueSolvable(int[] board, int n) {
        ConstraintBuilder builder = new ConstraintBuilder(board, n);
        ConstraintBuilder.Data data = builder.buildConstraints();

        int[][] matrix = data.matrix;
        int[] constraint = data.constraint;
        int blanks = data.blanks;

        DancingLinks dlx = new DancingLinks(matrix, constraint);
        dlx.runSolver();

        int[] ans = dlx.getAnswer();

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