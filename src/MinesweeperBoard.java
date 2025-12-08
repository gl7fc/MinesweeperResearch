import java.util.ArrayList;
import java.util.List;

public class MinesweeperBoard {

    public final int[] cells; // -1 = blank, 0〜8 = hint
    public final int size; // size × size board

    public MinesweeperBoard(int[] cells, int size) {
        this.cells = cells;
        this.size = size;
    }

    /** i が空白セル（-1）か？ */
    public boolean isBlank(int i) {
        return cells[i] == -1;
    }

    /** i がヒントセル（0〜8）か？ */
    public boolean isHint(int i) {
        return cells[i] >= 0;
    }

    /** セル値を取得 */
    public int get(int i) {
        return cells[i];
    }

    /** 盤面外チェック */
    private boolean inBoard(int r, int c) {
        return 0 <= r && r < size && 0 <= c && c < size;
    }

    /** 周囲8マスの 1次元 index を返す */
    public List<Integer> neighbors(int idx) {
        List<Integer> res = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;

                int nr = r + dr;
                int nc = c + dc;

                if (inBoard(nr, nc)) {
                    res.add(nr * size + nc);
                }
            }
        }
        return res;
    }

    /** 空白セル一覧を返す */
    public List<Integer> blankCells() {
        List<Integer> b = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (isBlank(i))
                b.add(i);
        }
        return b;
    }

    /** ヒントセル一覧を返す */
    public List<Integer> hintCells() {
        List<Integer> h = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (isHint(i))
                h.add(i);
        }
        return h;
    }
}
