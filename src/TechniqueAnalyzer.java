import java.util.*;

/**
 * 盤面全体の状態を管理し、推論のステップを進めるクラス。
 * 「Region事前全列挙モデル」に基づき実装。
 */
public class TechniqueAnalyzer {

    // =========================================================================
    // 定数定義
    // =========================================================================
    private static final int MINE = -1;
    private static final int IGNORE = -2;
    private static final int FLAGGED = -3;
    private static final int SAFE = -4;

    // 難易度定数
    public static final int LV_UNSOLVED = -1;
    public static final int LV_1_1 = 1; // 埋めるだけ
    public static final int LV_1_2 = 2; // 包含 (初期盤面等の単純なもの) - 今回はLv1-3に統合
    public static final int LV_1_3 = 3; // 包含 (推論全般)
    public static final int LV_1_4 = 4; // 共通

    // =========================================================================
    // フィールド
    // =========================================================================
    private int[] board;
    private final int[] completeBoard;
    private final int[] difficultyMap;
    private final int size;

    // 生成されたすべてのRegionを保持するプール
    private List<Region> regionPool;

    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
        this.regionPool = new ArrayList<>();
    }

    /**
     * 解析のメインループ。
     */
    public void analyze() {
        boolean changed = true;
        int round = 1;

        // 初期ヒントは難易度0
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0)
                difficultyMap[i] = 0;
        }

        while (changed) {
            changed = false;
            System.out.println("\n--- Round " + round + " Start ---");

            // 1. 全Region生成 (Lv0, Lv1-3, Lv1-4...)
            generateAllRegions();

            // デバッグ出力
            // printRegionPool();

            // 2. ソルビング (最も低いレベルで解けるものを適用)
            Map<Integer, Integer> deduced = solveFromPool();

            if (!deduced.isEmpty()) {
                System.out.println("Round " + round + ": Found " + deduced.size() + " cells.");
                applyResult(deduced);
                changed = true;
                round++;
            }
        }
    }

    /**
     * 現在の盤面から、あらゆる可能性のあるRegionを生成しプールする。
     */
    private void generateAllRegions() {
        regionPool.clear();

        // 1. Base Regions (Lv0: 盤面のヒント由来)
        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) {
                Region r = createRegionFromHint(i);
                if (r != null) {
                    baseRegions.add(r);
                }
            }
        }
        regionPool.addAll(baseRegions);

        // 2. Derived Regions (Lv1-3: 包含テクニック由来 & Lv1-4: 共通テクニック由来)
        // Base Region同士を総当たりで比較
        for (int i = 0; i < baseRegions.size(); i++) {
            for (int j = i + 1; j < baseRegions.size(); j++) {
                Region rA = baseRegions.get(i);
                Region rB = baseRegions.get(j);

                // --- 包含判定 (Lv1-3) ---
                // rA ⊂ rB
                if (rA.isSubsetOf(rB)) {
                    Region diff = rB.subtract(rA, LV_1_3);
                    if (!diff.getCells().isEmpty()) {
                        regionPool.add(diff);
                    }
                }
                // rB ⊂ rA
                else if (rB.isSubsetOf(rA)) {
                    Region diff = rA.subtract(rB, LV_1_3);
                    if (!diff.getCells().isEmpty()) {
                        regionPool.add(diff);
                    }
                }
                // --- 共通判定 (Lv1-4) ---
                // 包含関係がない場合のみ実行
                else {
                    Set<Region> intersections = rA.intersect(rB, LV_1_4);
                    regionPool.addAll(intersections);
                }
            }
        }
    }

    /**
     * プールされたRegionを使って確定できるセルを探す。
     * 低いレベルのRegionを優先して適用する。
     */
    private Map<Integer, Integer> solveFromPool() {
        Map<Integer, Integer> deduced = new HashMap<>();
        Map<Integer, Integer> deducedLevel = new HashMap<>(); // 各セルがどのレベルで解けたか

        // レベル順(昇順)にソートして処理することで、
        // Lv0(Lv1-1相当) -> Lv1-3(包含) -> Lv1-4(共通) の順に判定される
        regionPool.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : regionPool) {
            boolean determined = false;
            int valToSet = -99;

            // Lv1-1 ロジック (埋めるだけ)
            if (r.getMines() == r.size()) {
                determined = true;
                valToSet = FLAGGED;
            } else if (r.getMines() == 0) {
                determined = true;
                valToSet = SAFE;
            }

            if (determined) {
                // 確定した難易度レベル
                // 基本は Lv1-1(1) だが、Region自体が高度なテクニック(Lv3, 4)で作られていればそれを採用
                int complexity = Math.max(LV_1_1, r.getOriginLevel());

                for (int cell : r.getCells()) {
                    // まだ解けていない、または より低いレベルで解けることが判明した場合
                    if (!deduced.containsKey(cell)) {
                        deduced.put(cell, valToSet);
                        deducedLevel.put(cell, complexity);

                        String type = (valToSet == FLAGGED) ? "MINE" : "SAFE";
                        System.out.println("  -> Solved: Cell " + cell + " is " + type +
                                " (via Region Lv" + r.getOriginLevel() + ")");
                    } else {
                        // 既に解けている場合、より低いレベルなら更新する
                        if (complexity < deducedLevel.get(cell)) {
                            deducedLevel.put(cell, complexity);
                        }
                    }
                }
            }
        }

        // 難易度マップへの反映 (まだ未確定の場所のみ)
        for (Map.Entry<Integer, Integer> entry : deducedLevel.entrySet()) {
            int cell = entry.getKey();
            int lvl = entry.getValue();
            if (difficultyMap[cell] == LV_UNSOLVED) {
                difficultyMap[cell] = lvl;
            }
        }

        return deduced;
    }

    /**
     * 推論結果を盤面に適用する。
     */
    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            // 正解盤面から実際の数字を取得 (SAFEの場合)
            if (val == SAFE) {
                val = completeBoard[cellIdx];
            }

            board[cellIdx] = val;
        }
        // applyResult後に自動的にループ先頭に戻り generateAllRegions が呼ばれるため
        // ここでのRegion更新は不要
    }

    private Region createRegionFromHint(int hintIdx) {
        int hintVal = board[hintIdx];
        List<Integer> neighbors = getNeighbors(hintIdx);
        Set<Integer> unknownCells = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : neighbors) {
            int val = board[nb];
            if (val == MINE) {
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                flaggedCount++;
            }
        }

        if (unknownCells.isEmpty())
            return null;
        int remainingMines = hintVal - flaggedCount;
        return new Region(unknownCells, remainingMines, 0); // Lv0 = Base Hint
    }

    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    private void printRegionPool() {
        System.out.println("--- Region Pool ---");
        regionPool.sort(Comparator.comparingInt(Region::getOriginLevel));
        for (Region r : regionPool) {
            System.out.println(r);
        }
    }

    public void printCurrentBoard(String label) {
        // ... (省略)
    }
}