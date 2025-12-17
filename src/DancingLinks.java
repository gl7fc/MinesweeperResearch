import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// 制約行列 → DLXで解く
public class DancingLinks {

    // ... (既存の内部クラス Node, ColumnNode はそのまま) ...
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
    private int solutionsCount = 0;
    private List<Node> finalAnswer = new ArrayList<>();

    // Algorithm X
    private void knuthsAlgorithmX(int k) {
        // ★修正: ここにあった制約0の列を削除するループを削除
        // 再帰のたびにリストを操作するのは危険であり , 初期化時に一度行えば十分なため

        if (header.R == header) {
            solutionsCount++;
            finalAnswer = new ArrayList<>(answer);
            return;
        } else {
            // 制約が1の列（変数決定用の列）の中から , 最もノード数が少ない列cを選択
            ColumnNode c = selectColumnNode();

            // 選べる列がない場合（制約1の列は全て満たされた）
            if (c == null) {
                // すべての列（制約>1の列含む）が消えているか確認
                if (header.R == header) {
                    solutionsCount++;
                    finalAnswer = new ArrayList<>(answer);
                }
                // 制約>1の列が残っている場合は , 制約を満たせなかったのでバックトラック
                return;
            }

            ColumnNode chosen = c;
            chosen.cover();

            for (Node r = chosen.D; r != chosen; r = r.D) {
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
                    if (j.Col.covered >= j.Col.constraint) {
                        j.Col.uncover();
                    }
                    j.Col.covered--;
                }
            }
            chosen.uncover();
        }
    }

    // 制約が1の列(Primary Column)のみを選択対象にする
    private ColumnNode selectColumnNode() {
        int min = Integer.MAX_VALUE;
        ColumnNode ret = null;

        for (ColumnNode c = (ColumnNode) header.R; c != header; c = (ColumnNode) c.R) {
            if (c.constraint == 1 && c.size < min) {
                min = c.size;
                ret = c;
            }
        }
        return ret;
    }

    // ... (makeDLXBoard はそのまま) ...
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
                    if (prev == null)
                        prev = newNode;
                    col.U.hookDown(newNode);
                    prev = prev.hookRight(newNode);
                    col.size++;
                }
            }
        }
        headerNode.size = COLS;
        return headerNode;
    }

    public DancingLinks(int[][] grid, int[] constraint) {
        header = makeDLXBoard(grid, constraint);
    }

    public void runSolver() {
        answer = new LinkedList<Node>();

        // ★修正: 制約0の列（最初から満たされている列）を探索開始前にすべてcoverする
        // リスト操作によるConcurrentModificationExceptionやリンク破壊を防ぐため , 一度リストに集める
        List<ColumnNode> zeroConstraintCols = new ArrayList<>();
        for (Node c = header.R; c != header; c = c.R) {
            ColumnNode col = (ColumnNode) c;
            if (col.constraint == 0) {
                zeroConstraintCols.add(col);
            }
        }
        // 集めた列をcover
        for (ColumnNode c : zeroConstraintCols) {
            c.cover();
        }

        knuthsAlgorithmX(0);

        // 必要であればここでuncoverして元に戻すが , 今回はインスタンス使い捨てなので不要
    }

    public int[] getAnswer() {
        int[] ans = new int[finalAnswer.size()];
        for (int i = 0; i < finalAnswer.size(); i++) {
            ans[i] = finalAnswer.get(i).Row;
        }
        return ans;
    }

    public void showAnswer() {
        System.out.println("--- Solved ---");
        for (int i = 0; i < answer.size(); i++) {
            System.out.print(answer.get(i).Row + " ");
        }
        System.out.println("");
        System.out.println("");
    }

    public int SolutionsCount(int blanks) {
        return solutionsCount;
    }
}