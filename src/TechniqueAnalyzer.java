import java.util.*;

/**
 * Region事前全列挙モデルによるマインスイーパ難易度解析クラス。
 * <p>
 * このクラスは、マインスイーパの盤面状態を入力として受け取り、論理的に解ける手順をシミュレーションします。
 * 詳細設計書「2.2. TechniqueAnalyzer クラス」および「3. アルゴリズム詳細」に基づき実装されています。
 * </p>
 * <p>
 * 主な機能：
 * <ul>
 * <li>現在の盤面から制約条件（Region）を抽出</li>
 * <li>Region間の演算（包含、共通部分）による新たな制約の導出</li>
 * <li>各セルを解くために必要なテクニックレベル（難易度）の判定</li>
 * </ul>
 * </p>
 */
public class TechniqueAnalyzer {

    // =========================================================================
    // 定数定義
    // =========================================================================

    /** 未確定の変数（セル）。まだ開かれておらず、フラグも立っていない状態を表します。 */
    private static final int MINE = -1;

    /** 計算対象外のセル（壁や既に数字が確定している場所など、これ以上情報を生まない場所）。 */
    private static final int IGNORE = -2;

    /** プレイヤーによって（または解析によって）地雷であると特定されたセル。 */
    private static final int FLAGGED = -3;

    /** 解析によって安全であると特定されたセル（一時的に使用）。その後、正解盤面の数字で上書きされます。 */
    private static final int SAFE = -4;

    // --- 難易度レベル定数 ---

    /** 未解決の状態。 */
    public static final int LV_UNSOLVED = -1;

    /** レベル0: 初期ヒント（最初から開いている数字セル）。 */
    public static final int LV_0 = 0;

    /** レベル1: Base Hint (単純な数字の制約)。「残り地雷数 = 未開封セル数」または「残り地雷数 = 0」で解けるケース。 */
    public static final int LV_1 = 1;

    /** レベル2: 包含テクニック (1-1パターンなど)。あるRegionが別のRegionに完全に含まれる場合に使用。 */
    public static final int LV_2 = 2;

    /** レベル3: 共通テクニック (Intersection)。複数のRegionが共通部分を持ち、そこから情報を引き出す高度な手法。 */
    public static final int LV_3 = 3;

    // =========================================================================
    // フィールド
    // =========================================================================

    /** 現在のシミュレーション上の盤面状態。解析が進むにつれて更新されます。 */
    private int[] currentBoard;

    /** 正解盤面（裏で持っている真理値）。SAFEと判定されたセルの数字を取得するために使用します。 */
    private final int[] completeBoard;

    /** 各セルがどのレベルのテクニックで解けたかを記録するマップ。 */
    private final int[] difficultyMap;

    /** 盤面の横幅（正方形を想定）。座標計算に使用します。 */
    private final int size;

    /** 初期状態で開いていた数字セルのインデックス集合。解析の起点となります。 */
    private final Set<Integer> initialHints;

    /**
     * * 生成されたRegionを管理するプール。
     * Key: Regionを構成するセルのSet, Value: Regionオブジェクト
     * 重複するRegion（同じセル集合を持つもの）を排除するためにMapを使用しています。
     */
    private Map<Set<Integer>, Region> regionPool;

    // =========================================================================
    // コンストラクタ
    // =========================================================================

    /**
     * コンストラクタ。解析器を初期化します。
     * * @param initialBoard 初期盤面状態（未開封はMINE, 開封済みは数字）
     * 
     * @param solution 正解の完全な盤面
     * @param size     盤面の一辺の長さ
     */
    public TechniqueAnalyzer(int[] initialBoard, int[] solution, int size) {
        this.currentBoard = Arrays.copyOf(initialBoard, initialBoard.length);
        this.completeBoard = solution;
        this.size = size;

        // 難易度マップを初期化（全て未解決）
        this.difficultyMap = new int[initialBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);

        this.regionPool = new HashMap<>();

        // 初期ヒント（数字が見えているセル）の位置を記録
        // これらは解析の種（Seed）として機能します。
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
     * 解析のメインループを実行します。
     * <p>
     * 階層的探索アルゴリズムを採用しています：
     * <ol>
     * <li>まずLv1（基本ルール）だけで解ける場所を全て解きます。</li>
     * <li>行き詰まったらLv2（包含）を試します。Lv2で進展があれば、再びLv1に戻ります。</li>
     * <li>Lv2でもダメならLv3（共通）を試します。進展があれば、やはりLv1に戻ります。</li>
     * </ol>
     * これにより、「人間に近い」解き順（簡単な方法を優先する）をシミュレートします。
     * </p>
     */
    public void analyze() {
        // Step 0: 初期状態で開いているヒントは難易度0としてマーク
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = LV_0;
            }
        }

