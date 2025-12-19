import java.util.*;
import java.util.stream.Collectors;

/**
 * 盤面全体の状態を管理し , 推論のステップを進めるクラス.
 * 「Region事前全列挙モデル」に基づき実装.
 * ★修正版仕様:
 * 1. Lv2/Lv3のRegionは「初期盤面」からのみ生成し , 以降は新規生成しない.
 * 2. ラウンド進行時は , 既存のLv2/Lv3 Regionをメンテナンス(確定セルの除去)して維持する.
 * 3. Lv1 Regionのみ , 毎ラウンド最新の盤面から再生成する.
 * 4. AnalysisLogger によるCSV出力機能を追加.
 * 5. ★Phase 1: Lv4推論の基盤整備（スケルトンと候補セル選択）
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
    public static final int LV_4 = 4; // 背理法 (Contradiction) ★Phase 1: 追加

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
            updateAndGenerateRegions();

            // デバッグ出力
            // printRegionPool();

            // 2. ソルビング (レベル順に試す)
            Map<Integer, Integer> deduced = solveFromPool();

            if (!deduced.isEmpty()) {
                System.out.println("Round " + currentRound + ": Found " + deduced.size() + " cells.");
                applyResult(deduced);
                // 盤面が変わったので , 次のラウンドへ
                changed = true;
                currentRound++;
            } else {
                System.out.println("Round " + currentRound + ": No cells solved.");
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
     */
    private void updateAndGenerateRegions() {
        regionIdCounter = 0; // IDは見やすさのために毎回振り直す

        // 1. 既存プールのメンテナンス (Lv2, Lv3の更新)
        Map<Set<Integer>, Region> nextPool = new HashMap<>();

        for (Region r : regionPool.values()) {
            if (r.getOriginLevel() == LV_1)
                continue;

            Region updated = updateRegionState(r);

            if (updated != null && !updated.getCells().isEmpty()) {
                nextPool.put(updated.getCells(), updated);
            }
        }
        regionPool = nextPool;

        // 2. Lv1: Base Regions の完全再生成 (毎回実行)
        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) {
                Region r = createRegionFromHint(i);
                if (r != null) {
                    baseRegions.add(r);
                    addToPool(r);
                }
            }
        }

        // 3. Lv2 & Lv3 の新規生成 (★初回のみ実行★)
        if (!isDerivedRegionsGenerated) {
            for (int i = 0; i < baseRegions.size(); i++) {
                for (int j = i + 1; j < baseRegions.size(); j++) {
                    Region rA = baseRegions.get(i);
                    Region rB = baseRegions.get(j);

                    // --- 包含判定 (Lv2) ---
                    if (rA.isSubsetOf(rB)) {
                        Region diff = rB.subtract(rA, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(diff);
                    } else if (rB.isSubsetOf(rA)) {
                        Region diff = rA.subtract(rB, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(diff);
                    }
                    // --- 共通判定 (Lv3) ---
                    else {
                        Set<Region> intersections = rA.intersect(rB, LV_3);
                        for (Region r : intersections) {
                            addToPool(r);
                        }
                    }
                }
            }
            isDerivedRegionsGenerated = true;
        }

        reassignIds();
    }

    /**
     * Regionの状態を現在の盤面に合わせる（確定セルの除去）
     */
    private Region updateRegionState(Region original) {
        Set<Integer> currentCells = new HashSet<>();
        int currentMines = original.getMines();

        for (int cell : original.getCells()) {
            int val = board[cell];
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
     * Regionをプールに追加する.
     */
    private void addToPool(Region newRegion) {
        Set<Integer> key = newRegion.getCells();
        if (regionPool.containsKey(key)) {
            Region existing = regionPool.get(key);
            if (newRegion.getOriginLevel() < existing.getOriginLevel()) {
                regionPool.put(key, newRegion);
            }
        } else {
            regionPool.put(key, newRegion);
        }
    }

    private void reassignIds() {
        int id = 0;
        List<Region> list = new ArrayList<>(regionPool.values());
        list.sort(Comparator.comparingInt(Region::getOriginLevel)
                .thenComparingInt(Region::hashCode));

        for (Region r : list) {
            r.setId(++id);
        }
    }

    /**
     * プールされたRegionを使って確定できるセルを探す.
     */
    private Map<Integer, Integer> solveFromPool() {
        Map<Integer, Integer> deduced = new HashMap<>();
        Map<Integer, Integer> deducedLevel = new HashMap<>();

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(regionPool.values());
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

        // ★Phase 2: Lv4推論の呼び出し（有効化）
        if (deduced.isEmpty()) {
            deduced = solveWithContradiction();
            if (!deduced.isEmpty()) {
                Map<Integer, Integer> lv4Map = new HashMap<>();
                for (int cell : deduced.keySet()) {
                    lv4Map.put(cell, LV_4);
                }
                updateDifficultyMap(lv4Map);
            }
        }

        return deduced;
    }

    // =========================================================================
    // ★Phase 1-3: Lv4推論のメソッド群
    // =========================================================================

    /**
     * ★Phase 1-3: Lv4推論のメイン処理（背理法）
     * 
     * Lv1-3で解けなかった場合に呼び出される.
     * 未確定セルに対して「MINE」「SAFE」の仮定を立て、
     * DLXで矛盾を検出することで確定させる.
     * 
     * @return 確定したセルのマップ（空の場合、Lv4でも確定できなかった）
     */
    private Map<Integer, Integer> solveWithContradiction() {
        System.out.println("  [Lv4] Starting contradiction analysis...");

        // 候補セルの選択
        List<Integer> candidates = selectCandidateCells();

        if (candidates.isEmpty()) {
            System.out.println("  [Lv4] No unknown cells found.");
            return new HashMap<>();
        }

        System.out.println("  [Lv4] Testing " + candidates.size() + " candidate cells");

        // 各候補について検証
        for (int i = 0; i < candidates.size(); i++) {
            int cell = candidates.get(i);

            System.out.println("  [Lv4] Testing cell " + cell +
                    " (" + (i + 1) + "/" + candidates.size() + ")");

            // ★Phase 3: 実際の必要ヒント抽出
            Set<Integer> requiredHints = extractRequiredHints(cell);

            if (requiredHints.isEmpty()) {
                System.out.println("    No required hints found, skipping...");
                continue;
            }

            String hintsStr = requiredHints.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            System.out.println("    Required hints: " + hintsStr);

            // ★Phase 2: 仮定1 - MINE
            System.out.println("    Testing: cell = MINE");
            boolean mineValid = testAssumptionWithDLX(requiredHints, cell, FLAGGED);
            System.out.println("      → " + (mineValid ? "Valid (has solution)" : "CONTRADICTION (no solution)"));

            // ★Phase 2: 仮定2 - SAFE
            System.out.println("    Testing: cell = SAFE");
            boolean safeValid = testAssumptionWithDLX(requiredHints, cell, SAFE);
            System.out.println("      → " + (safeValid ? "Valid (has solution)" : "CONTRADICTION (no solution)"));

            // 結果判定
            if (!mineValid && safeValid) {
                // MINE仮定が矛盾 → SAFE確定
                System.out.println("  [Lv4] ★ Cell " + cell + " = SAFE (MINE leads to contradiction)");

                Map<Integer, Integer> result = new HashMap<>();
                result.put(cell, SAFE);

                logger.logStep(currentRound, cell, "SAFE", LV_4,
                        -1, "Contradiction(MINE→×)", hintsStr);

                return result;

            } else if (mineValid && !safeValid) {
                // SAFE仮定が矛盾 → MINE確定
                System.out.println("  [Lv4] ★ Cell " + cell + " = MINE (SAFE leads to contradiction)");

                Map<Integer, Integer> result = new HashMap<>();
                result.put(cell, FLAGGED);

                logger.logStep(currentRound, cell, "MINE", LV_4,
                        -1, "Contradiction(SAFE→×)", hintsStr);

                return result;

            } else if (!mineValid && !safeValid) {
                // 両方矛盾（エラー）
                System.err.println("  [Lv4] ERROR: Both assumptions lead to contradiction for cell " + cell);

            } else {
                // 両方valid → 確定できない
                System.out.println("  [Lv4] Cell " + cell + " cannot be determined");
            }
        }

        System.out.println("  [Lv4] No cells determined");
        return new HashMap<>();
    }

    /**
     * ★Phase 3: 実際の必要ヒント抽出
     * 
     * HintCountCalculatorを使用して、指定されたセルの確定に
     * 必要な最小限のヒント集合を取得する.
     * 
     * @param cellIndex 対象セルのインデックス
     * @return 必要なヒントのインデックス集合
     */
    private Set<Integer> extractRequiredHints(int cellIndex) {
        // HintCountCalculatorを実行
        HintCountCalculator calculator = new HintCountCalculator(
                board, // 現在の盤面
                completeBoard, // 完全解
                size // 盤面サイズ
        );

        // 解析実行
        calculator.calculate();

        // 指定されたセルの必要ヒントを取得
        Set<Integer> requiredHints = calculator.getRequiredHints(cellIndex);

        return requiredHints;
    }

    /**
     * ★Phase 2: DLXによる仮定の検証
     * 
     * 指定された仮定のもとで解が存在するかを判定する.
     * 
     * @param requiredHints 必要なヒント集合
     * @param targetCell    仮定する対象セル
     * @param assumedValue  仮定する値（FLAGGED or SAFE）
     * @return 解が存在するか（true: 矛盾しない, false: 矛盾する）
     */
    private boolean testAssumptionWithDLX(
            Set<Integer> requiredHints,
            int targetCell,
            int assumedValue) {

        // 1. 必要ヒントの周囲セルを収集（関連する全セル）
        Set<Integer> relevantCells = new HashSet<>();
        relevantCells.add(targetCell); // 対象セルも含む

        for (int hintIdx : requiredHints) {
            List<Integer> neighbors = getNeighbors(hintIdx);
            relevantCells.addAll(neighbors);
        }

        // 2. テスト盤面を作成（関連セルのみ、それ以外はIGNORE）
        int[] testBoard = new int[board.length];
        Arrays.fill(testBoard, IGNORE);

        // 3. 必要ヒントをコピー
        for (int hintIdx : requiredHints) {
            testBoard[hintIdx] = board[hintIdx];
        }

        // 4. 関連セルの状態をコピー
        for (int cellIdx : relevantCells) {
            if (cellIdx == targetCell) {
                // 対象セルには仮定を適用
                testBoard[cellIdx] = assumedValue;
            } else {
                // その他のセルは現在の盤面状態をコピー
                // MINE（未確定）, FLAGGED（地雷確定）, SAFE（安全確定）など
                testBoard[cellIdx] = board[cellIdx];
            }
        }

        // 5. ConstraintBuilderで制約行列化
        ConstraintBuilder cb = new ConstraintBuilder(testBoard, size);
        ConstraintBuilder.Data data = cb.buildConstraints();

        // 変数が存在しない場合（全て確定済み）は矛盾なしとみなす
        if (data.blanks == 0) {
            return true;
        }

        // 6. DLXで解の有無を判定
        DancingLinks dlx = new DancingLinks(data.matrix, data.constraint);
        dlx.runSolver();

        return dlx.SolutionsCount(0) > 0;
    }

    /**
     * ★Phase 1 Step 1.2: 候補セルの選択
     * 
     * 未確定セル（board[i] == MINE）を全て抽出する.
     * Phase 1では優先度なしでインデックス順に返す.
     * 
     * @return 未確定セルのリスト（インデックス順）
     */
    private List<Integer> selectCandidateCells() {
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < board.length; i++) {
            if (board[i] == MINE) { // 未確定セル
                candidates.add(i);
            }
        }

        // Phase 1: インデックス順（ソートなし）
        // 将来的に優先度ソートが必要な場合はここで実装

        return candidates;
    }

    // =========================================================================
    // ★Phase 1: テスト用のデバッグメソッド
    // =========================================================================

    /**
     * Phase 1の動作確認用: 候補セル選択のテスト
     */
    public void testCandidateSelection() {
        List<Integer> candidates = selectCandidateCells();
        System.out.println("=== Candidate Selection Test ===");
        System.out.println("Total unknown cells: " + candidates.size());
        System.out.println("Candidates (first 10): ");
        for (int i = 0; i < Math.min(10, candidates.size()); i++) {
            int cell = candidates.get(i);
            int row = cell / size;
            int col = cell % size;
            System.out.println("  Cell " + cell + " (row=" + row + ", col=" + col + ")");
        }
        System.out.println("================================");
    }

    // =========================================================================
    // 既存のヘルパーメソッド群
    // =========================================================================

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

    private Region createRegionFromHint(int hintIdx) {
        if (board[hintIdx] < 0)
            return null;

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
}