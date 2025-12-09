import java.util.*;

public class HintCountCalculator {

    // 定数定義 (ConstraintBuilderと連携)
    private static final int UNKNOWN = -1; // 未確定 (推論対象)
    private static final int IGNORE = -2; // 無視 (計算対象外)
    private static final int FLAGGED = -3; // 地雷確定 (定数として扱う)
    // HINTは 0以上 の整数

    private int[] currentBoard; // 現在の推論用盤面
    private final int[] completeBoard; // 正解盤面 (答え合わせ・更新用)
    private final int size;
    private int[] difficultyMap; // 結果格納用 (-1:未解決)

    // コンストラクタ
    public HintCountCalculator(int[] puzzleBoard, int[] completeBoard, int size) {
        this.size = size;
        this.completeBoard = completeBoard;
        // 盤面をコピーして作業用にする
        this.currentBoard = Arrays.copyOf(puzzleBoard, size * size);

        this.difficultyMap = new int[size * size];
        Arrays.fill(this.difficultyMap, -1);

        // 初期状態で開いている数字は 難易度0 として記録
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = 0;
            }
        }
    }

    /** メイン処理: 難易度解析を実行 */
    public void calculate() {
        // k=1 から最大ヒント数(あるいは適当な上限)までループ
        // ※今回は設計通り、途中で解けても k は戻さない
        int maxK = 1; // 通常のマインスイーパならこの程度で十分

        for (int k = 1; k <= maxK; k++) {
            // 全セルが解けていれば早期終了
            if (isAllSolved())
                break;

            // このラウンドを実行
            executeRound(k);
        }
    }

    /** 指定された k でのラウンド実行 (一括更新) */
    private void executeRound(int k) {
        // ステップA: 現在のヒントリストを取得
        List<Integer> allHints = getAllHints();

        // ヒント数が k 未満なら組み合わせ作れないので終了
        if (allHints.size() < k)
            return;

        // ステップB: 組み合わせ全列挙
        List<List<Integer>> subsets = enumerateSubsets(allHints, k);
        System.out.println(subsets);

        // ステップC: 探索と推論 (結果をプールする)
        Set<Integer> roundSolved = new HashSet<>();

        for (List<Integer> subset : subsets) {
            System.out.println(subset + "について");
            // 1. 有効判定: subset内に有効なヒント(周囲にUNKNOWNがある)が1つもなければスキップ
            if (!hasActiveHints(subset)) {
                System.out.println("周囲に有効な未知セルがありません");
                continue;
            }

            // 2. 局所推論: 解けたセル番号を取得
            Set<Integer> solvedInSubset = tryDeduce(subset);
            System.out.println("解けたセル番号：" + solvedInSubset);

            // 3. 蓄積
            roundSolved.addAll(solvedInSubset);
        }

        // ステップD: 盤面の一括更新
        if (!roundSolved.isEmpty()) {
            updateBoard(roundSolved, k);
        }
    }

    /** 局所的な推論を実行 */
    private Set<Integer> tryDeduce(List<Integer> subset) {
        Set<Integer> result = new HashSet<>();

        // 1. 一時盤面の作成
        int[] tempBoard = Arrays.copyOf(currentBoard, currentBoard.length);

        // 2. ノイズ除去
        // A. 選ばれていないヒントを IGNORE にする (FLAGGEDは残す)
        for (int i = 0; i < tempBoard.length; i++) {
            if (tempBoard[i] >= 0 && !subset.contains(i)) {
                tempBoard[i] = IGNORE;
            }
        }

        // B. 選ばれたヒントの「周囲」以外の UNKNOWN を IGNORE にする
        Set<Integer> activeArea = new HashSet<>();
        for (int h : subset) {
            for (int neighbor : getNeighbors(h)) {
                if (tempBoard[neighbor] == UNKNOWN) {
                    activeArea.add(neighbor);
                }
            }
        }

        for (int i = 0; i < tempBoard.length; i++) {
            if (tempBoard[i] == UNKNOWN && !activeArea.contains(i)) {
                tempBoard[i] = IGNORE;
            }
        }

        // 3. DLX実行
        // ConstraintBuilder側で FLAGGED の処理と IGNORE の処理を行う
        ConstraintBuilder builder = new ConstraintBuilder(tempBoard, size);
        ConstraintBuilder.Data data = builder.buildConstraints();

        // 空白がない(推論対象がない)場合はスキップ
        if (data.blanks == 0)
            return result;

        DancingLinks dlx = new DancingLinks(data.matrix, data.constraint);
        dlx.runSolver();

        int[] solvedRows = dlx.getAnswer();
        if (solvedRows.length == 0)
            return result;

        // 4. 結果判定 (正解盤面との照合)
        // DLXから返ってきた「解候補」が、正解と一致していれば「確定」とみなす
        for (int r : solvedRows) {
            int cellIdx = r / 2;
            boolean predictedMine = (r % 2 == 1); // 奇数行なら地雷予測

            // まだ難易度が確定していないセルについて
            if (difficultyMap[cellIdx] == -1) {
                boolean actuallyMine = (completeBoard[cellIdx] == -1);

                if (predictedMine == actuallyMine) {
                    result.add(cellIdx);
                }
            }
        }

        return result;
    }

    /** 盤面を更新し、難易度を記録する */
    private void updateBoard(Set<Integer> solvedCells, int k) {
        for (int cellIdx : solvedCells) {
            // 既に解決済みの場合はスキップ(重複防止)
            if (difficultyMap[cellIdx] != -1)
                continue;

            difficultyMap[cellIdx] = k;

            if (completeBoard[cellIdx] == -1) {
                currentBoard[cellIdx] = FLAGGED; // 地雷確定
            } else {
                currentBoard[cellIdx] = completeBoard[cellIdx]; // 安全なら数字オープン
            }
        }
    }

    // --- ユーティリティ ---

    // 全てのヒント(0以上)を取得
    private List<Integer> getAllHints() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                list.add(i);
            }
        }
        return list;
    }

    // 有効なヒント(周囲にUNKNOWNがある)が含まれているか判定
    private boolean hasActiveHints(List<Integer> subset) {
        for (int h : subset) {
            for (int nb : getNeighbors(h)) {
                if (currentBoard[nb] == UNKNOWN) {
                    return true;
                }
            }
        }
        return false;
    }

    // 全てのセルが解決済みかチェック
    private boolean isAllSolved() {
        for (int d : difficultyMap) {
            if (d == -1)
                return false;
        }
        return true;
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

    // 組み合わせ列挙 (再帰)
    private <T> List<List<T>> enumerateSubsets(List<T> list, int k) {
        List<List<T>> res = new ArrayList<>();
        // 計算量爆発防止のリミッター
        if (list.size() > 30 && k > 2) {
            // 実際にはヒント数は多いが、kが大きい場合の組み合わせは膨大になるため
            // 本来は近傍探索などの枝刈りが必要だが、今回は簡易的なリミットで対応
        }
        backtrack(list, k, 0, new ArrayList<>(), res);
        return res;
    }

    private <T> void backtrack(List<T> list, int k, int start, List<T> cur, List<List<T>> res) {
        if (cur.size() == k) {
            res.add(new ArrayList<>(cur));
            return;
        }
        // 安全策：組み合わせ数が多すぎる場合は打ち切る
        if (res.size() > 5000)
            return;

        for (int i = start; i < list.size(); i++) {
            cur.add(list.get(i));
            backtrack(list, k, i + 1, cur, res);
            cur.remove(cur.size() - 1);
            if (res.size() > 5000)
                return;
        }
    }

    // 結果取得
    public int[] getDifficultyMap() {
        return difficultyMap;
    }
}