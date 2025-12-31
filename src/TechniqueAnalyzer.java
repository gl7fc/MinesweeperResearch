import java.util.*;

/**
 * 盤面全体の状態を管理し , 推論のステップを進めるクラス.
 * 「Region事前全列挙モデル」に基づき実装.
 * 
 * ★修正版仕様:
 * 1. Lv2/Lv3のRegionは「初期盤面」からのみ生成し , 以降は新規生成しない.
 * 2. ラウンド進行時は , 既存のLv2/Lv3 Regionをメンテナンス(確定セルの除去)して維持する.
 * 3. Lv1 Regionのみ , 毎ラウンド最新の盤面から再生成する.
 * 4. AnalysisLogger によるCSV出力機能を追加.
 * 5. ★親子関係追跡: 1つのRegionから確定が出たら即return、triggerCellsを正確に記録
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

    // ★追加: 直前のラウンドで確定したセル（triggerCells計算用）
    private Set<Integer> lastConfirmedCells = new HashSet<>();

    // ★追加: 各セルの推論深度（依存するセル確定の連鎖の深さ）
    private Map<Integer, Integer> cellDepthMap = new HashMap<>();

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
     * ★修正: 推論結果を保持するクラス
     * 同一レベルの複数Regionからの確定を保持できるように変更
     */
    private static class DeductionResult {
        Map<Integer, Integer> deduced; // セル → 値(FLAGGED/SAFE)
        Map<Integer, Region> cellToSourceRegion; // セル → 確定元Region
        int level; // 確定したレベル

        DeductionResult() {
            this.deduced = new HashMap<>();
            this.cellToSourceRegion = new HashMap<>();
            this.level = 0;
        }

        boolean isEmpty() {
            return deduced.isEmpty();
        }
    }

    /**
     * 解析のメインループ.
     * ★修正: 1つのRegionから確定が出たら即座に次ラウンドへ
     */
    public void analyze() {
        // 初期ヒントは難易度0、推論深度も0
        // ★追加: 初期ヒントをログに出力（Round 0として）
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) {
                difficultyMap[i] = 0;
                cellDepthMap.put(i, 0);

                // ★追加: ログに記録
                logger.logInitialHint(i, board[i]);
            }
        }

        boolean changed = true;
        currentRound = 1;
        lastConfirmedCells = Collections.emptySet(); // 初回は空

        while (changed) {
            changed = false;
            System.out.println("\n--- Round " + currentRound + " Start ---");

            // ロガーにラウンド開始を通知
            logger.startNewRound();

            printCurrentBoard("Start of Round " + currentRound);

            // 1. Regionの生成とメンテナンス
            // ★修正: lastConfirmedCellsを渡してtriggerCellsを計算
            regionPool = updateAndGenerateRegions(board, regionPool, !isDerivedRegionsGenerated, lastConfirmedCells);
            if (!isDerivedRegionsGenerated) {
                isDerivedRegionsGenerated = true;
            }

            // デバッグ出力
            // printRegionPool();

            // 2. ソルビング (レベル順に試す: Lv1 → Lv2 → Lv3)
            // ★修正: 同一レベルの全Regionから確定を収集
            DeductionResult result = solveFromPool(board, regionPool);

            if (!result.isEmpty()) {
                System.out.println("Round " + currentRound + ": Found " + result.deduced.size() +
                        " cells (Lv" + result.level + ").");

                // ★ログ記録（親子関係情報付き）
                logDeduction(result);

                // 難易度マップ更新（セルごとのsourceRegionから取得）
                for (int cellIdx : result.deduced.keySet()) {
                    if (difficultyMap[cellIdx] == LV_UNSOLVED) {
                        Region sourceRegion = result.cellToSourceRegion.get(cellIdx);
                        int level = Math.max(LV_1, sourceRegion.getOriginLevel());
                        difficultyMap[cellIdx] = level;
                    }
                }

                // ★確定セルを記録（次ラウンドのtriggerCells計算用）
                lastConfirmedCells = result.deduced.keySet();

                // 盤面に反映
                applyResult(result.deduced);

                // 盤面が変わったので次のラウンドへ
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

                    // ★追加: Lv4-6の推論深度を計算（周囲の確定セルのdepthの最大値+1）
                    int depth = calculateDepthForLv4(cellIdx);
                    cellDepthMap.put(cellIdx, depth);

                    // 盤面に適用
                    board[cellIdx] = value;

                    // 難易度を記録
                    if (difficultyMap[cellIdx] == LV_UNSOLVED) {
                        difficultyMap[cellIdx] = level;
                    }

                    // ★確定セルを記録（次ラウンドのtriggerCells計算用）
                    lastConfirmedCells = Collections.singleton(cellIdx);

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
     * ★追加: Lv4-6で確定したセルの推論深度を計算
     * 周囲の確定済みセル（depth > 0）のdepthの最大値+1
     * 周囲に確定セルがない場合はdepth = 1
     */
    private int calculateDepthForLv4(int cellIdx) {
        List<Integer> neighbors = getNeighbors(cellIdx);
        int maxNeighborDepth = 0;

        for (int nb : neighbors) {
            if (cellDepthMap.containsKey(nb)) {
                int nbDepth = cellDepthMap.get(nb);
                if (nbDepth > 0) { // 初期ヒント（depth=0）以外
                    maxNeighborDepth = Math.max(maxNeighborDepth, nbDepth);
                }
            }
        }

        return maxNeighborDepth + 1;
    }

    /**
     * ★追加: Lv4-6で確定したセルのtriggerCellsを文字列として取得
     * 周囲の確定済みセル（depth > 0）をカンマ区切りで返す
     */
    private String getTriggerCellsForLv4(int cellIdx) {
        List<Integer> neighbors = getNeighbors(cellIdx);
        List<Integer> triggers = new ArrayList<>();

        for (int nb : neighbors) {
            if (cellDepthMap.containsKey(nb) && cellDepthMap.get(nb) > 0) {
                triggers.add(nb);
            }
        }

        Collections.sort(triggers);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < triggers.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(triggers.get(i));
        }
        return sb.toString();
    }

    /**
     * ★修正: 推論結果をログに記録（親子関係情報付き）
     * 同一レベルで確定した全セルを一括処理し、互いにtriggerにならないようにする
     */
    private void logDeduction(DeductionResult result) {
        // ★まず全セルのdepthを計算（cellDepthMapに追加せず）
        Map<Integer, Integer> newDepths = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : result.deduced.entrySet()) {
            int cell = entry.getKey();
            Region r = result.cellToSourceRegion.get(cell);
            int depth = calculateDepth(r);
            newDepths.put(cell, depth);
        }

        // ★ログ出力と一括でcellDepthMapに追加
        for (Map.Entry<Integer, Integer> entry : result.deduced.entrySet()) {
            int cell = entry.getKey();
            int val = entry.getValue();
            Region r = result.cellToSourceRegion.get(cell);
            int level = Math.max(LV_1, r.getOriginLevel());
            int depth = newDepths.get(cell);
            String type = (val == FLAGGED) ? "MINE" : "SAFE";

            System.out.println("  -> Solved: Cell " + cell + " is " + type +
                    " (via Region #" + r.getId() + ": " + r +
                    " [Source: " + r.getSourceHintsString() + "]" +
                    " [Trigger: " + r.getTriggerCellsString() + "]" +
                    " [Parent: " + r.getParentRegionSnapshot() + "]" +
                    " [Depth: " + depth + "])");

            // ログ記録（親子関係情報付き）
            logger.logStep(currentRound, cell, type, level,
                    r.getId(), r.toLogString(), r.getSourceHintsString(),
                    r.getTriggerCellsString(), r.getParentRegionSnapshot(), depth);
        }

        // ★一括でcellDepthMapに追加（ログ出力後）
        cellDepthMap.putAll(newDepths);
    }

    /**
     * ★追加: RegionのtriggerCellsから推論深度を計算
     * depth = max(triggerCellsのdepth) + 1
     * triggerCellsが空の場合はdepth = 1（初期ヒントから直接導出）
     */
    private int calculateDepth(Region r) {
        Set<Integer> triggers = r.getTriggerCells();

        if (triggers.isEmpty()) {
            // triggerがない = 初期ヒントから直接導出
            return 1;
        }

        int maxTriggerDepth = 0;
        for (int trigger : triggers) {
            int triggerDepth = cellDepthMap.getOrDefault(trigger, 0);
            maxTriggerDepth = Math.max(maxTriggerDepth, triggerDepth);
        }

        return maxTriggerDepth + 1;
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
     * @param confirmedCells  ★追加: 今回確定したセル（triggerCells計算用）
     * @return 更新後のRegionプール
     */
    private Map<Set<Integer>, Region> updateAndGenerateRegions(
            int[] targetBoard,
            Map<Set<Integer>, Region> targetPool,
            boolean generateDerived,
            Set<Integer> confirmedCells) {

        // 1. 既存プールのメンテナンス (Lv2, Lv3の更新)
        Map<Set<Integer>, Region> nextPool = new HashMap<>();

        for (Region r : targetPool.values()) {
            if (r.getOriginLevel() == LV_1)
                continue;

            // ★修正: confirmedCellsを渡す
            Region updated = updateRegionState(targetBoard, r, confirmedCells);

            if (updated != null && !updated.getCells().isEmpty()) {
                nextPool.put(updated.getCells(), updated);
            }
        }

        // 2. Lv1: Base Regions の完全再生成 (毎回実行)
        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < targetBoard.length; i++) {
            if (targetBoard[i] >= 0) {
                // ★修正: confirmedCellsを渡す
                Region r = createRegionFromHint(targetBoard, i, confirmedCells);
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
     * ★修正: confirmedCellsを受け取り、triggerCellsを計算
     * 矛盾があってもRegionを返す（checkContradictionで検出するため）
     */
    private Region updateRegionState(int[] targetBoard, Region original, Set<Integer> confirmedCells) {
        Set<Integer> currentCells = new HashSet<>();
        Set<Integer> triggerCells = new HashSet<>(); // ★追加
        int currentMines = original.getMines();

        for (int cell : original.getCells()) {
            int val = targetBoard[cell];
            if (val == MINE) {
                currentCells.add(cell); // まだ未確定 → 残す
            } else if (val == FLAGGED) {
                currentMines--;
                // ★追加: 今回の確定セルかどうかチェック
                if (confirmedCells.contains(cell)) {
                    triggerCells.add(cell);
                }
            } else if (val == SAFE) {
                // ★追加: 今回の確定セルかどうかチェック
                if (confirmedCells.contains(cell)) {
                    triggerCells.add(cell);
                }
            }
        }

        // 矛盾があってもnullを返さない（checkContradictionで検出する）
        // if (currentMines < 0)
        // return null;

        // ★変化がなければ元のRegionをそのまま返す
        if (triggerCells.isEmpty() &&
                currentCells.size() == original.getCells().size() &&
                currentMines == original.getMines()) {
            return original;
        }

        // ★新しいRegionを作成（triggerCells情報付き）
        String parentSnapshot = original.toLogString();
        int newDepth = original.getGenerationDepth() + 1;

        Region updated = new Region(
                currentCells, currentMines, original.getOriginLevel(),
                triggerCells, parentSnapshot, newDepth);
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
     * プールされたRegionを使って確定できるセルを探す
     * ★修正: 同一レベルの全Regionを評価し、確定可能なセルを一括で収集
     * Lv1で確定できるセルを全て収集してからreturn
     * Lv1で何も確定できなければLv2へ、という流れ
     */
    private DeductionResult solveFromPool(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        DeductionResult result = new DeductionResult();

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        int currentLevel = -1;

        for (Region r : sortedRegions) {
            int regionLevel = r.getOriginLevel();

            // レベルが変わった場合、前のレベルで確定があればreturn
            if (currentLevel != -1 && regionLevel > currentLevel && !result.isEmpty()) {
                result.level = currentLevel;
                return result;
            }

            currentLevel = regionLevel;

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
                // ★このRegionの全セルを確定として記録
                for (int cell : r.getCells()) {
                    // 既に確定済みでないことを確認
                    if (targetBoard[cell] == MINE) {
                        // まだ登録されていなければ追加
                        if (!result.deduced.containsKey(cell)) {
                            result.deduced.put(cell, valToSet);
                            result.cellToSourceRegion.put(cell, r);
                        }
                    }
                }
            }
        }

        // 最後のレベルで確定があればlevelを設定
        if (!result.isEmpty()) {
            result.level = currentLevel;
        }

        return result;
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

    /**
     * ヒントセルからLv1 Regionを作成
     * ★修正: 直前のラウンドで確定したセル（confirmedCells）のみをtriggerCellsとして記録
     */
    private Region createRegionFromHint(int[] targetBoard, int hintIdx, Set<Integer> confirmedCells) {
        if (targetBoard[hintIdx] < 0)
            return null;

        int hintVal = targetBoard[hintIdx];
        List<Integer> neighbors = getNeighbors(hintIdx);
        Set<Integer> unknownCells = new HashSet<>();
        Set<Integer> triggerCells = new HashSet<>(); // ★直前ラウンドで確定したセル
        int flaggedCount = 0;

        for (int nb : neighbors) {
            int val = targetBoard[nb];
            if (val == MINE) {
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                flaggedCount++;
                // ★修正: 直前のラウンドで確定したセルのみtriggerに追加
                if (confirmedCells.contains(nb)) {
                    triggerCells.add(nb);
                }
            } else if (val == SAFE) {
                // ★修正: 直前のラウンドで確定したセルのみtriggerに追加
                if (confirmedCells.contains(nb)) {
                    triggerCells.add(nb);
                }
            }
        }

        if (unknownCells.isEmpty())
            return null;
        int remainingMines = hintVal - flaggedCount;

        Region r;
        // ★triggerCellsがあれば拡張コンストラクタを使用
        if (!triggerCells.isEmpty()) {
            r = new Region(unknownCells, remainingMines, LV_1,
                    triggerCells, "", 0); // depthは後でcalculateDepthで計算
        } else {
            r = new Region(unknownCells, remainingMines, LV_1);
        }
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
            // ★親子関係情報も出力
            System.out.println("  #" + r.getId() + ": " + r.toDetailedString() +
                    " [Source: " + r.getSourceHintsString() + "]");
        }
    }

    public void printCurrentBoard(String label) {
        System.out.println("--- Board State [" + label + "] ---");
        for (int i = 0; i < board.length; i++) {
            int val = board[i];
            if (val == MINE) {
                System.out.print(" . ");
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

                // ★追加: 推論深度を計算
                int depth = calculateDepthForLv4(cellIdx);

                // ★追加: 周囲の確定セルをtriggerCellsとして取得
                String triggerCellsStr = getTriggerCellsForLv4(cellIdx);

                System.out.println("  -> [Lv" + finalLevel + "] Cell " + cellIdx + " is " + type +
                        " (contradiction at Lv" + contradictionLevel + ")" +
                        " [Trigger: " + triggerCellsStr + "]" +
                        " [Depth: " + depth + "]");

                // ログ記録（親子関係情報付き）
                logger.logStep(currentRound, cellIdx, type, finalLevel, -1,
                        "Lv" + finalLevel + "-Contradiction", "Assumed:" + assumedType,
                        triggerCellsStr, "", depth);

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

            // Regionを生成（毎回Lv2/3も生成、confirmedCellsは空）
            tempPool = updateAndGenerateRegions(tempBoard, tempPool, true, Collections.emptySet());

            // 矛盾チェック（盤面とRegionPoolの両方）
            int contradictionLevel = checkContradiction(tempBoard, tempPool);
            if (contradictionLevel > 0) {
                int level = Math.max(maxLevelUsed, contradictionLevel);
                System.out.println("    [Contradiction] Found in iteration " + iteration + " (Level " + level + ")");
                return level;
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
     * 盤面とRegionPoolに矛盾がないかチェックする
     * 
     * @param targetBoard チェック対象の盤面
     * @param targetPool  チェック対象のRegionPool
     * @return 矛盾があるレベル（1-3）, なければ-1
     */
    private int checkContradiction(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        // 1. RegionPoolの矛盾チェック
        for (Region r : targetPool.values()) {
            if (r.getMines() < 0) {
                System.out.println("    [Contradiction] Region " + r + ": mines=" + r.getMines() + " < 0");
                return r.getOriginLevel();
            }
            if (r.getMines() > r.size()) {
                System.out
                        .println("    [Contradiction] Region " + r + ": mines=" + r.getMines() + " > size=" + r.size());
                return r.getOriginLevel();
            }
        }

        // 2. 盤面のヒントセルを直接チェック（Regionが作られない場合の矛盾を検出）
        for (int i = 0; i < targetBoard.length; i++) {
            if (targetBoard[i] < 0) {
                continue; // ヒントセル以外はスキップ
            }

            int hintValue = targetBoard[i];
            List<Integer> neighbors = getNeighbors(i);

            int unknownCount = 0;
            int flaggedCount = 0;

            for (int nb : neighbors) {
                int val = targetBoard[nb];
                if (val == MINE) {
                    unknownCount++;
                } else if (val == FLAGGED) {
                    flaggedCount++;
                }
            }

            int remainingMines = hintValue - flaggedCount;

            // 矛盾1: フラグが多すぎる
            if (remainingMines < 0) {
                System.out.println("    [Contradiction] Hint " + i + ": remainingMines=" + remainingMines
                        + " < 0 (too many flags)");
                return LV_1;
            }
            // 矛盾2: 地雷を置く場所が足りない
            if (remainingMines > unknownCount) {
                System.out.println("    [Contradiction] Hint " + i + ": remainingMines=" + remainingMines
                        + " > unknownCount=" + unknownCount + " (not enough space)");
                return LV_1;
            }
        }

        return -1;
    }

    /**
     * 矛盾検出用のsolveFromPool（ログ出力なし、盤面直接更新）
     * Lv1で確定したらLv2以上は試さない（本体と同じロジック）
     */
    private SolveResult solveFromPoolForContradiction(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        int solvedCount = 0;
        int maxLevel = 0;
        Map<Integer, Integer> deducedLevel = new HashMap<>();

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : sortedRegions) {
            // Lv1で確定があればLv2以上は試さない
            if (solvedCount > 0) {
                boolean hasLv1Deduction = false;
                for (int level : deducedLevel.values()) {
                    if (level == LV_1) {
                        hasLv1Deduction = true;
                        break;
                    }
                }
                if (r.getOriginLevel() > LV_1 && hasLv1Deduction) {
                    return new SolveResult(solvedCount, maxLevel);
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
                int level = Math.max(LV_1, r.getOriginLevel());

                for (int cell : r.getCells()) {
                    if (targetBoard[cell] == MINE) {
                        targetBoard[cell] = valToSet;
                        solvedCount++;
                        maxLevel = Math.max(maxLevel, level);
                        deducedLevel.put(cell, level);
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