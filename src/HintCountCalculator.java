import java.util.*;

/**
 * HintCountCalculator.md の仕様に基づく難易度解析クラス
 * バージョン: v1
 */
public class HintCountCalculator {

    // 定数定義 (ConstraintBuilder/MDの仕様に準拠)
    private static final int MINE = -1; // UNKNOWN (未確定変数)
    private static final int IGNORE = -2; // 計算対象外
    private static final int FLAGGED = -3; // 地雷確定
    // 0以上はヒント数字

    private int[] initialPuzzle; // 初期盤面 (問題)
    private int[] completeBoard; // 正解盤面
    private int size;
    private int[] difficultyMap; // 結果格納用 (各セルの難易度k)

    // ★追加: 各セルの確定に必要なヒントのリストを保持
    private Map<Integer, Set<Integer>> requiredHintsMap;

    /**
     * コンストラクタ
     * 
     * @param puzzle   問題となる初期盤面 (未確定セルは-1)
     * @param solution 正解の完全な盤面 (地雷は-1)
     * @param size     盤面の幅/高さ
     */
    public HintCountCalculator(int[] puzzle, int[] solution, int size) {
        this.initialPuzzle = Arrays.copyOf(puzzle, puzzle.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[puzzle.length];
        Arrays.fill(this.difficultyMap, -1); // 初期値 -1 (未解決)

        // ★追加: 必要ヒントマップの初期化
        this.requiredHintsMap = new HashMap<>();
    }

    /**
     * 難易度解析を実行する
     */
    public void calculate() {
        // 現在の盤面状態 (解析が進むにつれて更新される)
        int[] currentBoard = Arrays.copyOf(initialPuzzle, initialPuzzle.length);

        // 初期状態で開示されているヒントは難易度0
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = 0;
                // ★追加: 初期ヒントは必要ヒントなし（既に開示されているため）
                requiredHintsMap.put(i, new HashSet<>());
            }
        }