        int round = 1;

        // 全てのセルが解けるか、これ以上手詰まりになるまでループ
        while (!isAllSolved()) {
            System.out.println("\n========== Round " + round + " ==========");

            // --- Phase 1: Base Regions (Lv1) のみで解けるだけ解く ---
            // Lv1は計算コストが低いため、変化がなくなるまでループさせて徹底的に適用します。
            boolean lv1Changed = true;
            while (lv1Changed && !isAllSolved()) {
                // Lv1のRegion（盤面の数字そのものから生成される制約）だけを生成
                generateBaseRegions();

                System.out.println("[Lv1] Generated " + regionPool.size() + " base regions");
                // デバッグ出力
                printRegionPool();

                // プール内のRegionを使って確定できるセルを探す
                Map<Integer, Integer> deduced = solveFromPool();

                if (deduced.isEmpty()) {
                    // Lv1ではこれ以上解けない
                    lv1Changed = false;
                } else {
                    // 進展があった場合、結果を適用してループ継続
                    System.out.println("[Lv1] Solved " + deduced.size() + " cells");
                    applyResult(deduced);
                }
            }

            // 全て解けたら終了
            if (isAllSolved())
                break;

            // --- Phase 2: Lv2 (包含テクニック) を試す ---
            // Lv1で行き詰まった場合のみ実行されます。
            System.out.println("\n[Lv2] Trying inclusion technique...");

            // Lv2までのRegion（Base + 包含による派生）を生成
            generateUpToLevel(LV_2);

            System.out.println("[Lv2] Generated " + regionPool.size() + " regions (Lv1+Lv2)");
            printRegionPool();

            Map<Integer, Integer> lv2Deduced = solveFromPool();

            if (!lv2Deduced.isEmpty()) {
                // Lv2で進展があった場合
                System.out.println("[Lv2] Solved " + lv2Deduced.size() + " cells");
                applyResult(lv2Deduced);
                round++;

                // 重要: 盤面が変わったので、より簡単なLv1で解ける場所が増えた可能性があります。
                // そのため、Lv3には行かずにループの先頭（Phase 1）に戻ります。
                continue;
            }

            if (isAllSolved())
                break;

            // --- Phase 3: Lv3 (共通テクニック) を試す ---
            // Lv1もLv2も通用しない場合のみ実行されます。計算コストが高い処理です。
            System.out.println("\n[Lv3] Trying intersection technique...");

            // Lv3までのRegion（Base + 包含 + 共通）を生成
            generateUpToLevel(LV_3);

            System.out.println("[Lv3] Generated " + regionPool.size() + " regions (Lv1+Lv2+Lv3)");
            printRegionPool();

            Map<Integer, Integer> lv3Deduced = solveFromPool();

            if (!lv3Deduced.isEmpty()) {
                // Lv3で進展があった場合
                System.out.println("[Lv3] Solved " + lv3Deduced.size() + " cells");
                applyResult(lv3Deduced);
                round++;

                // Lv2と同様、盤面更新後はPhase 1に戻ります。
                continue;
            }

            // どのレベルでも解けなかった場合は終了（手詰まり/運ゲー領域）
            System.out.println("\nNo more cells can be solved at any level. Stopping.");
            break;
        }

