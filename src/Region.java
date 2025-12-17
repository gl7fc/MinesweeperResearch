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

    // 生成元となったヒントのインデックス集合 (表示用)
    private final Set<Integer> sourceHints;

    // Region通し番号 (表示用)
    private int id = -1;

    public Region(Set<Integer> cells, int mines, int originLevel) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
        this.originLevel = originLevel;
        this.sourceHints = new HashSet<>();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void addSourceHint(int hintIdx) {
        this.sourceHints.add(hintIdx);
    }

    public void addSourceHints(Set<Integer> hints) {
        this.sourceHints.addAll(hints);
    }

    public String getSourceHintsString() {
        if (sourceHints.isEmpty())
            return "Derived";
        return sourceHints.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
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

    public boolean isSubsetOf(Region other) {
        return other.cells.containsAll(this.cells);
    }

    public Region subtract(Region other, int newLevel) {
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);
        int newMines = this.mines - other.mines;

        if (newMines < 0)
            newMines = 0;
        if (newMines > newCells.size())
            newMines = newCells.size();

        int derivedLevel = Math.max(newLevel, Math.max(this.originLevel, other.originLevel));

        Region newRegion = new Region(newCells, newMines, derivedLevel);
        newRegion.addSourceHints(this.sourceHints);
        newRegion.addSourceHints(other.sourceHints);

        return newRegion;
    }

    public Set<Region> intersect(Region other, int newLevel) {
        Set<Region> result = new HashSet<>();
        int derivedLevel = Math.max(newLevel, Math.max(this.originLevel, other.originLevel));

        Set<Integer> commonCells = new HashSet<>(this.cells);
        commonCells.retainAll(other.cells);

        if (commonCells.isEmpty())
            return result;

        Set<Integer> onlyACells = new HashSet<>(this.cells);
        onlyACells.removeAll(commonCells);

        Set<Integer> onlyBCells = new HashSet<>(other.cells);
        onlyBCells.removeAll(commonCells);

        int maxC = Math.min(commonCells.size(), Math.min(this.mines, other.mines));
        int minC = Math.max(0, Math.max(this.mines - onlyACells.size(), other.mines - onlyBCells.size()));

        if (minC == maxC) {
            int determinedMines = minC;
            Region rC = new Region(commonCells, determinedMines, derivedLevel);
            rC.addSourceHints(this.sourceHints);
            rC.addSourceHints(other.sourceHints);
            result.add(rC);

            if (!onlyACells.isEmpty()) {
                Region rA = new Region(onlyACells, this.mines - determinedMines, derivedLevel);
                rA.addSourceHints(this.sourceHints);
                rA.addSourceHints(other.sourceHints);
                result.add(rA);
            }
            if (!onlyBCells.isEmpty()) {
                Region rB = new Region(onlyBCells, other.mines - determinedMines, derivedLevel);
                rB.addSourceHints(this.sourceHints);
                rB.addSourceHints(other.sourceHints);
                result.add(rB);
            }
        } else {
            int minMinesOnlyA = this.mines - maxC;
            int maxMinesOnlyA = this.mines - minC;
            if (minMinesOnlyA == maxMinesOnlyA && !onlyACells.isEmpty()) {
                Region rA = new Region(onlyACells, minMinesOnlyA, derivedLevel);
                rA.addSourceHints(this.sourceHints);
                rA.addSourceHints(other.sourceHints);
                result.add(rA);
            }

            int minMinesOnlyB = other.mines - maxC;
            int maxMinesOnlyB = other.mines - minC;
            if (minMinesOnlyB == maxMinesOnlyB && !onlyBCells.isEmpty()) {
                Region rB = new Region(onlyBCells, minMinesOnlyB, derivedLevel);
                rB.addSourceHints(this.sourceHints);
                rB.addSourceHints(other.sourceHints);
                result.add(rB);
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
        return String.format("%s(Lv%d)=%d", cellStr, originLevel, mines);
    }

    public String toLogString() {
        String cellStr = cells.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "{", "}"));
        return String.format("%s=%d", cellStr, mines);
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