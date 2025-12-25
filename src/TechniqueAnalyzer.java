import java.util.*;

/**
 * 盤面全体の状態を管理し , 推論のステップを進めるクラス.
 * 「Region事前全列挙モデル」に基づき実装.
 * * ★修正版仕様:
 * 1. Lv2/Lv3のRegionは「初期盤面」からのみ生成し , 以降は新規生成しない.
 * 2. ラウンド進行時は , 既存のLv2/Lv3 Regionをメンテナンス(確定セルの除去)して維持する.
 * 3. Lv1 Regionのみ , 毎ラウンド最新の盤面から再生成する.
 * 4. AnalysisLogger によるCSV出力機能を追加.
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
    public static final int LV_1 = 1; // 埋めるだけ (Base Hint)
    public static final int LV_2 = 2; // 包含 (Subset)
    public static final int LV_3 = 3; // 共通 (Intersection)
    public static final int LV_4 = 4; // 背理法 + Lv1 (Contradiction + Base)
    public static final int LV_5 = 5; // 背理法 + Lv2 (Contradiction + Subset)
    public static final int LV_6 = 6; // 背理法 + Lv3 (Contradiction + Intersection)

    // =========================================================================
    // フィールド
    // =========================================================================
    private int[] board;
    private final int[] completeBoard;
    private final int[] difficultyMap;
    private final int size;

    // 生成されたすべてのRegionを保持するプール
    private Map<Set<Integer>, Region> regionPool;

    // RegionIDカウンタ
    private int regionIdCounter = 0;

    // Lv2/Lv3のRegionを生成済みかどうか
    private boolean isDerivedRegionsGenerated = false;

    // ★追加: ログ記録用
    private AnalysisLogger logger;
    private int currentRound = 0;

    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
        this.regionPool = new HashMap<>();
        this.logger = new AnalysisLogger(); // ロガー初期化
    }

    /**
     * 解析のメインループ.
     */
    public void analyze() {
        // 初期ヒントは難易度0
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0)
                difficultyMap[i] = 0;
        }

        boolean changed = true;
        currentRound = 1;

        while (changed) {
            changed = false;
            System.out.println("\n--- Round " + currentRound + " Start ---");

            // ロガーにラウンド開始を通知
            logger.startNewRound();

            printCurrentBoard("Start of Round " + currentRound);

            // 1. Regionの生成とメンテナンス
            // Lv1は全再生成 , Lv2/Lv3は初回のみ生成し以降は維持・更新
            regionPool = updateAndGenerateRegions(board, regionPool, !isDerivedRegionsGenerated);
            if (!isDerivedRegionsGenerated) {
                isDerivedRegionsGenerated = true;
            }

            // デバッグ出力
            printRegionPool();

            // 2. ソルビング (レベル順に試す: Lv1 → Lv2 → Lv3)
            Map<Integer, Integer> deduced = solveFromPool(board, regionPool);

            if (!deduced.isEmpty()) {
                System.out.println("Round " + currentRound + ": Found " + deduced.size() + " cells (Lv1-3).");
                applyResult(deduced);
                // 盤面が変わったので , 次のラウンドへ
                changed = true;
                currentRound++;
            } else {
                // ★追加: Lv1~Lv3で確定できなかった場合、Lv4-6（背理法）を試す
                System.out.println("Round " + currentRound + ": No cells solved by Lv1-3. Trying Lv4-6...");
                int[] lv4Result = solveLv4(); // [セルインデックス, 値, 難易度レベル] or null

                if (lv4Result != null) {
                    int cellIdx = lv4Result[0];
                    int value = lv4Result[1];
                    int level = lv4Result[2];

                    System.out.println("Round " + currentRound + ": Found 1 cell (Lv" + level + ").");

                    // 盤面に適用
                    board[cellIdx] = value;

                    // 難易度を記録
                    if (difficultyMap[cellIdx] == LV_UNSOLVED) {
                        difficultyMap[cellIdx] = level;
                    }

                    // 盤面が変わったので次のラウンドへ（Lv1から再試行）
                    changed = true;
                    currentRound++;
                } else {
                    System.out.println("Round " + currentRound + ": No cells solved by Lv4-6 either.");
                }
            }
        }
    }

    /**
     * 解析ログをCSVに出力する
     */
    public void exportLogToCSV(String filename) {
        logger.exportToCSV(filename);
    }

    /**
     * Regionプールの更新と新規生成を行う
     * 
     * @param targetBoard     対象の盤面
     * @param targetPool      対象のRegionプール
     * @param generateDerived Lv2/Lv3のRegionを生成するかどうか
     * @return 更新後のRegionプール
     */
    private Map<Set<Integer>, Region> updateAndGenerateRegions(
            int[] targetBoard,
            Map<Set<Integer>, Region> targetPool,
            boolean generateDerived) {

        // 1. 既存プールのメンテナンス (Lv2, Lv3の更新)
        Map<Set<Integer>, Region> nextPool = new HashMap<>();

        for (Region r : targetPool.values()) {
            if (r.getOriginLevel() == LV_1)
                continue;

            Region updated = updateRegionState(targetBoard, r);

            if (updated != null && !updated.getCells().isEmpty()) {
                nextPool.put(updated.getCells(), updated);
            }
        }

        // 2. Lv1: Base Regions の完全再生成 (毎回実行)
        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < targetBoard.length; i++) {
            if (targetBoard[i] >= 0) {
                Region r = createRegionFromHint(targetBoard, i);
                if (r != null) {
                    baseRegions.add(r);
                    addToPool(nextPool, r);
                }
            }
        }

        // 3. Lv2 & Lv3 の新規生成
        if (generateDerived) {
            for (int i = 0; i < baseRegions.size(); i++) {
                for (int j = i + 1; j < baseRegions.size(); j++) {
                    Region rA = baseRegions.get(i);
                    Region rB = baseRegions.get(j);

                    // --- 包含判定 (Lv2) ---
                    if (rA.isSubsetOf(rB)) {
                        Region diff = rB.subtract(rA, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(nextPool, diff);
                    } else if (rB.isSubsetOf(rA)) {
                        Region diff = rA.subtract(rB, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(nextPool, diff);
                    }
                    // --- 共通判定 (Lv3) ---
                    else {
                        Set<Region> intersections = rA.intersect(rB, LV_3);
                        for (Region r : intersections) {
                            addToPool(nextPool, r);
                        }
                    }
                }
            }
        }

        reassignIds(nextPool);
        return nextPool;
    }

    /**
     * Regionの状態を指定盤面に合わせる（確定セルの除去）
     */
    private Region updateRegionState(int[] targetBoard, Region original) {
        Set<Integer> currentCells = new HashSet<>();
        int currentMines = original.getMines();

        for (int cell : original.getCells()) {
            int val = targetBoard[cell];
            if (val == MINE) {
                currentCells.add(cell);
            } else if (val == FLAGGED) {
                currentMines--;
            }
        }

        if (currentMines < 0)
            return null;

        if (currentCells.size() == original.getCells().size() && currentMines == original.getMines()) {
            return original;
        }

        Region updated = new Region(currentCells, currentMines, original.getOriginLevel());
        updated.addSourceHints(parseSourceHints(original.getSourceHintsString()));

        return updated;
    }

    private Set<Integer> parseSourceHints(String str) {
        Set<Integer> hints = new HashSet<>();
        if (str.equals("Derived") || str.isEmpty())
            return hints;
        String[] parts = str.split(",");
        for (String p : parts) {
            try {
                hints.add(Integer.parseInt(p));
            } catch (Exception e) {
            }
        }
        return hints;
    }

    /**
     * Regionを指定プールに追加する.
     */
    private void addToPool(Map<Set<Integer>, Region> targetPool, Region newRegion) {
        Set<Integer> key = newRegion.getCells();
        if (targetPool.containsKey(key)) {
            Region existing = targetPool.get(key);
            if (newRegion.getOriginLevel() < existing.getOriginLevel()) {
                targetPool.put(key, newRegion);
            }
        } else {
            targetPool.put(key, newRegion);
        }
    }

    private void reassignIds(Map<Set<Integer>, Region> targetPool) {
        int id = 0;
        List<Region> list = new ArrayList<>(targetPool.values());
        list.sort(Comparator.comparingInt(Region::getOriginLevel)
                .thenComparingInt(Region::hashCode));

        for (Region r : list) {
            r.setId(++id);
        }
    }

    /**
     * プールされたRegionを使って確定できるセルを探す（本体用）
     */
    private Map<Integer, Integer> solveFromPool(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        Map<Integer, Integer> deduced = new HashMap<>();
        Map<Integer, Integer> deducedLevel = new HashMap<>();

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : sortedRegions) {
            if (!deduced.isEmpty()) {
                boolean hasLv1Deduction = false;
                for (int level : deducedLevel.values()) {
                    if (level == LV_1) {
                        hasLv1Deduction = true;
                        break;
                    }
                }
                if (r.getOriginLevel() > LV_1 && hasLv1Deduction) {
                    updateDifficultyMap(deducedLevel);
                    return deduced;
                }
            }

            boolean determined = false;
            int valToSet = -99;

            if (r.getMines() == r.size()) {
                determined = true;
                valToSet = FLAGGED;
            } else if (r.getMines() == 0) {
                determined = true;
                valToSet = SAFE;
            }

            if (determined) {
                int complexity = Math.max(LV_1, r.getOriginLevel());

                for (int cell : r.getCells()) {
                    if (!deduced.containsKey(cell)) {
                        deduced.put(cell, valToSet);
                        deducedLevel.put(cell, complexity);

                        String type = (valToSet == FLAGGED) ? "MINE" : "SAFE";
                        System.out.println("  -> Solved: Cell " + cell + " is " + type +
                                " (via Region #" + r.getId() + ": " + r + " [Source: " + r.getSourceHintsString()
                                + "])");

                        // ★追加: ログ記録
                        logger.logStep(currentRound, cell, type, complexity,
                                r.getId(), r.toLogString(), r.getSourceHintsString());
                    } else {
                        if (complexity < deducedLevel.get(cell)) {
                            deducedLevel.put(cell, complexity);
                            // ログの更新が必要ならここで行うが , 今回は初回の確定を優先する
                        }
                    }
                }
            }
        }

        updateDifficultyMap(deducedLevel);

        return deduced;
    }

    /**
     * 難易度マップの更新を行うヘルパーメソッド
     */
    private void updateDifficultyMap(Map<Integer, Integer> deducedLevel) {
        for (Map.Entry<Integer, Integer> entry : deducedLevel.entrySet()) {
            int cell = entry.getKey();
            int lvl = entry.getValue();
            if (difficultyMap[cell] == LV_UNSOLVED) {
                difficultyMap[cell] = lvl;
            }
        }
    }

    /**
     * 推論結果を盤面に適用する.
     */
    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();
            board[cellIdx] = val;
        }
    }

    private Region createRegionFromHint(int[] targetBoard, int hintIdx) {
        if (targetBoard[hintIdx] < 0)
            return null;

        int hintVal = targetBoard[hintIdx];
        List<Integer> neighbors = getNeighbors(hintIdx);
        Set<Integer> unknownCells = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : neighbors) {
            int val = targetBoard[nb];
            if (val == MINE) {
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                flaggedCount++;
            }
        }

        if (unknownCells.isEmpty())
            return null;
        int remainingMines = hintVal - flaggedCount;

        Region r = new Region(unknownCells, remainingMines, LV_1);
        r.addSourceHint(hintIdx);
        return r;
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
        System.out.println("--- Region Pool (" + regionPool.size() + ") ---");
        List<Region> sortedPool = new ArrayList<>(regionPool.values());
        sortedPool.sort(Comparator.comparingInt(Region::getOriginLevel));
        for (Region r : sortedPool) {
            System.out.println("  #" + r.getId() + ": " + r + " [Source: " + r.getSourceHintsString() + "]");
        }
    }

    public void printCurrentBoard(String label) {
        System.out.println("--- Board State [" + label + "] ---");
        for (int i = 0; i < board.length; i++) {
            int val = board[i];
            if (val == MINE) {
                System.out.print(" ? ");
            } else if (val == FLAGGED) {
                System.out.print(" F ");
            } else if (val == SAFE) {
                System.out.print(" S ");
            } else if (val == IGNORE) {
                System.out.print(" - ");
            } else {
                System.out.printf(" %d ", val);
            }

            if ((i + 1) % size == 0) {
                System.out.println();
            }
        }
        System.out.println("-----------------------------------");
    }

    /**
     * Lv4-6 (背理法) による推論
     * 未確定セルに対して逆の値を仮置きし、Lv1-3推論で矛盾が発生するか検証する
     * 
     * @return [セルインデックス, 値(FLAGGED/SAFE), 難易度レベル(4-6)] or null（確定できなかった場合）
     */
    private int[] solveLv4() {
        // 未確定セルを収集
        List<Integer> unknownCells = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == MINE) {
                unknownCells.add(i);
            }
        }

        System.out.println("  [Lv4-6] Testing " + unknownCells.size() + " unknown cells...");

        // 各未確定セルについて背理法を試す
        for (int cellIdx : unknownCells) {
            // 正解を確認
            int correctValue = completeBoard[cellIdx];
            boolean isMine = (correctValue == MINE || correctValue == FLAGGED);

            // 逆の値を決定（正解がMINEならSAFE、正解がSAFEならFLAGGED）
            int wrongValue;
            String wrongValueStr;
            if (isMine) {
                wrongValue = SAFE;
                wrongValueStr = "SAFE";
            } else {
                wrongValue = FLAGGED;
                wrongValueStr = "MINE";
            }

            System.out.println("  [Lv4-6] Trying cell " + cellIdx + ": assuming " + wrongValueStr + "...");

            // 一時盤面を作成し仮置き
            int[] tempBoard = Arrays.copyOf(board, board.length);
            tempBoard[cellIdx] = wrongValue;

            // 矛盾判定（Lv1-3で推論し、矛盾検出レベルを返す）
            int contradictionLevel = testContradiction(tempBoard);

            if (contradictionLevel > 0) {
                // 矛盾あり → このセルは正解で確定
                int confirmedValue;
                String type;
                String assumedType;

                if (isMine) {
                    confirmedValue = FLAGGED;
                    type = "MINE";
                    assumedType = "SAFE";
                } else {
                    confirmedValue = SAFE;
                    type = "SAFE";
                    assumedType = "MINE";
                }

                // 難易度レベル: 仮置き + Lv1 → Lv4, 仮置き + Lv2 → Lv5, 仮置き + Lv3 → Lv6
                int finalLevel = contradictionLevel + 3;

                System.out.println("  -> [Lv" + finalLevel + "] Cell " + cellIdx + " is " + type +
                        " (contradiction at Lv" + contradictionLevel + ")");

                // ログ記録
                logger.logStep(currentRound, cellIdx, type, finalLevel, -1,
                        "Lv" + finalLevel + "-Contradiction", "Assumed:" + assumedType);

                // 1セル確定したらすぐにreturn（Lv1に戻るため）
                return new int[] { cellIdx, confirmedValue, finalLevel };
            } else {
                System.out.println("  [Lv4-6] Cell " + cellIdx + ": no contradiction, skipping.");
            }
        }

        return null; // 全セル試しても確定できなかった
    }

    /**
     * 仮置き後のLv1-3推論で矛盾が発生するか検証する
     * 
     * @param tempBoard 仮置き済みの一時盤面
     * @return 矛盾検出レベル（1-3）, -1: 矛盾なし
     */
    private int testContradiction(int[] tempBoard) {
        // 一時盤面用のRegionPoolを作成
        Map<Set<Integer>, Region> tempPool = new HashMap<>();

        int iteration = 0;
        int maxLevelUsed = 0;

        while (true) {
            iteration++;

            // Regionを生成（毎回Lv2/3も生成）
            tempPool = updateAndGenerateRegions(tempBoard, tempPool, true);

            // 矛盾チェック: Regionの mines < 0 または mines > cells.size()
            int contradictionLevel = checkContradiction(tempPool);
            if (contradictionLevel > 0) {
                System.out
                        .println("    [Contradiction] Found at Lv" + contradictionLevel + " in iteration " + iteration);
                return Math.max(maxLevelUsed, contradictionLevel);
            }

            // 確定処理
            SolveResult result = solveFromPoolForContradiction(tempBoard, tempPool);

            if (result.solvedCount > 0) {
                System.out.println("    [Lv4-Lv" + result.maxLevel + "] Iteration " + iteration +
                        ": solved " + result.solvedCount + " cells");
                maxLevelUsed = Math.max(maxLevelUsed, result.maxLevel);
            } else {
                // 何も確定できなければ終了
                break;
            }
        }

        return -1; // 矛盾なし
    }

    /**
     * RegionPoolに矛盾がないかチェックする
     * 
     * @return 矛盾があるRegionの最小レベル, なければ-1
     */
    private int checkContradiction(Map<Set<Integer>, Region> targetPool) {
        for (Region r : targetPool.values()) {
            if (r.getMines() < 0) {
                return r.getOriginLevel();
            }
            if (r.getMines() > r.size()) {
                return r.getOriginLevel();
            }
        }
        return -1;
    }

    /**
     * 矛盾検出用のsolveFromPool（ログ出力なし、盤面直接更新）
     */
    private SolveResult solveFromPoolForContradiction(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        int solvedCount = 0;
        int maxLevel = 0;

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : sortedRegions) {
            boolean determined = false;
            int valToSet = -99;

            if (r.getMines() == r.size()) {
                determined = true;
                valToSet = FLAGGED;
            } else if (r.getMines() == 0) {
                determined = true;
                valToSet = SAFE;
            }

            if (determined) {
                int level = Math.max(LV_1, r.getOriginLevel());

                for (int cell : r.getCells()) {
                    if (targetBoard[cell] == MINE) {
                        targetBoard[cell] = valToSet;
                        solvedCount++;
                        maxLevel = Math.max(maxLevel, level);
                    }
                }
            }
        }

        return new SolveResult(solvedCount, maxLevel);
    }

    /**
     * solveFromPoolForContradictionの結果を保持するクラス
     */
    private static class SolveResult {
        final int solvedCount;
        final int maxLevel;

        SolveResult(int solvedCount, int maxLevel) {
            this.solvedCount = solvedCount;
            this.maxLevel = maxLevel;
        }
    }
}