import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DancingLinks {

    class Node {
        Node L, R, U, D;
        int Row;
        ColumnNode Col;

        public Node() {
            L = R = U = D = this;
        }

        public Node(ColumnNode c, int r) {
            this();
            Col = c;
            Row = r;
        }

        Node hookDown(Node n1) {
            assert (this.Col == n1.Col);
            n1.D = this.D;
            n1.D.U = n1;
            n1.U = this;
            this.D = n1;
            return n1;
        }

        Node hookRight(Node n1) {
            n1.R = this.R;
            n1.R.L = n1;
            n1.L = this;
            this.R = n1;
            return n1;
        }

        void unlinkLR() {
            this.L.R = this.R;
            this.R.L = this.L;
        }

        void relinkLR() {
            this.L.R = this.R.L = this;
        }

        void unlinkUD() {
            this.U.D = this.D;
            this.D.U = this.U;
        }

        void relinkUD() {
            this.U.D = this.D.U = this;
        }
    }

    class ColumnNode extends Node {
        int size;
        int covered;
        int constraint;
        String name;

        public ColumnNode(String n, int i) {
            super();
            size = 0;
            covered = 0;
            constraint = i;
            name = n;
            Col = this;
        }

        void cover() {
            unlinkLR();
            for (Node i = this.D; i != this; i = i.D) {
                for (Node j = i.R; j != i; j = j.R) {
                    j.unlinkUD();
                    j.Col.size--;
                }
            }
            header.size--;
        }

        void uncover() {
            for (Node i = this.U; i != this; i = i.U) {
                for (Node j = i.L; j != i; j = j.L) {
                    j.Col.size++;
                    j.relinkUD();
                }
            }
            relinkLR();
            header.size++;
        }
    }

    private ColumnNode header;
    private List<Node> answer;
    private List<List<Node>> allSolutions = new ArrayList<>();
    private List<Integer> blanks; // 空白セルのリスト（相対→絶対変換用）

    public DancingLinks(int[][] grid, int[] constraint) {
        header = makeDLXBoard(grid, constraint);
    }

    // 空白セルリストを設定（セルインデックス変換用）
    public void setBlanks(List<Integer> blanks) {
        this.blanks = new ArrayList<>(blanks);
    }

    // Algorithm X (全解列挙版)
    private void knuthsAlgorithmX(int k) {
        // 不要列を消す
        for (Node r = header.R; r != header; r = r.R) {
            if (r.Col.constraint == 0) {
                r.Col.cover();
            }
        }

        // 1. 行列が空である場合は問題が解けたので終了
        if (header.R == header) {
            allSolutions.add(new ArrayList<>(answer)); // 解を保存
            return; // continue searching by returning
        }

        ColumnNode c = selectColumnNode();
        if (c == null)
            return;

        c.cover();

        for (Node r = c.D; r != c; r = r.D) {
            answer.add(r);

            for (Node j = r.R; j != r; j = j.R) {
                j.Col.covered++;
                if (j.Col.covered >= j.Col.constraint) {
                    j.Col.cover();
                }
            }

            knuthsAlgorithmX(k + 1);

            r = answer.remove(answer.size() - 1);
            c = r.Col;

            for (Node j = r.L; j != r; j = j.L) {
                j.Col.uncover();
                j.Col.covered--;
            }
        }

        c.uncover();
    }

    private ColumnNode selectColumnNode() {
        int min = Integer.MAX_VALUE;
        ColumnNode ret = null;

        for (ColumnNode c = (ColumnNode) header.R; c != header; c = (ColumnNode) c.R) {
            if (c.size < min && c.size != 0) {
                min = c.size;
                ret = c;
            }
        }
        return ret;
    }

    private ColumnNode makeDLXBoard(int[][] grid, int[] constraint) {
        final int COLS = grid[0].length;
        final int ROWS = grid.length;

        ColumnNode headerNode = new ColumnNode("header", 0);
        ArrayList<ColumnNode> columnNodes = new ArrayList<ColumnNode>();

        for (int i = 0; i < COLS; i++) {
            ColumnNode n = new ColumnNode(Integer.toString(i), constraint[i]);
            columnNodes.add(n);
            headerNode = (ColumnNode) headerNode.hookRight(n);
        }

        headerNode = (ColumnNode) headerNode.R;

        for (int i = 0; i < ROWS; i++) {
            Node prev = null;
            for (int j = 0; j < COLS; j++) {
                if (grid[i][j] == 1) {
                    ColumnNode col = columnNodes.get(j);
                    Node newNode = new Node(col, i);
                    if (prev == null) {
                        prev = newNode;
                    }
                    col.U.hookDown(newNode);
                    prev = prev.hookRight(newNode);
                    col.size++;
                }
            }
        }

        headerNode.size = COLS;
        return headerNode;
    }

    public void runSolver() {
        answer = new LinkedList<Node>();
        allSolutions.clear();
        knuthsAlgorithmX(0);
    }

    /**
     * 全解で状態（Safe/Mine）まで一致するセルのみを返す
     * 
     * @return Map<絶対セルインデックス, 状態(0=Safe, 1=Mine)>
     */
    public Map<Integer, Integer> getDeducedState() {
        if (allSolutions.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 全解で共通して選択されている「行ID」を抽出
        Set<Integer> commonRows = new HashSet<>();
        for (Node n : allSolutions.get(0)) {
            commonRows.add(n.Row);
        }

        // 2. 他のすべての解と共通集合をとる
        for (int i = 1; i < allSolutions.size(); i++) {
            Set<Integer> currentRows = new HashSet<>();
            for (Node n : allSolutions.get(i)) {
                currentRows.add(n.Row);
            }
            commonRows.retainAll(currentRows);
        }

        // 3. 共通行を <絶対セルIndex, 値> のマップに変換
        Map<Integer, Integer> deduced = new HashMap<>();
        for (int row : commonRows) {
            // 相対インデックス（blanks内の位置）
            int relativeIdx = row / 2;
            // 状態: 偶数行=Safe(0), 奇数行=Mine(1)
            int state = row % 2;

            // 絶対インデックスに変換
            if (blanks != null && relativeIdx >= 0 && relativeIdx < blanks.size()) {
                int absoluteIdx = blanks.get(relativeIdx);
                deduced.put(absoluteIdx, state);
            }
        }

        return deduced;
    }

    // 解の数を返す
    public int getSolutionCount() {
        return allSolutions.size();
    }

    // 最初の解の行を配列で返す (既存の互換性のため)
    public int[] getAnswer() {
        if (allSolutions.isEmpty()) {
            return new int[0];
        }
        List<Node> firstSolution = allSolutions.get(0);
        int[] ans = new int[firstSolution.size()];
        for (int i = 0; i < firstSolution.size(); i++) {
            ans[i] = firstSolution.get(i).Row;
        }
        return ans;
    }

    public void showAnswer() {
        System.out.println("--- Found " + allSolutions.size() + " solution(s) ---");
        for (int s = 0; s < Math.min(allSolutions.size(), 5); s++) {
            System.out.print("Solution " + (s + 1) + ": ");
            for (Node n : allSolutions.get(s)) {
                System.out.print(n.Row + " ");
            }
            System.out.println();
        }
        if (allSolutions.size() > 5) {
            System.out.println("... and " + (allSolutions.size() - 5) + " more solutions");
        }
    }
}