import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// 制約行列 → DLXで解く
public class DancingLinks {

    // Dancing Links のノード
    // ノード同士のリンクを切ったりつなげたり
    class Node {
        Node L, R, U, D; // すべてのノードには左右上下にリンクするノードが存在
        int Row; // 行番号
        ColumnNode Col; // すべてのノードには対応する列ヘッダが存在

        // コンストラクタ
        // 左右上下, 自分自身にリンクするよう初期化
        public Node() {
            L = R = U = D = this;
        }

        // コンストラクタ
        // ノードと指定の列ヘッダを対応させる
        public Node(ColumnNode c, int r) {
            this();
            Col = c;
            Row = r;
        }

        // ノードn1をノードthisの下にリンク
        Node hookDown(Node n1) {
            // n1がthisと同じ列じゃないときエラー
            assert (this.Col == n1.Col);

            n1.D = this.D;
            n1.D.U = n1;
            n1.U = this;
            this.D = n1;
            return n1;
        }

        // ノードn1をノードthisの右にリンク
        Node hookRight(Node n1) {
            n1.R = this.R;
            n1.R.L = n1;
            n1.L = this;
            this.R = n1;
            return n1;
        }

        // ノードthisの左右のリンクを外す
        void unlinkLR() {
            this.L.R = this.R;
            this.R.L = this.L;
        }

        // thisからリンクを外した左右のノードをthisにリンク
        void relinkLR() {
            this.L.R = this.R.L = this;
        }

        // ノードthisの上下のリンクを外す
        void unlinkUD() {
            this.U.D = this.D;
            this.D.U = this.U;
        }

        // thisからリンクを外した上下のノードをthisにリンク
        void relinkUD() {
            this.U.D = this.D.U = this;
        }
    }

    // 列ヘッダを表すノード
    class ColumnNode extends Node {
        int size; // 列のサイズ
        int covered; // 列にあるノードのうち削除されたノード数
        int constraint; // 列の制約数
        String name; // 列の名前

        // コンストラクタ
        // 列名をつける
        public ColumnNode(String n, int i) {
            super();
            size = 0;
            covered = 0;
            constraint = i;
            name = n;
            Col = this; // 列ヘッダは自分
        }

        // 列jを削除する
        void cover() {
            unlinkLR(); // まず列ヘッダを削除
            // 列にあるノードについて
            for (Node i = this.D; i != this; i = i.D) {
                // "1"のノードがある行を削除する
                for (Node j = i.R; j != i; j = j.R) {
                    j.unlinkUD();
                    j.Col.size--;
                }
            }
            header.size--; // 列ヘッダの数を1減らす
        }

        // 列jを復元する
        // cover() と逆順の処理
        void uncover() {
            for (Node i = this.U; i != this; i = i.U) {
                for (Node j = i.L; j != i; j = j.L) {
                    j.Col.size++;
                    j.relinkUD();
                }
            }
            relinkLR();
            header.size++; // 列ヘッダの数を1増やす
        }

    }

    private ColumnNode header; // 列ヘッダをまとめるノード
    private List<Node> answer; // 解答を保存するリスト
    private int solutionsCount = 0;
    private List<Node> finalAnswer = new ArrayList<>(); // 最終結果を保存するリスト

    // Algorithm X
    // k: 再帰の深さ
    private void knuthsAlgorithmX(int k) {
        // 不要列(制約が0の列, 1が1つも残っていない列)を消す
        for (Node r = header.R; r != header; r = r.R) {
            if (r.Col.constraint == 0) {
                r.Col.cover();
            }
        }

        // 1. 行列が空である場合は問題が解けたので終了
        if (header.R == header) {
            solutionsCount++;
            finalAnswer = new ArrayList<>(answer);
            return;
        } else {
            // 2. その列に含まれる1の数(ノード数)が最も少ない列cを選択・削除
            ColumnNode c = selectColumnNode();

            if (c == null)
                return;

            ColumnNode chosen = c;
            chosen.cover();

            // 3. X[r][c] = 1 であるようなすべての行rについて
            for (Node r = chosen.D; r != chosen; r = r.D) {
                // 3.a. 行rを解の候補として記録
                answer.add(r);

                // 3.b. X[r][j] = 1 であるようなすべての列jについて
                for (Node j = r.R; j != r; j = j.R) {

                    // 3.b.i. 列jのカウンタを1増やす
                    j.Col.covered++;

                    // 3.b.ii. 列jのカウンタが地雷の制約数を満たしている場合,
                    if (j.Col.covered >= j.Col.constraint) {
                        j.Col.cover();
                    }
                }

                // 3.c. 行と列が削除された行列について, このアルゴリズムをもう一度適用
                knuthsAlgorithmX(k + 1);

                // 解答のノードリストから最後のノードを削除, そのノードをrに代入
                r = answer.remove(answer.size() - 1);

                // 最後に選んだ解を次の繰り返しで削除するようcに代入
                c = r.Col;

                // 最後に削除した行から辿れる列を復元
                for (Node j = r.L; j != r; j = j.L) {
                    if (j.Col.covered >= j.Col.constraint) {
                        j.Col.uncover();
                    }
                    j.Col.covered--;
                }
            }
            // バックトラック
            chosen.uncover();
        }
    }

