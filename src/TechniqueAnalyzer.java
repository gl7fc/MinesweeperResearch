import java.util.*;

/**
 * Region事前全列挙モデルによる難易度解析クラス
 * 詳細設計書「2.2. TechniqueAnalyzer クラス」および「3. アルゴリズム詳細」に基づき実装
 */
public class TechniqueAnalyzer {

    // =========================================================================
    // 定数定義
    // =========================================================================
    private static final int MINE = -1; // 未確定変数
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定
    private static final int SAFE = -4; // 安全確定（一時的に使用）

    // 難易度レベル定数
    public static final int LV_UNSOLVED = -1;
    public static final int LV_0 = 0; // 初期ヒント
    public static final int LV_1 = 1; // Base Hint (単純)
    public static final int LV_2 = 2; // 包含テクニック
    public static final int LV_3 = 3; // 共通テクニック

    // =========================================================================
    // フィールド
    // =========================================================================
    private int[] currentBoard; // 現在の盤面状態
    private final int[] completeBoard; // 正解盤面
    private final int[] difficultyMap; // 結果格納用
    private final int size;
    private final Set<Integer> initialHints; // 初期ヒントの位置を記録

    private Map<Set<Integer>, Region> regionPool; // Regionプール（重複排除用）

    // =========================================================================
    // コンストラクタ
    // =========================================================================
    public TechniqueAnalyzer(int[] initialBoard, int[] solution, int size) {
        this.currentBoard = Arrays.copyOf(initialBoard, initialBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[initialBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
        this.regionPool = new HashMap<>();

        // 初期ヒントの位置を記録
        this.initialHints = new HashSet<>();
        for (int i = 0; i < initialBoard.length; i++) {
            if (initialBoard[i] >= 0) {
                initialHints.add(i);
            }
        }
    }

    // =========================================================================
    // 公開メソッド
    // =========================================================================

    /**
     * 解析のメインループ
     * 階層的探索: Lv1で行き詰まったらLv2、Lv2で解けたらLv1に戻る、というように進める
     */
    public void analyze() {
        // 初期ヒントは難易度0
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = LV_0;
            }
        }

        int round = 1;

        while (!isAllSolved()) {
            System.out.println("\n========== Round " + round + " ==========");

            // Phase 1: Base Regions (Lv1) のみで解けるだけ解く
            boolean lv1Changed = true;
            while (lv1Changed && !isAllSolved()) {
                // Lv1のRegionだけを生成
                generateBaseRegions();

                System.out.println("[Lv1] Generated " + regionPool.size() + " base regions");
                printRegionPool();

                Map<Integer, Integer> deduced = solveFromPool();

                if (deduced.isEmpty()) {
                    lv1Changed = false;
                } else {
                    System.out.println("[Lv1] Solved " + deduced.size() + " cells");
                    applyResult(deduced);
                }
            }

            if (isAllSolved())
                break;

            // Phase 2: Lv2 (包含) を試す
            System.out.println("\n[Lv2] Trying inclusion technique...");
            generateUpToLevel(LV_2);

            System.out.println("[Lv2] Generated " + regionPool.size() + " regions (Lv1+Lv2)");
            printRegionPool();

            Map<Integer, Integer> lv2Deduced = solveFromPool();

            if (!lv2Deduced.isEmpty()) {
                System.out.println("[Lv2] Solved " + lv2Deduced.size() + " cells");
                applyResult(lv2Deduced);
                round++;
                continue; // Lv1に戻る
            }

            if (isAllSolved())
                break;

            // Phase 3: Lv3 (共通) を試す
            System.out.println("\n[Lv3] Trying intersection technique...");
            generateUpToLevel(LV_3);

            System.out.println("[Lv3] Generated " + regionPool.size() + " regions (Lv1+Lv2+Lv3)");
            printRegionPool();

            Map<Integer, Integer> lv3Deduced = solveFromPool();

            if (!lv3Deduced.isEmpty()) {
                System.out.println("[Lv3] Solved " + lv3Deduced.size() + " cells");
                applyResult(lv3Deduced);
                round++;
                continue; // Lv1に戻る
            }

            // どのレベルでも解けなかった場合は終了
            System.out.println("\nNo more cells can be solved at any level. Stopping.");
            break;
        }

        System.out.println("\n========== Analysis Complete ==========");
        if (isAllSolved()) {
            System.out.println("All cells solved!");
        } else {
            System.out.println("Some cells remain unsolved (may require Lv4+ techniques)");
        }
    }

    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    // =========================================================================
    // Phase 1: Region生成フェーズ
    // =========================================================================

