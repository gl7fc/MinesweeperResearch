import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盤面上の「ある範囲」と「その中の地雷数」を表現する不変 (Immutable) クラス。
 * 数字ヒントだけでなく、推論によって得られた仮想的なヒントも表現する。
 */
public class Region {
    private final Set<Integer> cells;
    private final int mines;

    /**
     * コンストラクタ
     * @param cells この領域に含まれる未確定セルのインデックス集合
     * @param mines この領域内に存在する地雷の数
     */
    public Region(Set<Integer> cells, int mines) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
    }

    public Set<Integer> getCells() {
        return cells;
    }

    public int getMines() {
        return mines;
    }

    public int size() {
        return cells.size();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /**
     * 指定されたRegionが自分の部分集合かどうか判定する
     * (this ⊇ other)
     */
    public boolean containsAll(Region other) {
        return this.cells.containsAll(other.cells);
    }

    /**
     * 差分領域（自分 - 相手）を新しい Region として返す
     * 前提: other は this の部分集合であること
     */
    public Region subtract(Region other) {
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);
        int newMines = this.mines - other.mines;
        return new Region(newCells, newMines);
    }

    /**
     * 指定されたセルを取り除いた新しい Region を返す (盤面更新時に使用)
     * @param cellToRemove 削除するセル
     * @param isMine 削除するセルが地雷だった場合は true (minesを減らす)
     */
    public Region removeCell(int cellToRemove, boolean isMine) {
        if (!cells.contains(cellToRemove)) {
            return this;
        }
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.remove(cellToRemove);
        int newMines = isMine ? this.mines - 1 : this.mines;
        
        // 地雷数が負になるような矛盾時は0に補正（あるいは例外でも良いが安全側に倒す）
        if (newMines < 0) newMines = 0;
        
        return new Region(newCells, newMines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Region region = (Region) o;

        if (mines != region.mines) return false;
        return cells.equals(region.cells);
    }

    @Override
    public int hashCode() {
        int result = cells.hashCode();
        result = 31 * result + mines;
        return result;
    }

    @Override
    public String toString() {
        String cellStr = cells.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "{" + cellStr + "}=" + mines;
    }
}