    // 属しているノード数の少ない列を探索
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

    // 引数に取られた制約行列をもとにダンシングリンクを構成する
    // constraint は各列の地雷数の制約を入れた配列
    // 制約行列の1の値を持っている部分のみダンシングリンク化するという疎行列を形成する
    private ColumnNode makeDLXBoard(int[][] grid, int[] constraint) {
        final int COLS = grid[0].length; // ECP行列の列数
        final int ROWS = grid.length; // ECP行列の行数

        // カラムノードの現在リンクが済んでいる最新の部分を
        // "header"という名前を付けてインスタンス化
        ColumnNode headerNode = new ColumnNode("header", 0);
        // カラムノードの集合
        ArrayList<ColumnNode> columnNodes = new ArrayList<ColumnNode>();

        // 全ての列に関してカラムノードをインスタンス化する
        // 名前は何列目かを表す
        for (int i = 0; i < COLS; i++) {
            // i列目のカラムノードをインスタンス化
            ColumnNode n = new ColumnNode(Integer.toString(i), constraint[i]);
            // カラムノードのリストに追加
            columnNodes.add(n);
            // headerとi-1列目の間にi列目のカラムノードをリンクさせる
            // リンクさせたnをheaderノードとする
            headerNode = (ColumnNode) headerNode.hookRight(n);
        }

        // headerノードを最後の列に持ってくる
        headerNode = (ColumnNode) headerNode.R;

        for (int i = 0; i < ROWS; i++) {
            Node prev = null;
            for (int j = 0; j < COLS; j++) {
                // ECP行列のi行j列目の値が1の場合...条件式1
                if (grid[i][j] == 1) {
                    // カラムノードのリストからj列目のノードを取り出しcolに代入
                    ColumnNode col = columnNodes.get(j);
                    // j列目にダンシングノードを作る
                    Node newNode = new Node(col, i);
                    // 条件式1に初めて入る場合
                    if (prev == null) {
                        // newNodeをprevに代入
                        prev = newNode;
                    }

                    // newNode0行j列目に代入
                    // 左右上下とリンクする
                    col.U.hookDown(newNode);
                    prev = prev.hookRight(newNode);
                    // j列目に含まれる1の数をインクリメント
                    col.size++;
                }
            }
        }

        // 最後の列のノードのsizeはCOLS
        headerNode.size = COLS;
        // 最後の列のカラムノードを返す
        return headerNode;
    }

    // コンストラクタ
    public DancingLinks(int[][] grid, int[] constraint) {
        header = makeDLXBoard(grid, constraint);
    }

    // 数独解読の実行
    public void runSolver() {
        // 解答用のリストをインスタンス化
        answer = new LinkedList<Node>();
        // Knuth's Algorithm Xの実行
        knuthsAlgorithmX(0);
    }

    // 解答の行を配列で返す
    public int[] getAnswer() {
        int[] ans = new int[finalAnswer.size()];
        for (int i = 0; i < finalAnswer.size(); i++) {
            ans[i] = finalAnswer.get(i).Row;
        }
        return ans;
    }

    // 解を表示
    public void showAnswer() {
        System.out.println("--- Solved ---");
        for (int i = 0; i < answer.size(); i++) {
            System.out.print(answer.get(i).Row + " ");
        }
        System.out.println("");
        System.out.println("");
    }

    // 解の個数
    public int SolutionsCount(int blanks) {
        return solutionsCount;
    }
}