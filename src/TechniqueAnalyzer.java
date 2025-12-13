import java.util.*;

/**
 * 盤面全体の状態を管理し、推論のステップを進めるクラス。
 * <p>
 * 設計方針 (TA方針.md) に基づき、以下の役割を担います:
 * 1. 盤面状態 (board) の管理
 * 2. 推論の手がかりとなる領域 (Region) の管理
 * 3. 各レベルの推論ロジック (Lv1-1, Lv1-2...) の実行制御
 * </p>
 */
public class TechniqueAnalyzer {

    // =========================================================================
    // 定数定義 (HintCountCalculator/ConstraintBuilderと合わせる)
    // =========================================================================
    /** 未確定セル (変数候補) */
    private static final int MINE = -1;
    /** 計算対象外 (盤面外や無効なセル) */
    private static final int IGNORE = -2;
    /** 地雷確定セル (推論により地雷と判定された場所) */
    private static final int FLAGGED = -3;

    /**
     * * 安全確定セル (推論により安全と判定された場所)。
     * <p>
     * ★重要: 本パズルの仕様上、安全と確定しても「そのセルの数字(ヒント)」は開示されない。
     * そのため、通常の数字(0以上)とは区別して管理する。
     * このセルから新しい Region が生成されることはない。
     * </p>
     */
    private static final int SAFE = -4;

    // ※ 0以上の値は、そのセルに表示されているヒント数字を表す

    // =========================================================================
    // フィールド
    // =========================================================================
    /** 現在の盤面状態。推論が進むにつれて MINE -> 数字 or FLAGGED に更新される。 */
    private int[] board;

    /** 正解盤面。推論結果の検証（安全装置）や、安全確定時の数字取得に使用する。 */
    private final int[] completeBoard;

    /** 各セルがどのレベルのテクニックで確定したかを記録するマップ。初期値は-1。 */
    private final int[] difficultyMap;

    /** 盤面のサイズ (N x N の N) */
    private final int size;

    /**
     * 現在有効な推論の手がかり (Region) のリスト。
     * Region とは、「あるセル集合の中に地雷が N 個ある」という制約情報のこと。
     */
    private List<Region> activeRegions;

