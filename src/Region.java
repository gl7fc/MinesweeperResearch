import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盤面上のヒントや推論によって得られた「領域」を表すクラス.
 * 不変(Immutable)オブジェクトとして扱う.
 * 
 * ★追加機能: 親子関係の追跡
 * - triggerCells: このRegionを生成/更新させた確定セル
 * - parentRegionSnapshot: 親Regionの状態記録
 * - generationDepth: 派生深度（0=初期Lv1）
 */
public class Region {
    private final Set<Integer> cells; // この領域に含まれる未確定セルのインデックス集合
    private final int mines; // この領域内に存在する地雷の数
    private final int originLevel; // この情報が生成されたテクニックのレベル

    // 生成元となったヒントのインデックス集合 (表示用)
    private final Set<Integer> sourceHints;

    // Region通し番号 (表示用)
    private int id = -1;

    // ★追加フィールド（親子関係追跡用）
    private final Set<Integer> triggerCells; // 生成/更新のトリガーとなった確定セル
    private final String parentRegionSnapshot; // 親Regionの状態記録
    private final int generationDepth; // 派生深度（0=初期Lv1）

    /**
     * 基本コンストラクタ（初期生成用）
     */
    public Region(Set<Integer> cells, int mines, int originLevel) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
        this.originLevel = originLevel;
        this.sourceHints = new HashSet<>();
        // ★初期生成時は親子関係なし
        this.triggerCells = Collections.emptySet();
        this.parentRegionSnapshot = "";
        this.generationDepth = 0;
    }

    /**
     * ★拡張コンストラクタ（派生/更新用）
     */
    public Region(Set<Integer> cells, int mines, int originLevel,
            Set<Integer> triggerCells, String parentSnapshot, int depth) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
        this.originLevel = originLevel;
        this.sourceHints = new HashSet<>();
        // ★親子関係情報
        this.triggerCells = Collections.unmodifiableSet(new HashSet<>(triggerCells));
        this.parentRegionSnapshot = parentSnapshot;
        this.generationDepth = depth;
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

    // ★追加ゲッター
    public Set<Integer> getTriggerCells() {
        return triggerCells;
    }

    public String getParentRegionSnapshot() {
        return parentRegionSnapshot;
    }

    public int getGenerationDepth() {
        return generationDepth;
    }

    /**
     * ★triggerCellsをCSV出力用の文字列に変換
     */
    public String getTriggerCellsString() {
        if (triggerCells.isEmpty()) {
            return "";
        }
        return triggerCells.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public boolean isSubsetOf(Region other) {
        return other.cells.containsAll(this.cells);
    }

    /**
     * 包含判定による派生（Lv2）
     * ★親子関係情報を追加
     */
    public Region subtract(Region other, int newLevel) {
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);
        int newMines = this.mines - other.mines;

        if (newMines < 0)
            newMines = 0;
        if (newMines > newCells.size())
            newMines = newCells.size();

        int derivedLevel = Math.max(newLevel, Math.max(this.originLevel, other.originLevel));

        // ★親スナップショット作成
        String parentSnapshot = this.toLogString() + " - " + other.toLogString();
        int newDepth = Math.max(this.generationDepth, other.generationDepth) + 1;

        Region newRegion = new Region(
                newCells, newMines, derivedLevel,
                Collections.emptySet(), // 論理演算なのでtriggerCellsは空
                parentSnapshot, newDepth);
        newRegion.addSourceHints(this.sourceHints);
        newRegion.addSourceHints(other.sourceHints);

        return newRegion;
    }

    /**
     * 共通判定による派生（Lv3）
     * ★親子関係情報を追加
     */
    public Set<Region> intersect(Region other, int newLevel) {
        Set<Region> result = new HashSet<>();
        int derivedLevel = Math.max(newLevel, Math.max(this.originLevel, other.originLevel));

        // ★親スナップショット作成
        String parentSnapshot = this.toLogString() + " ∩ " + other.toLogString();
        int newDepth = Math.max(this.generationDepth, other.generationDepth) + 1;

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
            Region rC = new Region(commonCells, determinedMines, derivedLevel,
                    Collections.emptySet(), parentSnapshot + " [common]", newDepth);
            rC.addSourceHints(this.sourceHints);
            rC.addSourceHints(other.sourceHints);
            result.add(rC);

            if (!onlyACells.isEmpty()) {
                Region rA = new Region(onlyACells, this.mines - determinedMines, derivedLevel,
                        Collections.emptySet(), parentSnapshot + " [onlyA]", newDepth);
                rA.addSourceHints(this.sourceHints);
                rA.addSourceHints(other.sourceHints);
                result.add(rA);
            }
            if (!onlyBCells.isEmpty()) {
                Region rB = new Region(onlyBCells, other.mines - determinedMines, derivedLevel,
                        Collections.emptySet(), parentSnapshot + " [onlyB]", newDepth);
                rB.addSourceHints(this.sourceHints);
                rB.addSourceHints(other.sourceHints);
                result.add(rB);
            }
        } else {
            int minMinesOnlyA = this.mines - maxC;
            int maxMinesOnlyA = this.mines - minC;
            if (minMinesOnlyA == maxMinesOnlyA && !onlyACells.isEmpty()) {
                Region rA = new Region(onlyACells, minMinesOnlyA, derivedLevel,
                        Collections.emptySet(), parentSnapshot + " [onlyA]", newDepth);
                rA.addSourceHints(this.sourceHints);
                rA.addSourceHints(other.sourceHints);
                result.add(rA);
            }

            int minMinesOnlyB = other.mines - maxC;
            int maxMinesOnlyB = other.mines - minC;
            if (minMinesOnlyB == maxMinesOnlyB && !onlyBCells.isEmpty()) {
                Region rB = new Region(onlyBCells, minMinesOnlyB, derivedLevel,
                        Collections.emptySet(), parentSnapshot + " [onlyB]", newDepth);
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

    /**
     * ★追加: 親子関係情報を含む詳細文字列
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toString());
        if (!triggerCells.isEmpty()) {
            sb.append(" [trigger: ").append(getTriggerCellsString()).append("]");
        }
        if (!parentRegionSnapshot.isEmpty()) {
            sb.append(" [parent: ").append(parentRegionSnapshot).append("]");
        }
        sb.append(" [depth: ").append(generationDepth).append("]");
        return sb.toString();
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