    /**
     * 現在の盤面から全てのRegionを生成し、プールに追加する
     * 設計書「3.1. Region生成フェーズ」に準拠
     */
    private void generateAllRegions() {
        generateUpToLevel(LV_3);
    }

    /**
     * 指定されたレベルまでのRegionを生成
     */
    private void generateUpToLevel(int maxLevel) {
        regionPool.clear();

        // Step 1: Base Regions (Lv1) - 盤面の数字ヒントから生成
        List<Region> baseRegions = generateBaseRegionsInternal();
        for (Region r : baseRegions) {
            addToPool(r);
        }

        if (maxLevel < LV_2)
            return;

        // Step 2: Derived Regions - 包含 (Lv2)
        List<Region> derivedSubtract = generateSubtractionRegions(baseRegions);
        for (Region r : derivedSubtract) {
            addToPool(r);
        }

        if (maxLevel < LV_3)
            return;

        // Step 3: Derived Regions - 共通 (Lv3)
        List<Region> derivedIntersect = generateIntersectionRegions(baseRegions);
        for (Region r : derivedIntersect) {
            addToPool(r);
        }
    }

    /**
     * Base Regions生成のみ（外部から呼ばれる用）
     */
    private void generateBaseRegions() {
        regionPool.clear();
        List<Region> baseRegions = generateBaseRegionsInternal();
        for (Region r : baseRegions) {
            addToPool(r);
        }
    }

    /**
     * Base Regions生成 (盤面の数字ヒントから)
     */
    private List<Region> generateBaseRegionsInternal() {
        List<Region> regions = new ArrayList<>();

        // 初期ヒントの位置からのみRegionを生成
        for (int hintIdx : initialHints) {
            Region r = createRegionFromHint(hintIdx, LV_1);
            if (r != null && !r.getCells().isEmpty()) {
                regions.add(r);
            }
        }

        return regions;
    }

    /**
     * ヒント位置からRegionを生成
     */
    private Region createRegionFromHint(int hintIdx, int level) {
        int hintVal = currentBoard[hintIdx];
        List<Integer> neighbors = getNeighbors(hintIdx);
        Set<Integer> unknownCells = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : neighbors) {
            int val = currentBoard[nb];
            if (val == MINE) {
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                flaggedCount++;
            }
        }

        if (unknownCells.isEmpty()) {
            return null;
        }

        int remainingMines = hintVal - flaggedCount;

        // ソースヒントを記録
        Set<Integer> sourceHints = new HashSet<>();
        sourceHints.add(hintIdx);

