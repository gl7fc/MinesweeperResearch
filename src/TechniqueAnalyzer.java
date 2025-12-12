import java.util.*;

/**
 * テクニックベースの難易度解析クラス
 * 人間が解く手順（定石）を模倣して、各セルがどのレベルの技術で解けるかを判定する。
 */
public class TechniqueAnalyzer {

    // 定数定義 (HintCountCalculator等と合わせる)
    private static final int MINE = -1; // UNKNOWN (未確定変数)
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定

    // 結果として返す難易度レベル
    public static final int LV_UNSOLVED = -1;
    public static final int LV_1_1 = 1; // 埋めるだけ
    public static final int LV_1_2 = 2; // 包含 (確定)
    public static final int LV_1_3 = 3; // 包含 (情報のみ) - ※DifficultyMapには直接出ないこともある
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

    /**
     * 解析のメインプロセス
     */
    public void analyze() {
        // 1. 初期化
        currentBoard = Arrays.copyOf(initialPuzzle, initialPuzzle.length);
        activeRegions = new ArrayList<>();

        // 初期ヒント(数字)は難易度0
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = 0;
            }
        }

        // 初期Regionの生成
        initRegions();

        // 2. メインループ: 変化がなくなるまで繰り返す
        boolean changed;
        do {
            changed = false;

            // Step 1: Lv1-1 埋めるだけ
            if (solveLv1_1()) {
                changed = true;
                continue; // 盤面が変わったら最初(Lv1-1)から再チェック
            }

            // Step 2: Lv1-2 包含 (確定)
            if (solveLv1_2()) {
                changed = true;
                continue;
            }

            // Step 3: Lv1-3 包含 (情報のみ)
            // ※これは盤面確定ではなくRegionが増えるだけだが、その新RegionでLv1-1等が解ける可能性がある
            if (solveLv1_3()) {
                changed = true;
                continue;
            }

            // Step 4: Lv1-4 共通部分 (Phase 5で実装予定)
            if (solveLv1_4()) {
                changed = true;
                continue;
            }

            // Step 5: Lv2 背理法 (Phase 4で実装予定)
            if (solveLv2()) {
                changed = true;
                continue;
            }

        } while (changed);
    }

    /**
     * 盤面上の数字セルを走査し、初期のRegionリストを生成する
     */
    private void initRegions() {
        activeRegions.clear();
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) { // ヒント数字
                createRegionFromHint(i);
            }
        }
    }

    /**
     * 特定のヒントセルからRegionを生成してリストに追加
     */
    private void createRegionFromHint(int index) {
        int hintValue = currentBoard[index];
        Set<Integer> neighbors = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : getNeighbors(index)) {
            if (currentBoard[nb] == MINE) { // UNKNOWN
                neighbors.add(nb);
            } else if (currentBoard[nb] == FLAGGED) {
                flaggedCount++;
            }
        }

        if (!neighbors.isEmpty()) {
            int remainingMines = hintValue - flaggedCount;
            // 矛盾回避（0未満にはならないはずだが）
            if (remainingMines < 0)
                remainingMines = 0;

            Region region = new Region(neighbors, remainingMines);

            // 重複チェックして追加
            if (!activeRegions.contains(region)) {
                activeRegions.add(region);
            }
        }
    }

    // --- Phase 2以降で実装するメソッド群 ---

    /**
     * Lv1-1: 埋めるだけ
     * 単一のRegionを見て、
     * - 残り地雷数 == セル数 -> すべて地雷
     * - 残り地雷数 == 0 -> すべて安全
     */
    private boolean solveLv1_1() {
        Map<Integer, Integer> deduced = new HashMap<>();

        for (Region region : activeRegions) {
            // 空のRegionはスキップ
            if (region.isEmpty())
                continue;

            // Case 1: 残りの未確定セルがすべて地雷
            if (region.getMines() == region.size()) {
                for (int cellIdx : region.getCells()) {
                    // まだ確定していない場合のみ追加
                    if (currentBoard[cellIdx] == MINE) {
                        deduced.put(cellIdx, FLAGGED);
                    }
                }
            }
            // Case 2: 残りの未確定セルがすべて安全
            else if (region.getMines() == 0) {
                for (int cellIdx : region.getCells()) {
                    if (currentBoard[cellIdx] == MINE) {
                        // 正解盤面から正しい数字(または空白)を取得してセット
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

    private boolean solveLv1_2() {
        // TODO: Phase 3で実装
        return false;
    }

    private boolean solveLv1_3() {
        // TODO: Phase 3で実装
        return false;
    }

    private boolean solveLv1_4() {
        // TODO: Phase 5で実装
        return false;
    }

    private boolean solveLv2() {
        // TODO: Phase 4で実装
        return false;
    }

    // --- 共通ユーティリティ ---

    /**
     * 推論で確定した結果を盤面に反映し、Regionリストを更新する
     * 
     * @param deduced 確定したセル (Key: index, Value: FLAGGED or 数字)
     * @param level   確定に使用したテクニックレベル
     */
    private void applyResult(Map<Integer, Integer> deduced, int level) {
        if (deduced.isEmpty())
            return;

        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int idx = entry.getKey();
            int val = entry.getValue();

            // 盤面更新
            if (currentBoard[idx] == MINE) { // 念のためチェック
                currentBoard[idx] = val;

                // 難易度記録 (未記録の場合のみ)
                if (difficultyMap[idx] == LV_UNSOLVED) {
                    difficultyMap[idx] = level;
                }

                // 安全セルが開いた場合、新たなヒントになるのでRegion生成を試みる
                // 地雷(FLAGGED)の場合はRegionにはならない
                if (val >= 0) {
                    createRegionFromHint(idx);
                }
            }
        }

        // 既存Regionの更新
        // 確定したセルをすべてのRegionから取り除く
        List<Region> nextRegions = new ArrayList<>();

        for (Region r : activeRegions) {
            Region current = r;

            for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
                int idx = entry.getKey();
                int val = entry.getValue();
                boolean isMine = (val == FLAGGED);

                // Regionに含まれるセルが確定した場合、Regionを更新(縮小)する
                if (current.getCells().contains(idx)) {
                    current = current.removeCell(idx, isMine);
                }
            }

            // 空でなければ次世代リストに残す
            if (!current.isEmpty()) {
                nextRegions.add(current);
            }
        }

        // 重複排除して更新
        activeRegions = new ArrayList<>(new HashSet<>(nextRegions));
    }

    /**
     * 周囲8セルのインデックスを取得
     */
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
}