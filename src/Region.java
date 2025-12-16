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
     * @param originLevel 生成レベル
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

    public int size() {
        return cells.size();
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
    public Region subtract(Region other, int newLevel) {
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);

        // mines = this.mines - other.mines
        int newMines = this.mines - other.mines;

        // 矛盾回避
        if (newMines < 0)
            newMines = 0;
        if (newMines > newCells.size())
            newMines = newCells.size();

        return new Region(newCells, newMines, newLevel);
    }

    /**
     * 共通部分を利用した推論を行い、新しい Region のリストを返す (Lv1-4用)
     * 2つのRegion (A, B) の共通部分と差分領域の地雷数範囲を計算し、
     * 確定する情報があればそれを Region として返す。
     */
    public Set<Region> intersect(Region other, int newLevel) {
        Set<Region> result = new HashSet<>();

        // 共通部分のセル集合
        Set<Integer> commonCells = new HashSet<>(this.cells);
        commonCells.retainAll(other.cells);

        if (commonCells.isEmpty())
            return result; // 共通部分なし

        // 差分領域 (OnlyA, OnlyB)
        Set<Integer> onlyACells = new HashSet<>(this.cells);
        onlyACells.removeAll(commonCells);

        Set<Integer> onlyBCells = new HashSet<>(other.cells);
        onlyBCells.removeAll(commonCells);

        // 各領域のサイズ
        int sizeCommon = commonCells.size();
        int sizeOnlyA = onlyACells.size();
        int sizeOnlyB = onlyBCells.size();

        // 共通部分の地雷数の範囲 [minC, maxC] を計算
        // maxC = min(|Common|, mA, mB)
        int maxC = Math.min(sizeCommon, Math.min(this.mines, other.mines));

        // minC = max(0, mA - |OnlyA|, mB - |OnlyB|)
        int minC = Math.max(0, Math.max(this.mines - sizeOnlyA, other.mines - sizeOnlyB));

        // ケース1: 共通部分の地雷数が確定する場合 (minC == maxC)
        if (minC == maxC) {
            int determinedMines = minC;
            result.add(new Region(commonCells, determinedMines, newLevel));

            // 派生: OnlyA の地雷数も確定する (mA - commonMines)
            if (!onlyACells.isEmpty()) {
                int minesA = this.mines - determinedMines;
                result.add(new Region(onlyACells, minesA, newLevel));
            }

            // 派生: OnlyB の地雷数も確定する (mB - commonMines)
            if (!onlyBCells.isEmpty()) {
                int minesB = other.mines - determinedMines;
                result.add(new Region(onlyBCells, minesB, newLevel));
            }
        } else {
            // ケース2: 共通部分は確定しないが、周辺領域(OnlyA, OnlyB)が確定するケース
            // OnlyAの地雷数範囲: [mA - maxC, mA - minC]
            // OnlyBの地雷数範囲: [mB - maxC, mB - minC]

            // OnlyA が確定するかチェック
            int minMinesOnlyA = this.mines - maxC;
            int maxMinesOnlyA = this.mines - minC;
            if (minMinesOnlyA == maxMinesOnlyA && !onlyACells.isEmpty()) {
                result.add(new Region(onlyACells, minMinesOnlyA, newLevel));
            }

            // OnlyB が確定するかチェック
            int minMinesOnlyB = other.mines - maxC;
            int maxMinesOnlyB = other.mines - minC;
            if (minMinesOnlyB == maxMinesOnlyB && !onlyBCells.isEmpty()) {
                result.add(new Region(onlyBCells, minMinesOnlyB, newLevel));
            }
        }

        return result;
    }

    @Override
    public String toString() {
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