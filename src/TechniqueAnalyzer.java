import java.util.*;

/**
 * 盤面全体の状態を管理し、推論のステップを進めるクラス。
 * TA方針.md に基づき実装。
 */
public class TechniqueAnalyzer {

    // 定数定義 (HintCountCalculator/ConstraintBuilderと合わせる)
    private static final int MINE = -1; // 未確定 (変数候補)
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定
    // 0以上はヒント数字

    private int[] board; // 現在の盤面状態
    private final int[] completeBoard; // 正解盤面 (検証・更新用)
    private final int[] difficultyMap; // 各セルの確定にかかったテクニックレベル
    private final int size; // 盤面サイズ (横幅=高さ)
    private List<Region> activeRegions; // 現在有効な推論の手がかりリスト

    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, -1); // -1:未解決
        this.activeRegions = new ArrayList<>();
    }

    /**
     * 解析のメインループ
     */
    public void analyze() {
        // 初期化: 盤面の数字ヒントからRegionを作成
        initRegions();

        // デバッグ表示 (初期状態) - 必要に応じてコメントアウトを解除してください
        printActiveRegions("Initial");

        boolean changed = true;
        int round = 1;

        while (changed) {
            changed = false;

            // 1. Lv1-1 (埋めるだけ) を試す
            Map<Integer, Integer> deducedLv1 = solveLv1_1();
            if (!deducedLv1.isEmpty()) {
                // デバッグ表示 (確定前) - 必要に応じてコメントアウトを解除してください
                // System.out.println("Round " + round + ": Found " + deducedLv1.size() + "
                // cells by Lv1-1");

                applyResult(deducedLv1, 1); // Lv1で確定
                changed = true;

                // デバッグ表示 (確定後) - 必要に応じてコメントアウトを解除してください
                printActiveRegions("After Lv1-1 (Round " + round + ")");

                round++;
                continue; // 確定したらループの最初(Lv1-1)に戻る
            }

            // Lv1-2/3, Lv1-4, Lv2 は現時点では実装しない
            // if (solveLv1_2_3()) ...
        }
    }

    /**
     * 初期化: 盤面上の数字ヒントを走査し、初期の Region リストを生成する
     */
    private void initRegions() {
        activeRegions.clear();
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) { // ヒントセル
                Region r = createRegionFromHint(i);
                if (r != null) {
                    activeRegions.add(r);
                }
            }
        }
    }

    /**
     * 指定されたヒントセルから Region を生成する
     * 
     * @param hintIdx ヒントセルのインデックス
     * @return Region オブジェクト (未確定セルが無い場合は null)
     */
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

        if (unknownCells.isEmpty()) {
            return null; // 全て確定済みならRegionは不要
        }

        // 残りの地雷数 = ヒント値 - 周囲の旗数
        int remainingMines = hintVal - flaggedCount;

        // 整合性チェック (負になる場合はデータ矛盾だが、ここでは0に丸める)
        if (remainingMines < 0)
            remainingMines = 0;

        // レベル0 (盤面由来) のRegionを作成
        return new Region(unknownCells, remainingMines, 0);
    }

    /**
     * Lv1-1: 埋めるだけ
     * - 残りすべて地雷 (mines == cells.size)
     * - 残りすべて安全 (mines == 0)
     */
    private Map<Integer, Integer> solveLv1_1() {
        Map<Integer, Integer> deduced = new HashMap<>();

        for (Region r : activeRegions) {
            if (r.getMines() == r.getCells().size()) {
                // すべて地雷
                for (int cell : r.getCells()) {
                    if (!deduced.containsKey(cell)) {
                        deduced.put(cell, FLAGGED);
                    }
                }
            } else if (r.getMines() == 0) {
                // すべて安全 -> 正解盤面から値を取得
                for (int cell : r.getCells()) {
                    if (!deduced.containsKey(cell)) {
                        // 正解盤面を参照 (Mineなら矛盾だが、ロジック上はSafe扱い)
                        int trueVal = completeBoard[cell];
                        // もし正解がMineならFLAGGEDにすべきだが、
                        // ここは「安全確定」のロジックなので、正解盤面がMineだとロジック矛盾か盤面不正
                        // ひとまず正解盤面の値をそのまま入れる
                        if (trueVal == -1) {
                            // 理論上ここには来ないはず(mines=0判定なので)
                            deduced.put(cell, FLAGGED);
                        } else {
                            deduced.put(cell, trueVal);
                        }
                    }
                }
            }
        }
        return deduced;
    }

    /**
     * 推論結果の適用と更新処理
     * 1. 盤面の完全更新
     * 2. 新規 Region の生成
     * 3. 既存 Region の更新
     * 4. マージ
     */
    private void applyResult(Map<Integer, Integer> deduced, int level) {
        Set<Integer> newlyOpenedHints = new HashSet<>();
        // Set<Integer> determinedCells = deduced.keySet(); // 未使用

        // 1. 盤面の完全更新 & 難易度記録
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            board[cellIdx] = val;
            if (difficultyMap[cellIdx] == -1) {
                difficultyMap[cellIdx] = level;
            }

            // もし安全で数字が開いたなら、新規ヒントとしてマーク
            if (val >= 0) {
                newlyOpenedHints.add(cellIdx);
            }
        }

        List<Region> nextRegions = new ArrayList<>();

        // 2. 新規 Region の生成 (新しく開いたヒントセルから)
        for (int hintIdx : newlyOpenedHints) {
            Region r = createRegionFromHint(hintIdx);
            if (r != null) {
                nextRegions.add(r);
            }
        }

        // 3. 既存 Region の更新
        for (Region r : activeRegions) {
            Set<Integer> currentCells = r.getCells();

            // 安全のため、relevant チェックを廃止し、常に全Regionを走査して更新判定を行う
            Set<Integer> newCells = new HashSet<>();
            int minesFound = 0;

            for (int cell : currentCells) {
                // まず deduced (今回の確定分) を確認
                if (deduced.containsKey(cell)) {
                    int val = deduced.get(cell);
                    if (val == FLAGGED) {
                        minesFound++;
                    }
                    // Safeなら除外 (minesFound増やさない)
                } else {
                    // deduced にない場合、現在の board の状態を確認する (重要！)
                    // これにより、deduced経由以外で確定したセルの取りこぼしを防ぐ
                    int currentVal = board[cell];
                    if (currentVal == MINE) {
                        // まだ未確定なら残す
                        newCells.add(cell);
                    } else if (currentVal == FLAGGED) {
                        // 既に地雷確定しているならカウントする
                        minesFound++;
                    }
                    // Safeなら除外
                }
            }

            // セル数が変わっていなくても、地雷数だけ変わる可能性もあるため、
            // 「まだ未確定セルが残っているか」だけで判定する
            if (!newCells.isEmpty()) {
                int newMines = r.getMines() - minesFound;
                // 整合性補正
                if (newMines < 0)
                    newMines = 0;
                if (newMines > newCells.size())
                    newMines = newCells.size();

                // 新しいRegion作成 (Levelは継承)
                Region newRegion = new Region(newCells, newMines, r.getOriginLevel());
                nextRegions.add(newRegion);
            }
        }

        // 4. マージ (重複排除)
        // Setに入れて重複を消してからリストに戻す
        Set<Region> uniqueRegions = new HashSet<>(nextRegions);
        activeRegions = new ArrayList<>(uniqueRegions);
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
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    // 結果取得用
    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    /**
     * デバッグ用: 現在の activeRegions をコンソールに出力する
     */
    public void printActiveRegions(String label) {
        System.out.println("--- Active Regions [" + label + "] ---");
        if (activeRegions.isEmpty()) {
            System.out.println(" (No active regions)");
        } else {
            // 見やすくするため、地雷数やセル数などでソートして表示することも可能だが
            // まずはそのまま出力する
            for (int i = 0; i < activeRegions.size(); i++) {
                System.out.println(" [" + i + "]: " + activeRegions.get(i).toString());
            }
        }
        System.out.println("-----------------------------------");
    }
}