        return new Region(unknownCells, remainingMines, level, sourceHints);
    }

    /**
     * 包含関係から差分Regionを生成 (Lv2)
     */
    private List<Region> generateSubtractionRegions(List<Region> baseRegions) {
        List<Region> derived = new ArrayList<>();

        // 全ペアについて包含関係をチェック
        for (int i = 0; i < baseRegions.size(); i++) {
            Region rA = baseRegions.get(i);
            for (int j = 0; j < baseRegions.size(); j++) {
                if (i == j)
                    continue;
                Region rB = baseRegions.get(j);

                // A ⊂ B の場合、差分 D = B - A を生成
                if (rA.isSubsetOf(rB)) {
                    Region diff = rB.subtract(rA, LV_2);

                    // 空でなく、論理的に妥当な場合のみ追加
                    if (!diff.getCells().isEmpty() &&
                            diff.getMines() >= 0 &&
                            diff.getMines() <= diff.size()) {
                        derived.add(diff);
                    }
                }
            }
        }

        return derived;
    }

    /**
     * 共通部分から新しいRegionを生成 (Lv3)
     */
    private List<Region> generateIntersectionRegions(List<Region> baseRegions) {
        List<Region> derived = new ArrayList<>();

        // 全ペアについて共通部分をチェック
        for (int i = 0; i < baseRegions.size(); i++) {
            Region rA = baseRegions.get(i);
            for (int j = i + 1; j < baseRegions.size(); j++) {
                Region rB = baseRegions.get(j);

                // 包含関係がある場合はスキップ（Lv2で処理済み）
                if (rA.isSubsetOf(rB) || rB.isSubsetOf(rA)) {
                    continue;
                }

                // 共通部分を持つ場合、intersectメソッドで新Regionを生成
                Set<Region> intersectResults = rA.intersect(rB, LV_3);
                derived.addAll(intersectResults);
            }
        }

        return derived;
    }

    /**
     * Regionをプールに追加（重複排除・レベル優先）
     * 同じ制約内容なら、より低いレベルのものを優先
     */
    private void addToPool(Region newRegion) {
        Set<Integer> key = newRegion.getCells();

        if (regionPool.containsKey(key)) {
            Region existing = regionPool.get(key);
            // 同じセル集合で同じ地雷数の場合のみ比較
            if (existing.getMines() == newRegion.getMines()) {
                // より低いレベルを優先
                if (newRegion.getOriginLevel() < existing.getOriginLevel()) {
                    regionPool.put(key, newRegion);
                }
            } else {
                // 地雷数が異なる場合は別のRegionとして扱う（キーを変える必要がある）
                // ここでは簡易的に、cells+minesをキーとするため、別途格納
                // ※設計書では「cellsとminesで等価性判定」とあるため、実際にはこのケースは発生しないはず
            }
        } else {
            regionPool.put(key, newRegion);
        }
    }

    // =========================================================================
    // Phase 2: 解決フェーズ
    // =========================================================================

    // 解決に使用したRegionを記録するマップ
    private Map<Integer, Integer> cellToRegionIndex = new HashMap<>();

    /**
     * プール内のRegionをレベル順に試し、解けるセルを探す
     * 設計書「3.2. 解決フェーズ」に準拠
     */
    private Map<Integer, Integer> solveFromPool() {
        Map<Integer, Integer> deduced = new HashMap<>();
        cellToRegionIndex.clear();

        // レベル順にソート
        List<Region> sortedRegions = new ArrayList<>(regionPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        // ソートされた順にRegionを試す
        for (int regionIdx = 0; regionIdx < sortedRegions.size(); regionIdx++) {
            Region region = sortedRegions.get(regionIdx);

            // mines == cells.size() → 全地雷
            if (region.getMines() == region.size()) {
                for (int cell : region.getCells()) {
                    if (!deduced.containsKey(cell) && currentBoard[cell] == MINE) {
                        deduced.put(cell, FLAGGED);
                        cellToRegionIndex.put(cell, regionIdx);
                        // 難易度を記録（最小レベルを採用）
                        int level = Math.max(LV_1, region.getOriginLevel());
                        if (difficultyMap[cell] == LV_UNSOLVED) {
                            difficultyMap[cell] = level;
                        }
                    }
                }
            }
            // mines == 0 → 全安全
            else if (region.getMines() == 0) {
                for (int cell : region.getCells()) {
                    if (!deduced.containsKey(cell) && currentBoard[cell] == MINE) {
                        deduced.put(cell, SAFE);
                        cellToRegionIndex.put(cell, regionIdx);
                        // 難易度を記録（最小レベルを採用）
                        int level = Math.max(LV_1, region.getOriginLevel());
                        if (difficultyMap[cell] == LV_UNSOLVED) {
                            difficultyMap[cell] = level;
                        }
                    }
                }
            }
        }

        return deduced;
    }

    // =========================================================================
    // Phase 3: 更新フェーズ
    // =========================================================================

    /**
     * 確定したセルを盤面に反映
     * 設計書「3.3. 更新フェーズ」に準拠
     */
    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            // 使用したRegion番号を取得
            Integer regionIdx = cellToRegionIndex.get(cellIdx);
            String regionInfo = (regionIdx != null) ? " using Region[" + regionIdx + "]" : "";

            // SAFEの場合は正解盤面から実際の数字を取得
            String displayVal;
            if (val == SAFE) {
                val = completeBoard[cellIdx];
                displayVal = "SAFE";
            } else {
                displayVal = "MINE";
            }

            currentBoard[cellIdx] = val;

            System.out.println("  Applied: Cell " + cellIdx + " = " + displayVal +
                    " (Level " + difficultyMap[cellIdx] + ")" + regionInfo);
        }
    }

    // =========================================================================
    // ユーティリティメソッド
    // =========================================================================

    /**
     * 周囲8セルの座標を取得
     */
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

    /**
     * 全てのセルが解決済みか判定
     */
    private boolean isAllSolved() {
        for (int val : currentBoard) {
            if (val == MINE)
                return false;
        }
        return true;
    }

    /**
     * Regionプールの内容を表示（デバッグ用）
     */
    private void printRegionPool() {
        if (regionPool.isEmpty()) {
            System.out.println("  (Pool is empty)");
            return;
        }

        List<Region> sorted = new ArrayList<>(regionPool.values());
        sorted.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (int i = 0; i < sorted.size(); i++) {
            Region r = sorted.get(i);
            System.out.println("  [" + i + "]: " + r.toString());
        }
    }
}