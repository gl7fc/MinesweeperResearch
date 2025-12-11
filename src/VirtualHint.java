import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 推論の過程で生成される「制約」を扱うクラス。
 * 実ヒント（盤面の数字）も仮想ヒント（推論結果）も統一して扱う。
 */
public class VirtualHint {
    public Set<Integer> cells; // 対象となる未確定セル(UNKNOWN)のインデックス集合
    public int mines; // cellsの中に含まれる地雷の総数
    public int generation; // この情報の世代（0スタート）
    public List<VirtualHint> sources; // 親ヒント（系譜記録用、今回は簡易実装のためnull許容）

    public VirtualHint(Set<Integer> cells, int mines, int generation, List<VirtualHint> sources) {
        this.cells = new HashSet<>(cells);
        this.mines = mines;
        this.generation = generation;
        this.sources = sources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VirtualHint that = (VirtualHint) o;
        return mines == that.mines && Objects.equals(cells, that.cells);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells, mines);
    }

    @Override
    public String toString() {
        return "Hint{gen=" + generation + ", mines=" + mines + ", cells=" + cells + "}";
    }
}