        // ヒント数 k を 1 から最大値(例えば8)まで増やしながら探索
        int maxK = 8;
        for (int k = 1; k <= maxK; k++) {
            // 全てのセルが解決済みなら終了
            if (isAllSolved(currentBoard)) {
                break;
            }

            // ラウンド処理実行
            executeRound(k, currentBoard);
        }
    }

    /**
     * 解析結果のマップを取得
     */
    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    /**
     * ★追加: 指定されたセルの確定に必要なヒントのリストを取得
     * 
     * @param cellIndex 対象セルのインデックス
     * @return 必要なヒントのインデックス集合（セルが未解析の場合は空集合）
     */
    public Set<Integer> getRequiredHints(int cellIndex) {
        if (requiredHintsMap.containsKey(cellIndex)) {
            return new HashSet<>(requiredHintsMap.get(cellIndex)); // コピーを返す
        }
        return new HashSet<>(); // 未解析の場合は空集合
    }

    /**
     * ★追加: 全セルの必要ヒントマップを取得（デバッグ用）
     */
    public Map<Integer, Set<Integer>> getAllRequiredHints() {
        return new HashMap<>(requiredHintsMap);
    }

    /**
     * 全てのセルが確定済み（UNKNOWNがない）か判定
     */
    private boolean isAllSolved(int[] board) {
        for (int val : board) {
            if (val == MINE)
                return false; // まだ未確定がある
        }
        return true;
    }

    /**
     * 指定されたヒント数 k におけるラウンド処理
     */
    private void executeRound(int k, int[] currentBoard) {
        // 1. ヒントセルの収集
        List<Integer> hintIndices = new ArrayList<>();
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                hintIndices.add(i);
            }
        }

        // 2. 組み合わせ生成と推論
        Set<Integer> roundSolved = new HashSet<>(); // このラウンドで確定したセル(重複防止)

        // ★追加: このラウンドで確定したセルとその必要ヒント集合を記録
        Map<Integer, Set<Integer>> roundRequiredHints = new HashMap<>();

        // k個のヒントの組み合わせを全列挙
        Iterable<List<Integer>> combinations = getCombinations(hintIndices, k);

        for (List<Integer> subset : combinations) {
            // 有効性チェック: 部分集合の周囲にUNKNOWNがなければスキップ
            // 同時に , この部分集合に関連するUNKNOWNセル(ターゲット)を特定
            Set<Integer> neighbors = getUnknownNeighbors(subset, currentBoard);
            if (neighbors.isEmpty()) {
                continue;
            }

            // 局所解析 (tryDeduce相当)
            Map<Integer, Integer> deduced = tryDeduce(subset, neighbors, currentBoard);

            // 確定情報を収集 (roundSolvedに追加)
            for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
                int cellIdx = entry.getKey();

                // 既にこのラウンドで処理済みでなければ
                if (!roundSolved.contains(cellIdx)) {
                    roundSolved.add(cellIdx);

                    // 難易度記録 (初めて解けた場合のみ)
                    if (difficultyMap[cellIdx] == -1) {
                        difficultyMap[cellIdx] = k;
                        // ★追加: 必要ヒント集合を記録
                        roundRequiredHints.put(cellIdx, new HashSet<>(subset));
                    }
                }
            }
        }

        // 3. 必要ヒントマップに記録
        for (Map.Entry<Integer, Set<Integer>> entry : roundRequiredHints.entrySet()) {
            int cellIdx = entry.getKey();
            Set<Integer> hints = entry.getValue();

            // まだ記録されていないか、より少ないヒント数の場合のみ更新
            if (!requiredHintsMap.containsKey(cellIdx) ||
                    hints.size() < requiredHintsMap.get(cellIdx).size()) {
                requiredHintsMap.put(cellIdx, hints);
            }
        }

        // 4. 盤面の一括更新
        for (int cellIdx : roundSolved) {
            int trueVal = completeBoard[cellIdx];
            if (trueVal == -1) {
                currentBoard[cellIdx] = FLAGGED; // 地雷確定
            } else {
                currentBoard[cellIdx] = trueVal; // 安全確定(数字公開)
            }
        }
    }

    /**
     * 局所解析を行い , 確定したセルとその値を返す
     * 
     * @return Map<セルインデックス, 確定した値(FLAGGED or 数字)>
     */
    private Map<Integer, Integer> tryDeduce(List<Integer> subset, Set<Integer> targetUnknowns, int[] currentBoard) {
        Map<Integer, Integer> result = new HashMap<>();

        // 1. 一時盤面の作成とマスキング
        int[] tempBoard = Arrays.copyOf(currentBoard, currentBoard.length);

        // ヒントのマスク: subset以外をIGNOREにする
        for (int i = 0; i < tempBoard.length; i++) {
            if (tempBoard[i] >= 0) {
                if (!subset.contains(i)) {
                    tempBoard[i] = IGNORE;
                }
            }
            // UNKNOWNのマスク: targetUnknowns以外をIGNOREにする
            else if (tempBoard[i] == MINE) {
                if (!targetUnknowns.contains(i)) {
                    tempBoard[i] = IGNORE;
                }
            }
            // FLAGGEDはそのまま残す
        }

        // 2. 制約行列の生成
        ConstraintBuilder cb = new ConstraintBuilder(tempBoard, size);
        ConstraintBuilder.Data data = cb.buildConstraints();

        if (data.blanks == 0)
            return result; // 変数がなければ何もしない

        // 基本的な整合性チェック（解が0個の場合は矛盾しているのでスキップ）
        DancingLinks dlxChecker = new DancingLinks(data.matrix, data.constraint);
        dlxChecker.runSolver();
        if (dlxChecker.SolutionsCount(data.blanks) == 0) {
            return result;
        }

        // 3. プロービングによる確定判定
        // 行ラベルから「どの行が」「どのセルの」「どの状態(Safe/Mine)か」をマッピング
        // rowLabels[r] -> "12#0" (セル12がSafe), "12#1" (セル12がMine)
        String[] rowLabels = cb.getRowLabels();
        Map<Integer, Integer> cellToSafeRow = new HashMap<>();
        Map<Integer, Integer> cellToMineRow = new HashMap<>();

        for (int r = 0; r < rowLabels.length; r++) {
            String label = rowLabels[r];
            if (label.startsWith("ROW"))
                continue; // 予備行など
            String[] parts = label.split("#");
            int idx = Integer.parseInt(parts[0]);
            int type = Integer.parseInt(parts[1]); // 0=Safe, 1=Mine

            if (type == 0)
                cellToSafeRow.put(idx, r);
            else
                cellToMineRow.put(idx, r);
        }

        // ターゲットとなる各未知セルについて検証
        for (int cellIdx : targetUnknowns) {
            if (!cellToSafeRow.containsKey(cellIdx) || !cellToMineRow.containsKey(cellIdx)) {
                continue;
            }

            int rSafe = cellToSafeRow.get(cellIdx);
            int rMine = cellToMineRow.get(cellIdx);

            // Safeである可能性はあるか？ (Mineの行を無効化してDLX実行)
            boolean canBeSafe = isScenarioPossible(data.matrix, data.constraint, rMine);

            // Mineである可能性はあるか？ (Safeの行を無効化してDLX実行)
            boolean canBeMine = isScenarioPossible(data.matrix, data.constraint, rSafe);

            // 判定
            if (canBeSafe && !canBeMine) {
                // Must be Safe
                // 正解盤面と照合
                if (completeBoard[cellIdx] != -1) {
                    result.put(cellIdx, completeBoard[cellIdx]);
                }
            } else if (!canBeSafe && canBeMine) {
                // Must be Mine
                // 正解盤面と照合
                if (completeBoard[cellIdx] == -1) {
                    result.put(cellIdx, FLAGGED);
                }
            }
        }

        return result;
    }

    /**
     * 特定のシナリオが可能か判定する (指定された行を禁止した場合に解があるか)
     * 
     * @param forbiddenRow 選択してはいけない行（事実上削除する行）
     */
    private boolean isScenarioPossible(int[][] originalMatrix, int[] constraint, int forbiddenRow) {
        // 行列のディープコピーを作成
        int rows = originalMatrix.length;
        int cols = originalMatrix[0].length;
        int[][] matrix = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            // 禁止行はすべて0にして無効化する (DLXは1のノードしかリンクしないため)
            if (i == forbiddenRow) {
                // new int[cols] (all zeros)
            } else {
                System.arraycopy(originalMatrix[i], 0, matrix[i], 0, cols);
            }
        }

        DancingLinks dlx = new DancingLinks(matrix, constraint);
        dlx.runSolver();
        // 解が1つ以上あれば , そのシナリオは「可能」
        // ConstraintBuilderのblanks数は渡すが , この判定には影響しないため適当で良いが ,
        // 既存実装に合わせるならCBから取ったほうが良い. ただしSolutionsCountの実装依存.
        // ここでは便宜上0を渡すが , DancingLinksのコードを見る限り引数は使われていないか ,
        // 単なるカウンター返却用メソッドなら問題ない.
        // UploadされたDancingLinksを見ると引数blanksは使われていない(return solutionsCountのみ).
        return dlx.SolutionsCount(0) > 0;
    }

    /**
     * 指定されたヒント群の周囲にあるUNKNOWNセルを取得
     */
    private Set<Integer> getUnknownNeighbors(List<Integer> subset, int[] board) {
        Set<Integer> neighbors = new HashSet<>();
        for (int hintIdx : subset) {
            // 周囲8方向
            List<Integer> adj = getNeighbors(hintIdx);
            for (int nb : adj) {
                if (board[nb] == MINE) { // UNKNOWN
                    neighbors.add(nb);
                }
            }
        }
        return neighbors;
    }

    /**
     * 周囲のセル座標を取得 (ConstraintBuilderとロジック共有)
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

    /**
     * 組み合わせ生成 (n C k)
     */
    private Iterable<List<Integer>> getCombinations(List<Integer> list, int k) {
        List<List<Integer>> result = new ArrayList<>();
        combine(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private void combine(List<Integer> list, int k, int start, List<Integer> current, List<List<Integer>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combine(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}