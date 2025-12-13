import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盤面上のヒントや推論によって得られた「領域」を表すクラス。
 * 不変(Immutable)オブジェクトとして扱う。
 */
public class Region {
    private final Set<Integer> cells; // この領域に含まれる未確定セルのインデックス集合
    private final int mines; // この領域内に存在する地雷の数
    private final int originLevel; // この情報が生成されたテクニックのレベル

    /**
     * コンストラクタ
     * 
     * @param cells       未確定セルの集合
     * @param mines       地雷数
     * @param originLevel 生成レベル (0=盤面ヒント, 3=Lv1-3推論など)
     */
    public Region(Set<Integer> cells, int mines, int originLevel) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
        this.originLevel = originLevel;
    }

    public Set<Integer> getCells() {
        return cells;
    }

    public int getMines() {
        return mines;
    }

    public int getOriginLevel() {
        return originLevel;
    }

    /**
     * 自分が相手の部分集合かどうか判定する
     * (A isSubsetOf B means A ⊆ B)
     */
    public boolean isSubsetOf(Region other) {
        return other.cells.containsAll(this.cells);
    }

    /**
     * 差分領域（自分 - 相手）を新しい Region として返す
     * D = this - other
     * ※ Lv1-2/3で使用される
     */
    public Region subtract(Region other) {
        // cells = this.cells - other.cells
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);

        // mines = this.mines - other.mines
        int newMines = this.mines - other.mines;

        // level = max(this.level, other.level, 3)
        int newLevel = Math.max(this.originLevel, other.originLevel);
        newLevel = Math.max(newLevel, 3); // Lv1-3以上として扱う

        return new Region(newCells, newMines, newLevel);
    }

    @Override
    public String toString() {
        // デバッグ用表示: "{10, 11, 12}(3) = 1"
        String cellStr = cells.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "{", "}"));
        return String.format("%s(Lv%d) = %d", cellStr, originLevel, mines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Region region = (Region) o;

        if (mines != region.mines)
            return false;
        return cells.equals(region.cells);
    }

    @Override
    public int hashCode() {
        int result = cells.hashCode();
        result = 31 * result + mines;
        return result;
    }
}