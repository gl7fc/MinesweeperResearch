import java.util.*;

/**
 * テクニックベースの難易度解析クラス
 * Phase 2 Fix v3: applyResultで盤面全体との完全同期を行い、情報の更新漏れを防ぐ
 */
public class TechniqueAnalyzer {

    // 定数定義
    private static final int MINE = -1; // UNKNOWN
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定

    // 難易度レベル
    public static final int LV_UNSOLVED = -1;
    public static final int LV_1_1 = 1; // 埋めるだけ
    public static final int LV_1_2 = 2; // 包含 (確定)
    public static final int LV_1_3 = 3; // 包含 (情報のみ)
    public static final int LV_1_4 = 4; // 共通部分
    public static final int LV_2 = 5; // 背理法

    private final int[] initialPuzzle;
    private final int[] completeBoard;
    private final int size;

    // 解析中の状態
    private int[] currentBoard;
    private int[] difficultyMap;
    private List<Region> activeRegions;

    public TechniqueAnalyzer(int[] puzzle, int[] solution, int size) {
        this.initialPuzzle = puzzle;
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[puzzle.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
    }

    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    public void analyze() {
        // 1. 初期化
        currentBoard = Arrays.copyOf(initialPuzzle, initialPuzzle.length);
        activeRegions = new ArrayList<>();

        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = 0;
            }
        }

        initRegions();

        // 2. メインループ
        boolean changed;
        do {
            changed = false;

            // Lv1-1: 埋めるだけ
            if (solveLv1_1()) {
                changed = true;
                continue;
            }

            // Phase 3以降で Lv1-2 等を実装
            if (solveLv1_2()) {
                changed = true;
                continue;
            }

        } while (changed);
    }

    /**
     * Lv1-1: 埋めるだけ
     */
    private boolean solveLv1_1() {
        Map<Integer, Integer> deduced = new HashMap<>();

        for (Region region : activeRegions) {
            if (region.isEmpty())
                continue;

            int m = region.getMines();
            int s = region.size();

            // ※ここで currentBoard との不整合(ゴミ)があっても、
            // 以下のループ内チェックと applyResult の強力なクリーニングで吸収する

            if (m == s) { // 全部地雷
                for (int cellIdx : region.getCells()) {
                    if (currentBoard[cellIdx] == MINE) {
                        deduced.put(cellIdx, FLAGGED);
                    }
                }
            } else if (m == 0) { // 全部安全
                for (int cellIdx : region.getCells()) {
                    if (currentBoard[cellIdx] == MINE) {
                        int trueVal = completeBoard[cellIdx];
                        deduced.put(cellIdx, trueVal);
                    }
                }
            }
        }

        if (!deduced.isEmpty()) {
            applyResult(deduced, LV_1_1);
            return true;
        }
        return false;
    }

    /**
     * 推論結果の反映 (完全同期版)
     */
    private void applyResult(Map<Integer, Integer> deduced, int level) {
        if (deduced.isEmpty())
            return;

        // Step 1: 盤面と難易度マップを更新
        // 先に currentBoard を「最新の真実」にする
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int idx = entry.getKey();
            int val = entry.getValue();

            if (currentBoard[idx] == MINE) {
                currentBoard[idx] = val;
                if (difficultyMap[idx] == LV_UNSOLVED) {
                    difficultyMap[idx] = level;
                }
            }
        }

        // Step 2: 新規Regionの作成
        // 新しく開いた数字セル(Safe)からRegionを作る
        List<Region> newRegions = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int idx = entry.getKey();
            int val = entry.getValue();
            if (val >= 0) {
                Region nr = getRegionFromHint(idx);
                if (nr != null) {
                    newRegions.add(nr);
                }
            }
        }

        // Step 3: 既存Regionの更新とクリーニング (修正ポイント)
        // deduced だけでなく、currentBoard 全体と照らし合わせて
        // 「既に確定しているセル」が Region に残っていたら全て削除する
        List<Region> nextRegions = new ArrayList<>();

        for (Region r : activeRegions) {
            Region current = r;

            // Region内の各セルについて、盤面上で確定済みかチェック
            // (注意: removeCellするとgetCellsの中身が変わる可能性があるため、
            // 元のセルセットをコピーして回すか、removeCellが新しいインスタンスを返すことを利用する)

            for (int cellIdx : r.getCells()) {
                int boardVal = currentBoard[cellIdx];

                // MINE(-1) 以外なら、それはもう確定しているセル
                if (boardVal != MINE) {
                    boolean isMine = (boardVal == FLAGGED);
                    current = current.removeCell(cellIdx, isMine);
                }
            }

            if (!current.isEmpty()) {
                nextRegions.add(current);
            }
        }

        // Step 4: マージ
        nextRegions.addAll(newRegions);
        activeRegions = new ArrayList<>(new HashSet<>(nextRegions));
    }

    // --- Helper Methods ---

    private void initRegions() {
        activeRegions.clear();
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                Region r = getRegionFromHint(i);
                if (r != null && !activeRegions.contains(r)) {
                    activeRegions.add(r);
                }
            }
        }
    }

    private Region getRegionFromHint(int index) {
        int hintValue = currentBoard[index];
        Set<Integer> neighbors = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : getNeighbors(index)) {
            if (currentBoard[nb] == MINE) {
                neighbors.add(nb);
            } else if (currentBoard[nb] == FLAGGED) {
                flaggedCount++;
            }
        }

        if (!neighbors.isEmpty()) {
            int remainingMines = hintValue - flaggedCount;
            if (remainingMines < 0)
                remainingMines = 0;
            return new Region(neighbors, remainingMines);
        }
        return null;
    }

    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    // Stubs for Phase 3+
    private boolean solveLv1_2() {
        return false;
    }

    private boolean solveLv1_3() {
        return false;
    }

    private boolean solveLv1_4() {
        return false;
    }

    private boolean solveLv2() {
        return false;
    }
}