    /**
     * コンストラクタ
     *
     * @param currentBoard 現在の盤面 (未確定部分は -1)
     * @param solution     正解の完全な盤面
     * @param size         盤面の一辺の長さ
     */
    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, -1); // -1:未解決で初期化
        this.activeRegions = new ArrayList<>();
    }

    /**
     * 解析のメインループ。
     * 簡単なテクニック (Lv1-1) から順に適用し、変化がなくなるまで繰り返す。
     */
    public void analyze() {
        // 1. 初期化: 盤面の既存の数字ヒントから最初の Region リストを作成する
        initRegions();

        // デバッグ表示 (初期状態)
        // printActiveRegions("Initial");

        boolean changed = true;
        int round = 1;

        // 変化が起き続ける限りループ (不動点に達するまで)
        while (changed) {
            changed = false;

            System.out.println("\n--- Round " + round + " Start ---");

            // ★追加: 現在の盤面を表示
            printCurrentBoard("Start of Round " + round);

            // -----------------------------------------------------------
            // 2. Lv1-1 (埋めるだけ) の適用
            // -----------------------------------------------------------
            Map<Integer, Integer> deducedLv1 = solveLv1_1();

            // 何らかのセルが確定した場合
            if (!deducedLv1.isEmpty()) {
                System.out.println("Round " + round + ": Found " + deducedLv1.size() + " cells by Lv1-1");

                // 確定情報を盤面に反映し、Region を更新・再生成する
                applyResult(deducedLv1, 1); // Lv1として記録

                // 盤面が変化したので、より高度なテクニックには進まず、
                // 再度 Lv1-1 からチェックし直す (簡単な手筋を優先するため)
                changed = true;

                // デバッグ表示 (確定後)
                // printActiveRegions("After Lv1-1 (Round " + round + ")");

                round++;
                continue;
            }

            // -----------------------------------------------------------
            // Lv1-2/3, Lv1-4, Lv2 は現時点では未実装
            // 将来的にはここに solveLv1_2_3() などを追加する
            // -----------------------------------------------------------
            // if (solveLv1_2_3()) ...
        }
    }

    /**
     * 初期化処理。
     * 盤面上のすべてのヒント数字を走査し、初期の Region リストを生成する。
     */
    private void initRegions() {
        activeRegions.clear();
        for (int i = 0; i < board.length; i++) {
            // 0以上の値はヒント数字。SAFE(-4)やFLAGGED(-3)からは生成しない。
            if (board[i] >= 0) {
                Region r = createRegionFromHint(i);
                if (r != null) {
                    activeRegions.add(r);
                }
            }
        }
    }

    /**
     * 指定されたヒントセルから Region (制約領域) を生成する。
     *
     * @param hintIdx ヒントセルのインデックス
     * @return Region オブジェクト。未確定セルが無い場合や、矛盾がある場合は null を返す。
     */
    private Region createRegionFromHint(int hintIdx) {
        // 対象セルの数字を取得
        int hintVal = board[hintIdx];
        // 対象セルの周囲のセルの番号を取得
        List<Integer> neighbors = getNeighbors(hintIdx);

        Set<Integer> unknownCells = new HashSet<>();
        int flaggedCount = 0;

        // 周囲のセル状態を確認
        for (int nb : neighbors) {
            int val = board[nb];
            if (val == MINE) {
                unknownCells.add(nb); // 未確定セルとしてリストアップ
            } else if (val == FLAGGED) {
                flaggedCount++; // 既に地雷と確定している数をカウント
            }
        }

        // 未確定セルが一つもない（既に解決済み）場合は Region を作る必要がない
        if (unknownCells.isEmpty()) {
            return null;
        }

        // この Region 内に残っているはずの地雷数 = (ヒント数字) - (周囲の確定地雷数)
        int remainingMines = hintVal - flaggedCount;

        // レベル0 (盤面のヒント数字由来) のRegionを作成して返す
        return new Region(unknownCells, remainingMines, 0);
    }

    /**
     * Lv1-1: 埋めるだけ (Trivial Fill)
     * <p>
     * 以下の2パターンを判定する:
     * 1. 残りすべて地雷 (Regionの地雷数 == 未確定セル数) -> すべて FLAGGED
     * 2. 残りすべて安全 (Regionの地雷数 == 0) -> すべて SAFE (数字は開かない)
     * </p>
     *
     * @return 確定したセルのマップ (インデックス -> 確定後の値)
     */
    private Map<Integer, Integer> solveLv1_1() {
        Map<Integer, Integer> deduced = new HashMap<>();

        for (int i = 0; i < activeRegions.size(); i++) {
            Region r = activeRegions.get(i);

            // パターン1: 未確定セルがすべて地雷である場合
            if (r.getMines() == r.getCells().size()) {
                for (int cell : r.getCells()) {
                    // まだ推論済みリストに入っていない場合
                    if (!deduced.containsKey(cell)) {
                        deduced.put(cell, FLAGGED);
                        System.out.println("  -> Solved: Cell " + cell + " is MINE (Region " + i + ": " + r + ")");
                    }
                }
            }
            // パターン2: 未確定セルがすべて安全である場合
            else if (r.getMines() == 0) {
                for (int cell : r.getCells()) {
                    if (!deduced.containsKey(cell)) {
                        int trueVal = completeBoard[cell];

                        deduced.put(cell, SAFE);
                        System.out.println("  -> Solved: Cell " + cell + " is SAFE (" + trueVal + ") (Region " + i
                                + ": " + r + ")");
                    }
                }
            }
        }
        return deduced;
    }

    /**
     * 推論結果を盤面に適用し、次のラウンドのために Region リストを更新する。
     *
     * @param deduced 今回のステップで確定したセルのマップ
     * @param level   確定に使用したテクニックレベル
     */
    private void applyResult(Map<Integer, Integer> deduced, int level) {
        // 1. 盤面の完全更新 & 難易度記録

        // deduced.entrySet()で確定情報のペアを順番に取得
        // entry: 確定したセルの位置と状態のセットを順番に1つずつ保持
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            int cellIdx = entry.getKey();
            int val = entry.getValue();

            board[cellIdx] = val; // 盤面配列を更新
            if (difficultyMap[cellIdx] == -1) { // このセルが未推論かチェック
                difficultyMap[cellIdx] = level; // 初めて解けた場合は難易度を記録
            }
        }

        List<Region> nextRegions = new ArrayList<>();

        // 2. Level 0 (盤面ヒント由来) のRegionを【全再生成】
        // board[i] >= 0 (数字が見えているセル) のみ対象。
        // SAFE(-4) になったセルからは生成されないので、情報は増えない。
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) { // ヒントセル
                Region r = createRegionFromHint(i);
                if (r != null) {
                    nextRegions.add(r);
                }
            }
        }

        // // 3. Level > 0 (推論由来) のRegionを【更新して引き継ぐ】
        // // Lv1-2以降で生成される「仮想的なRegion」は盤面上の特定ヒントと紐付かないため、
        // // 既存のRegionから確定セルを取り除く「差分計算」で維持する必要がある。
        // // (現時点では Lv1-1 のみ実装のため、このループは実質機能しないが、拡張性のために残す)
        // for (Region r : activeRegions) {
        // if (r.getOriginLevel() == 0) {
        // continue; // Level 0 は上で再生成済みなのでスキップ
        // }

        // Set<Integer> currentCells = r.getCells();
        // Set<Integer> newCells = new HashSet<>();
        // int minesFound = 0;

        // // 既存Regionに含まれる各セルについて、最新の状態を確認
        // for (int cell : currentCells) {
        // int currentVal = board[cell];
        // if (currentVal == MINE) {
        // // まだ未確定なら、新しいRegionの構成要素として残す
        // newCells.add(cell);
        // } else if (currentVal == FLAGGED) {
        // // 既に地雷と確定している場合、このRegion内の地雷が見つかったとみなす
        // minesFound++;
        // }
        // // Safeになったセルは単にリストから除外される (地雷数は減らない)
        // }

        // // まだ未確定セルが残っている場合、新しいRegionとして登録
        // if (!newCells.isEmpty()) {
        // int newMines = r.getMines() - minesFound;

        // // 整合性補正
        // if (newMines < 0)
        // newMines = 0;
        // if (newMines > newCells.size())
        // newMines = newCells.size();

        // Region newRegion = new Region(newCells, newMines, r.getOriginLevel());
        // nextRegions.add(newRegion);
        // }
        // }

        // 4. マージ (重複排除)
        // 複数のヒントから全く同じ Region (例: {A, B}に地雷1個) が生成されることがあるため、
        // Set を経由してユニークな Region のみに絞り込む。
        Set<Region> uniqueRegions = new HashSet<>(nextRegions);
        activeRegions = new ArrayList<>(uniqueRegions);
    }

    /**
     * 指定されたインデックスの周囲8セルのインデックスを取得する。
     * 盤面外の座標は除外される。
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

    // --- 結果取得・デバッグ用メソッド ---

    /**
     * 計算された難易度マップを取得する。
     */
    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    /**
     * 現在の activeRegions の内容をコンソールに出力する (デバッグ用)。
     */
    public void printActiveRegions(String label) {
        System.out.println("--- Active Regions [" + label + "] ---");
        if (activeRegions.isEmpty()) {
            System.out.println(" (No active regions)");
        } else {
            for (int i = 0; i < activeRegions.size(); i++) {
                System.out.println(" [" + i + "]: " + activeRegions.get(i).toString());
            }
        }
        System.out.println("-----------------------------------");
    }

    /**
     * デバッグ用: 現在の盤面状態を表示する。
     * [ ? ] = 未確定 (MINE)
     * [ F ] = 地雷確定 (FLAGGED)
     * [ S ] = 安全確定 (SAFE)
     * [ N ] = 数字
     */
    public void printCurrentBoard(String label) {
        System.out.println("--- Board State [" + label + "] ---");
        for (int i = 0; i < board.length; i++) {
            int val = board[i];
            if (val == MINE) {
                System.out.print(" ? ");
            } else if (val == FLAGGED) {
                System.out.print(" F ");
            } else if (val == SAFE) {
                System.out.print(" S "); // 安全確定だが見えていない状態
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