        // 解析終了後の結果表示
        System.out.println("\n========== Analysis Complete ==========");
        if (isAllSolved()) {
            System.out.println("All cells solved!");
        } else {
            System.out.println("Some cells remain unsolved (may require Lv4+ techniques)");
        }
    }

    /**
     * 解析結果の難易度マップを取得します。
     * 
     * @return 各セルの難易度（LV_0 ～ LV_3, または LV_UNSOLVED）
     */
    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    // =========================================================================
    // Phase 1: Region生成フェーズ
    // =========================================================================

    /**
     * 現在の盤面から全てのRegion（Lv1〜Lv3）を生成し、プールに追加します。
     * 設計書「3.1. Region生成フェーズ」に準拠。
     */
    private void generateAllRegions() {
        generateUpToLevel(LV_3);
    }

    /**
     * 指定された難易度レベルまでのRegionを段階的に生成します。
     * * @param maxLevel 生成する最大レベル（LV_1, LV_2, or LV_3）
     */
    private void generateUpToLevel(int maxLevel) {
        // プールをリセットして再生成（盤面状況が変わっているため）
        regionPool.clear();

        // Step 1: Base Regions (Lv1) - 盤面の数字ヒントから直接生成
        List<Region> baseRegions = generateBaseRegionsInternal();
        for (Region r : baseRegions) {
            addToPool(r);
        }

        // Lv2以上が要求されていない場合はここで終了
        if (maxLevel < LV_2)
            return;

        // Step 2: Derived Regions - 包含 (Lv2)
        // 既存のBase Regions同士を比較して、包含関係から新しい制約を導きます。
        List<Region> derivedSubtract = generateSubtractionRegions(baseRegions);
        for (Region r : derivedSubtract) {
            addToPool(r);
        }

        // Lv3以上が要求されていない場合はここで終了
        if (maxLevel < LV_3)
            return;

        // Step 3: Derived Regions - 共通 (Lv3)
        // 共通部分を持つRegion同士から、さらに新しい制約を導きます。
        // ※ここでは簡単のため、baseRegionsのみをソースとしていますが、
        // 本来はderivedSubtractも含めて再帰的に行うとより強力です。
        List<Region> derivedIntersect = generateIntersectionRegions(baseRegions);
        for (Region r : derivedIntersect) {
            addToPool(r);
        }
    }

    /**
     * Base Regions生成のみを行うラッパーメソッド（analyzeループ内で使用）。
     */
    private void generateBaseRegions() {
        regionPool.clear();
        List<Region> baseRegions = generateBaseRegionsInternal();
        for (Region r : baseRegions) {
            addToPool(r);
        }
    }

    /**
     * Base Regions生成の内部ロジック。
     * 盤上の数字ヒントを走査し、Regionオブジェクトに変換します。
     * * @return 生成されたRegionのリスト
     */
    private List<Region> generateBaseRegionsInternal() {
        List<Region> regions = new ArrayList<>();

        // 効率化のため、全てのセルではなく「初期ヒント（数字）」の位置のみをチェック
        // ※厳密には「現在境界に接している数字」だけを見れば十分です。
        for (int hintIdx : initialHints) {
            // Regionの生成を試みる
            Region r = createRegionFromHint(hintIdx, LV_1);

            // 有効なRegion（未確定セルを含んでいる）であればリストに追加
            if (r != null && !r.getCells().isEmpty()) {
                regions.add(r);
            }
        }

        return regions;
    }

    /**
     * 特定のヒント（数字セル）からRegionを生成します。
     * <p>
     * 例：数字「2」の周りに未確定セルが3つ、確定旗が1つある場合
     * -> 未確定セル3つの中に、残り地雷数(2 - 1) = 1個がある、というRegionを生成。
     * </p>
     * * @param hintIdx ヒントセルのインデックス
     * 
     * @param level このRegionの基本レベル
     * @return 生成されたRegion、または情報がない場合はnull
     */
    private Region createRegionFromHint(int hintIdx, int level) {
        int hintVal = currentBoard[hintIdx]; // ヒントの数字（例: 3）
        List<Integer> neighbors = getNeighbors(hintIdx); // 周囲8マス
        Set<Integer> unknownCells = new HashSet<>();
        int flaggedCount = 0; // 既に特定された地雷の数

        // 周囲のセルを分類
        for (int nb : neighbors) {
            int val = currentBoard[nb];
            if (val == MINE) {
                // 未確定セル
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                // 確定地雷
                flaggedCount++;
            }
        }

        // 未確定セルがなければ、これ以上このヒントから情報は得られない
        if (unknownCells.isEmpty()) {
            return null;
        }

        // 残り地雷数 = ヒント数字 - 既に判明した地雷数
        int remainingMines = hintVal - flaggedCount;

        // このRegionの生成元となったヒント情報を記録（デバッグ・可視化用）
        Set<Integer> sourceHints = new HashSet<>();
        sourceHints.add(hintIdx);

        // 新しいRegionを作成
        return new Region(unknownCells, remainingMines, level, sourceHints);
    }

    /**
     * 包含関係(Subset)に基づく差分Regionの生成 (Lv2)。
     * <p>
     * ロジック：
     * Region A のセル集合が Region B のセル集合に完全に含まれる場合 (A ⊂ B)、
     * 差分領域 (B - A) にある地雷数は (Bの地雷数 - Aの地雷数) となります。
     * これにより、元のBよりも範囲が狭く、情報の密度が高い新しいRegionが生成されます。
     * </p>
     * * @param baseRegions 比較対象のRegionリスト
     * 
     * @return 生成された差分Regionのリスト
     */
    private List<Region> generateSubtractionRegions(List<Region> baseRegions) {
        List<Region> derived = new ArrayList<>();

        // 全ペアについて包含関係を総当たりチェック
        // ※計算量は O(N^2) となるため、Region数が多い場合は注意が必要です。
        for (int i = 0; i < baseRegions.size(); i++) {
            Region rA = baseRegions.get(i);
            for (int j = 0; j < baseRegions.size(); j++) {
                if (i == j)
                    continue;
                Region rB = baseRegions.get(j);

                // A ⊂ B の場合、差分 D = B - A を生成
                if (rA.isSubsetOf(rB)) {
                    // Regionクラスのsubtractメソッドで差分計算
                    Region diff = rB.subtract(rA, LV_2);

                    // 生成されたRegionが有効かチェック
                    // (空でなく、地雷数が負にならず、地雷数がセル数を超えない)
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
     * 共通部分(Intersection)に基づくRegion生成 (Lv3)。
     * <p>
     * ロジック：
     * 2つのRegionが部分的に重なっている場合、その重なり部分や、重なり以外の部分について
     * 新たな制約（地雷数の最大・最小値など）を導き出せる場合があります。
     * </p>
     * * @param baseRegions 比較対象のRegionリスト
     * 
     * @return 生成された派生Regionのリスト
     */
    private List<Region> generateIntersectionRegions(List<Region> baseRegions) {
        List<Region> derived = new ArrayList<>();

        // 全ペアについて共通部分をチェック
        for (int i = 0; i < baseRegions.size(); i++) {
            Region rA = baseRegions.get(i);
            for (int j = i + 1; j < baseRegions.size(); j++) {
                Region rB = baseRegions.get(j);

                // 包含関係がある場合はLv2で処理済みなのでスキップ
                if (rA.isSubsetOf(rB) || rB.isSubsetOf(rA)) {
                    continue;
                }

                // 共通部分を持つ場合、intersectメソッドで新Regionを生成
                // ※intersectは複数の可能性（分割されたRegionなど）を返すことがあります
                Set<Region> intersectResults = rA.intersect(rB, LV_3);
                derived.addAll(intersectResults);
            }
        }

        return derived;
    }

    /**
     * 生成されたRegionをプールに追加します。
     * 重複排除と、より低レベル（＝単純）な解法の優先を行います。
     * * @param newRegion 追加候補のRegion
     */
    private void addToPool(Region newRegion) {
        Set<Integer> key = newRegion.getCells();

        if (regionPool.containsKey(key)) {
            Region existing = regionPool.get(key);
            // 同じセル集合に対して、同じ地雷数の主張をしている場合のみ比較
            if (existing.getMines() == newRegion.getMines()) {
                // より低いレベル（簡単な理屈）で生成された方を優先して残す
                if (newRegion.getOriginLevel() < existing.getOriginLevel()) {
                    regionPool.put(key, newRegion);
                }
            } else {
                // セル集合が同じで地雷数が矛盾する場合、論理矛盾が発生している可能性があります。
                // ここでは簡易実装として無視しますが、厳密なソルバでは矛盾検知が必要です。
            }
        } else {
            // 新規Regionならそのまま登録
            regionPool.put(key, newRegion);
        }
    }

    // =========================================================================
    // Phase 2: 解決フェーズ
    // =========================================================================

    /** 解決に使用したRegionのインデックスを記録（ログ出力用） */
    private Map<Integer, Integer> cellToRegionIndex = new HashMap<>();

    /**
     * プール内の全てのRegionをチェックし、地雷または安全が確定するセルを探します。
     * 設計書「3.2. 解決フェーズ」に準拠。
     * * @return 確定したセル情報（Key:セルID, Value:判定結果(FLAGGED/SAFE)）
     */
    private Map<Integer, Integer> solveFromPool() {
        Map<Integer, Integer> deduced = new HashMap<>();
        cellToRegionIndex.clear();

        // 難易度レベルが低い順（簡単なRegion順）にソートして評価します
        // これにより、Lv2で解ける場所を誤ってLv3の手柄にすることを防ぎます。
        List<Region> sortedRegions = new ArrayList<>(regionPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        // ソートされた順にRegionを試す
        for (int regionIdx = 0; regionIdx < sortedRegions.size(); regionIdx++) {
            Region region = sortedRegions.get(regionIdx);

            // Case 1: 残り地雷数 == 未確定セル数 → 「全て地雷」
            if (region.getMines() == region.size()) {
                for (int cell : region.getCells()) {
                    // まだ判定が出ていないセルについて処理
                    if (!deduced.containsKey(cell) && currentBoard[cell] == MINE) {
                        deduced.put(cell, FLAGGED);
                        cellToRegionIndex.put(cell, regionIdx);

                        // 難易度マップを更新（そのセルを解くのに必要だった最低レベル）
                        int level = Math.max(LV_1, region.getOriginLevel());
                        if (difficultyMap[cell] == LV_UNSOLVED) {
                            difficultyMap[cell] = level;
                        }
                    }
                }
            }
            // Case 2: 残り地雷数 == 0 → 「全て安全」
            else if (region.getMines() == 0) {
                for (int cell : region.getCells()) {
                    if (!deduced.containsKey(cell) && currentBoard[cell] == MINE) {
                        deduced.put(cell, SAFE);
                        cellToRegionIndex.put(cell, regionIdx);

                        // 難易度マップを更新
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
     * 確定したセル情報を現在の盤面に反映します。
     * 設計書「3.3. 更新フェーズ」に準拠。
     * * @param deduced 確定したセルのマップ
     */
    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            // ログ用に、どのRegionを使って解いたかを取得
            Integer regionIdx = cellToRegionIndex.get(cellIdx);
            String regionInfo = (regionIdx != null) ? " using Region[" + regionIdx + "]" : "";

            String displayVal;
            if (val == SAFE) {
                // 安全なら、正解盤面から本当の数字を取得してオープンする
                val = completeBoard[cellIdx];
                displayVal = "SAFE";
            } else {
                // 地雷ならフラグを立てる
                displayVal = "MINE";
            }

            // 盤面状態を更新
            currentBoard[cellIdx] = val;

            System.out.println("  Applied: Cell " + cellIdx + " = " + displayVal +
                    " (Level " + difficultyMap[cellIdx] + ")" + regionInfo);
        }
    }

    // =========================================================================
    // ユーティリティメソッド
    // =========================================================================

    /**
     * 指定されたセルの周囲8マスのインデックスを取得します。
     * 盤面の端や角の判定も行います。
     * * @param idx 中心セルのインデックス
     * 
     * @return 周囲セルのインデックスリスト
     */
    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size; // 行
        int c = idx % size; // 列

        // 3x3の範囲を走査
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue; // 自分自身は除く

                int nr = r + dr;
                int nc = c + dc;

                // 盤面範囲内かチェック
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }

        return list;
    }

    /**
     * 全てのセルが解決済み（未確定MINEがない）か判定します。
     * * @return true: 全て確定済み, false: 未確定セルあり
     */
    private boolean isAllSolved() {
        for (int val : currentBoard) {
            if (val == MINE)
                return false;
        }
        return true;
    }

    /**
     * デバッグ用: 現在のRegionプールの内容を標準出力に表示します。
     */
    private void printRegionPool() {
        if (regionPool.isEmpty()) {
            System.out.println("  (Pool is empty)");
            return;
        }

        List<Region> sorted = new ArrayList<>(regionPool.values());
        // ログが見やすいようにレベル順にソートして表示
        sorted.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (int i = 0; i < sorted.size(); i++) {
            Region r = sorted.get(i);
            System.out.println("  [" + i + "]: " + r.toString());
        }
    }
}