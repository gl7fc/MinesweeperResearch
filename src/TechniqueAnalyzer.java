import java.util.*;

/**
 * 盤面全体の状態を管理し、推論のステップを進めるクラス。
 * 「Region事前全列挙モデル」に基づき実装。
 * * ★修正版仕様:
 * 1. Lv2/Lv3のRegionは「初期盤面」からのみ生成し、以降は新規生成しない。
 * 2. ラウンド進行時は、既存のLv2/Lv3 Regionをメンテナンス(確定セルの除去)して維持する。
 * 3. Lv1 Regionのみ、毎ラウンド最新の盤面から再生成する。
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

    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
        this.regionPool = new HashMap<>();
    }

    /**
     * 解析のメインループ。
     */
    public void analyze() {
        // 初期ヒントは難易度0
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0)
                difficultyMap[i] = 0;
        }

        boolean changed = true;
        int round = 1;

        while (changed) {
            changed = false;
            System.out.println("\n--- Round " + round + " Start ---");

            printCurrentBoard("Start of Round " + round);

            // 1. Regionの生成とメンテナンス
            // Lv1は全再生成、Lv2/Lv3は初回のみ生成し以降は維持・更新
            updateAndGenerateRegions();

            // デバッグ出力
            printRegionPool();

            // 2. ソルビング (レベル順に試す)
            Map<Integer, Integer> deduced = solveFromPool();

            if (!deduced.isEmpty()) {
                System.out.println("Round " + round + ": Found " + deduced.size() + " cells.");
                applyResult(deduced);
                // 盤面が変わったので、次のラウンドへ
                changed = true;
                round++;
            } else {
                System.out.println("Round " + round + ": No cells solved.");
            }
        }
    }

    /**
     * Regionプールの更新と新規生成を行う
     */
    private void updateAndGenerateRegions() {
        regionIdCounter = 0; // IDは見やすさのために毎回振り直す

        // 1. 既存プールのメンテナンス (Lv2, Lv3の更新)
        // Lv1は再生成するので引き継がない。
        // Lv2, Lv3は「初期に生成した推論」を維持するため、現在の盤面に合わせて内容を更新(縮小)して残す。
        Map<Set<Integer>, Region> nextPool = new HashMap<>();

        for (Region r : regionPool.values()) {
            // Lv1 は毎回作り直すので、プールには残さない
            if (r.getOriginLevel() == LV_1)
                continue;

            // Lv2, Lv3 について、確定したセルを除去するなどのメンテナンスを行う
            Region updated = updateRegionState(r);

            // まだ未確定セルが残っていて有効なら、次世代プールに引き継ぐ
            if (updated != null && !updated.getCells().isEmpty()) {
                // キーは新しいセル集合
                nextPool.put(updated.getCells(), updated);
            }
        }
        regionPool = nextPool;

        // 2. Lv1: Base Regions の完全再生成 (毎回実行)
        // 現在の盤面から、最新のヒントRegionを生成する
        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) {
                Region r = createRegionFromHint(i);
                if (r != null) {
                    baseRegions.add(r);
                    addToPool(r); // Lv1を追加
                }
            }
        }

        // 3. Lv2 & Lv3 の新規生成 (★初回のみ実行★)
        // ユーザー要望: Lv2/Lv3は初期盤面から生成し、以降は新しく生成しない
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
                    // 包含関係がない場合のみ
                    else {
                        Set<Region> intersections = rA.intersect(rB, LV_3);
                        for (Region r : intersections) {
                            addToPool(r);
                        }
                    }
                }
            }
            // 生成済みフラグを立てる (これ以降、Lv2/Lv3の新規生成は行われない)
            isDerivedRegionsGenerated = true;
        }

        // 最後にIDを振り直して整理
        reassignIds();
    }

    /**
     * Regionの状態を現在の盤面に合わせる（確定セルの除去）
     * 矛盾が生じたり空になった場合はnullを返す
     */
    private Region updateRegionState(Region original) {
        Set<Integer> currentCells = new HashSet<>();
        int currentMines = original.getMines();

        for (int cell : original.getCells()) {
            int val = board[cell];
            if (val == MINE) {
                // まだ未確定なら残す
                currentCells.add(cell);
            } else if (val == FLAGGED) {
                // 地雷と確定していたら、このRegionの「残り地雷数」を減らす
                currentMines--;
            }
            // SAFEの場合は単にリストから消える（地雷数は変わらない）
        }

        if (currentMines < 0)
            return null; // 矛盾

        // 中身が変わっていなければ元のインスタンスを返す
        if (currentCells.size() == original.getCells().size() && currentMines == original.getMines()) {
            return original;
        }

        // 更新された情報で新しいRegionを作成 (IDやソース情報は引き継ぎたいが、ここではシンプルに生成)
        // 必要なら SourceHint の引き継ぎロジックを入れる
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
     * Regionをプールに追加する。
     */
    private void addToPool(Region newRegion) {
        Set<Integer> key = newRegion.getCells();
        if (regionPool.containsKey(key)) {
            Region existing = regionPool.get(key);
            // 既存より新しいRegionのレベルが低い場合のみ更新
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
     * プールされたRegionを使って確定できるセルを探す。
     */
    private Map<Integer, Integer> solveFromPool() {
        Map<Integer, Integer> deduced = new HashMap<>();
        Map<Integer, Integer> deducedLevel = new HashMap<>();

        // レベル順(昇順)にソートして処理
        List<Region> sortedRegions = new ArrayList<>(regionPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : sortedRegions) {
            // もし既にLv1のテクニックで確定したセルがあるなら、Lv2以上の探索は行わずに即座に戻る
            if (!deduced.isEmpty()) {
                boolean hasLv1Deduction = false;
                for (int level : deducedLevel.values()) {
                    if (level == LV_1) {
                        hasLv1Deduction = true;
                        break;
                    }
                }
                if (r.getOriginLevel() > LV_1 && hasLv1Deduction) {
                    // ★修正: 戻る前に難易度マップを更新する
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
                                " (via Region #" + r.getId() + " [Lv" + r.getOriginLevel() + "])");
                    } else {
                        if (complexity < deducedLevel.get(cell)) {
                            deducedLevel.put(cell, complexity);
                        }
                    }
                }
            }
        }

        // 難易度マップへの反映 (まだ未確定の場所のみ)
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
     * 推論結果を盤面に適用する。
     */
    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            // ペンシルパズルルール: 数字は開示せず、状態のみ更新
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
            // MINE(-1) は未確定。SAFE(-4) は安全確定だがヒントではない。
            // Regionの対象は「地雷かどうかわからないセル」なので、SAFEは除外する。
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
        r.addSourceHint(hintIdx); // 生成元ヒントの位置を記録
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
                System.out.print(" S "); // Sマークのみで数字は出ない
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