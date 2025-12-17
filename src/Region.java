import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盤面上のヒントや推論によって得られた「領域」を表すクラス。
 * 詳細設計書「2.1. Region クラス」および「3.1. Region生成フェーズ」に基づき実装。
 * 不変(Immutable)オブジェクト。
 */
public class Region {
    private final Set<Integer> cells; // 未確定セルのインデックス集合
    private final int mines; // その集合内に含まれる地雷数
    private final int originLevel; // 生成由来レベル (1:Base, 2:包含, 3:共通)
    private final Set<Integer> sourceHints; // このRegionを生成したヒントセルのインデックス集合

    /**
     * コンストラクタ
     */
    public Region(Set<Integer> cells, int mines, int originLevel) {
        this(cells, mines, originLevel, new HashSet<>());
    }

    /**
     * コンストラクタ（ヒント情報付き）
     */
    public Region(Set<Integer> cells, int mines, int originLevel, Set<Integer> sourceHints) {
        this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        this.mines = mines;
        this.originLevel = originLevel;
        this.sourceHints = Collections.unmodifiableSet(new HashSet<>(sourceHints));
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

    public Set<Integer> getSourceHints() {
        return sourceHints;
    }

    public int size() {
        return cells.size();
    }

    /**
     * 包含判定 (this ⊆ other)
     */
    public boolean isSubsetOf(Region other) {
        // サイズチェックで早期リターン（最適化）
        if (this.size() > other.size())
            return false;
        return other.cells.containsAll(this.cells);
    }

    /**
     * 差分領域生成 (this - other)
     * Lv2: 包含テクニックで使用
     */
    public Region subtract(Region other, int newLevel) {
        Set<Integer> newCells = new HashSet<>(this.cells);
        newCells.removeAll(other.cells);

        int newMines = this.mines - other.mines;

        // 矛盾回避（論理的にありえない場合はクランプするが、呼び出し元で検証すべき）
        newMines = Math.max(0, Math.min(newMines, newCells.size()));

        // ソースヒントは両方のRegionのヒントを結合
        Set<Integer> newSourceHints = new HashSet<>();
        newSourceHints.addAll(this.sourceHints);
        newSourceHints.addAll(other.sourceHints);

        return new Region(newCells, newMines, newLevel, newSourceHints);
    }

    /**
     * 共通部分から新たな制約を導出する (Lv3: 共通テクニック)
     * 設計書 3.1 準拠
     */
    public Set<Region> intersect(Region other, int newLevel) {
        Set<Region> result = new HashSet<>();

        // 共通部分 (Common)
        Set<Integer> commonCells = new HashSet<>(this.cells);
        commonCells.retainAll(other.cells);

        if (commonCells.isEmpty())
            return result; // 共通部分なし

        // 差分領域 (OnlyA, OnlyB)
        Set<Integer> onlyACells = new HashSet<>(this.cells);
        onlyACells.removeAll(commonCells); // A - B

        Set<Integer> onlyBCells = new HashSet<>(other.cells);
        onlyBCells.removeAll(commonCells); // B - A

        // 変数定義（設計書準拠）
        // |Common|, |OnlyA|, |OnlyB|
        int sizeCommon = commonCells.size();
        int sizeOnlyA = onlyACells.size();
        int sizeOnlyB = onlyBCells.size();

        // MA, MB
        int minesA = this.mines;
        int minesB = other.mines;

        // Commonの地雷数の範囲 [minC, maxC] を計算
        // maxC = min(|Common|, MA, MB)
        int maxC = Math.min(sizeCommon, Math.min(minesA, minesB));

        // minC = max(0, MA - |OnlyA|, MB - |OnlyB|)
        int minC = Math.max(0, Math.max(minesA - sizeOnlyA, minesB - sizeOnlyB));

        // ソースヒントは両方のRegionのヒントを結合
        Set<Integer> newSourceHints = new HashSet<>();
        newSourceHints.addAll(this.sourceHints);
        newSourceHints.addAll(other.sourceHints);

        // 制約導出: もし maxC == minC なら Common の地雷数は確定
        if (minC == maxC) {
            int determinedMines = minC;

            // 新Region生成 (Common)
            if (!commonCells.isEmpty()) {
                result.add(new Region(commonCells, determinedMines, newLevel, newSourceHints));
            }

            // 派生: OnlyA の地雷数も確定する (MA - CommonMines)
            if (!onlyACells.isEmpty()) {
                int minesOnlyA = minesA - determinedMines;
                result.add(new Region(onlyACells, minesOnlyA, newLevel, newSourceHints));
            }

            // 派生: OnlyB の地雷数も確定する (MB - CommonMines)
            if (!onlyBCells.isEmpty()) {
                int minesOnlyB = minesB - determinedMines;
                result.add(new Region(onlyBCells, minesOnlyB, newLevel, newSourceHints));
            }
        }

        // NOTE: 設計書では minC < maxC の場合の記述がないため、ここでは生成しない。
        // 将来的に背理法や高度な共通ロジックを入れる場合はここに記述する。

        return result;
    }

    /**
     * 等価性判定 (originLevelは無視する)
     * ※ 同じ制約内容なら、より低いレベルで生成されたものを優先するため
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Region))
            return false;
        Region region = (Region) o;
        return mines == region.mines && cells.equals(region.cells);
    }

    @Override
    public int hashCode() {
        // cellsとminesのみでハッシュを生成
        int result = cells.hashCode();
        result = 31 * result + mines;
        return result;
    }

    @Override
    public String toString() {
        // デバッグ用表示
        String cellStr = cells.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "{", "}"));

        String hintStr = "";
        if (!sourceHints.isEmpty()) {
            hintStr = " from hints " + sourceHints.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
        }

        return String.format("Lv%d: %s = %d%s", originLevel, cellStr, mines, hintStr);
    }
}