import java.util.*;

/**
 * 世代別分類ソルバー
 * ヒント間の集合演算と限界値チェックを繰り返し、推論の深さ(世代)に基づいて難易度を判定する。
 */
public class GenerationSolver {

    // 定数定義 (他のクラスと合わせる)
    private static final int UNKNOWN = -1;
    private static final int IGNORE = -2;
    private static final int FLAGGED = -3;

    private static final int MAX_GEN = 10; // 探索する最大世代数

    private int[] initialPuzzle;
    private int[] completeBoard;
    private int size;
    private int[] difficultyMap;

    public GenerationSolver(int[] puzzle, int[] completeBoard, int size) {
        this.initialPuzzle = Arrays.copyOf(puzzle, puzzle.length);
        this.completeBoard = completeBoard;
        this.size = size;
        this.difficultyMap = new int[puzzle.length];
        Arrays.fill(this.difficultyMap, -1); // 初期値 -1 (未解決)
    }

    /**
     * 解析を実行する
     */
    public void analyze() {
        int[] currentBoard = Arrays.copyOf(initialPuzzle, initialPuzzle.length);
        Set<VirtualHint> pool = new HashSet<>();

        // 初期状態で開示されているヒントは難易度0
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) {
                difficultyMap[i] = 0;
            }
        }

        // メインループ: 盤面に変化がある限り繰り返す
        boolean changed = true;
        while (changed) {
            changed = false;

            // Step 1: Gen 0 プールの再構築 (盤面更新に伴いリセット)
            pool.clear();
            pool.addAll(generateGen0Hints(currentBoard));

            // Step 2: 世代別探索ループ
            for (int targetGen = 0; targetGen <= MAX_GEN; targetGen++) {

                // Phase A: 解決フェーズ (Solve Check)
                if (checkAndSolve(pool, currentBoard, targetGen)) {
                    changed = true;
                    break; // 盤面が変わったので Step 1 からやり直し
                }

                // Phase B: 拡張フェーズ (Expansion)
                // この世代で解決しなかったので、次の世代のヒント生成を試みる
                boolean expansionHit = expandPool(pool, currentBoard, targetGen);

                // 拡張フェーズ内で「即時確定」が発生した場合も解決扱いとする
                // (expandPool内で盤面更新が行われた場合、trueが返るように実装する)
                if (expansionHit) {
                    changed = true;
                    break; // 盤面が変わったので Step 1 からやり直し
                }
            }

            // 全世代探索しても変化がなければ終了
            if (!changed) {
                break;
            }
        }
    }

    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    // --- 内部ロジック ---

    /**
     * 現在の盤面から Gen 0 ヒントを生成する
     */
    private List<VirtualHint> generateGen0Hints(int[] board) {
        List<VirtualHint> hints = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) { // 数字セル
                Set<Integer> neighbors = getUnknownNeighbors(i, board);
                int flagCount = countFlaggedNeighbors(i, board);
                int remainingMines = board[i] - flagCount;

                // 未確定セルが残っている場合のみヒントとして採用
                if (!neighbors.isEmpty()) {
                    hints.add(new VirtualHint(neighbors, remainingMines, 0, null));
                }
            }
        }
        return hints;
    }

    /**
     * Phase A: プール内のヒントで確定できるかチェック
     */
    private boolean checkAndSolve(Set<VirtualHint> pool, int[] board, int difficultyLevel) {
        boolean solved = false;

        for (VirtualHint hint : pool) {
            // 全埋め (残り地雷数 == 未確定セル数) -> 全部 FLAGGED
            if (hint.mines == hint.cells.size()) {
                for (int idx : hint.cells) {
                    if (board[idx] == UNKNOWN) {
                        board[idx] = FLAGGED;
                        recordDifficulty(idx, difficultyLevel);
                        solved = true;
                    }
                }
            }
            // 全安全 (残り地雷数 == 0) -> 全部 SAFE
            else if (hint.mines == 0) {
                for (int idx : hint.cells) {
                    if (board[idx] == UNKNOWN) {
                        revealSafeCell(idx, board);
                        recordDifficulty(idx, difficultyLevel);
                        solved = true;
                    }
                }
            }
        }
        return solved;
    }

    /**
     * Phase B: 次の世代のヒントを生成 & 拡張差分ロジックによる即時確定チェック
     */
    private boolean expandPool(Set<VirtualHint> pool, int[] board, int targetGen) {
        List<VirtualHint> currentList = new ArrayList<>(pool);
        List<VirtualHint> newHints = new ArrayList<>();
        boolean solved = false;

        // ペアリング総当たり
        // (注: 実用上は共通セルを持つペアのみに絞るべきだが、ここでは設計書のロジックを忠実に実装する)
        for (int i = 0; i < currentList.size(); i++) {
            for (int j = 0; j < currentList.size(); j++) {
                if (i == j)
                    continue;
                VirtualHint A = currentList.get(i);
                VirtualHint B = currentList.get(j);

                // 生成されるヒントが次の世代 (targetGen + 1) になるペアのみ処理
                // 設計書の定義: newGen = max(A.gen, B.gen) + 1
                int nextGen = Math.max(A.generation, B.generation) + 1;
                if (nextGen != targetGen + 1) {
                    continue;
                }

                // 共通セルがないペアはスキップ (計算量抑制の第一歩)
                if (!hasIntersection(A.cells, B.cells)) {
                    continue;
                }

                // 拡張差分ロジック適用
                SolveResult result = applyIntersectionLogic(A, B, board, nextGen);

                if (result.type == SolveResultType.SOLVED) {
                    // 即時確定パターン
                    // 盤面更新は applyIntersectionLogic 内ではなくここで行うのが行儀が良いが、
                    // 情報を返すのが複雑になるため、resultに確定情報を持たせる
                    for (Map.Entry<Integer, Integer> entry : result.determinedCells.entrySet()) {
                        int idx = entry.getKey();
                        int val = entry.getValue();
                        if (board[idx] == UNKNOWN) {
                            if (val == FLAGGED) {
                                board[idx] = FLAGGED;
                            } else {
                                revealSafeCell(idx, board); // 正解盤面から値コピー
                            }
                            // 難易度は「現在の世代(親の世代の最大)」とする
                            recordDifficulty(idx, Math.max(A.generation, B.generation));
                            solved = true;
                        }
                    }
                    if (solved)
                        return true; // 確定したら即リターンして再スタート
                } else if (result.type == SolveResultType.NEW_HINT) {
                    newHints.add(result.newHint);
                }
            }
        }

        // 新しいヒントをプールに追加
        boolean added = false;
        for (VirtualHint h : newHints) {
            if (!pool.contains(h)) {
                pool.add(h);
                added = true;
            }
        }

        return false; // 確定はしなかった
    }

    /**
     * コアロジック: 拡張差分判定
     */
    private SolveResult applyIntersectionLogic(VirtualHint A, VirtualHint B, int[] board, int newGen) {
        // 1. 共通部分チェック (呼び出し元でもやっているが念のため)
        Set<Integer> common = new HashSet<>(A.cells);
        common.retainAll(B.cells);
        if (common.isEmpty())
            return SolveResult.none();

        // 2. 領域分割
        Set<Integer> onlyA = new HashSet<>(A.cells);
        onlyA.removeAll(common);

        Set<Integer> onlyB = new HashSet<>(B.cells);
        onlyB.removeAll(common);

        // 3. 差分計算
        int diffVal = B.mines - A.mines;
        // 論理: (Mines in OnlyB) - (Mines in OnlyA) = diffVal

        // 4. 判定分岐

        // パターン①: 限界値チェック (即時確定)
        int maxDiff = onlyB.size() - 0; // B全埋め - A全安全
        int minDiff = 0 - onlyA.size(); // B全安全 - A全埋め

        if (diffVal == maxDiff) {
            // OnlyB -> FLAGGED, OnlyA -> SAFE
            Map<Integer, Integer> det = new HashMap<>();
            for (int idx : onlyB)
                det.put(idx, FLAGGED);
            for (int idx : onlyA)
                det.put(idx, 0); // 0は便宜上SAFE扱い
            return SolveResult.solved(det);
        }

        if (diffVal == minDiff) {
            // OnlyB -> SAFE, OnlyA -> FLAGGED
            Map<Integer, Integer> det = new HashMap<>();
            for (int idx : onlyB)
                det.put(idx, 0);
            for (int idx : onlyA)
                det.put(idx, FLAGGED);
            return SolveResult.solved(det);
        }

        // パターン②: 包含関係 (仮想ヒント生成)
        if (onlyA.isEmpty()) {
            // A ⊆ B -> OnlyB の地雷数は diffVal
            if (!onlyB.isEmpty() && diffVal >= 0 && diffVal <= onlyB.size()) {
                return SolveResult.newHint(new VirtualHint(onlyB, diffVal, newGen, null));
            }
        }

        if (onlyB.isEmpty()) {
            // B ⊆ A -> OnlyA の地雷数は -diffVal
            int val = -diffVal;
            if (!onlyA.isEmpty() && val >= 0 && val <= onlyA.size()) {
                return SolveResult.newHint(new VirtualHint(onlyA, val, newGen, null));
            }
        }

        // パターン③: 決定不能
        return SolveResult.none();
    }

    // --- ユーティリティ ---

    private Set<Integer> getUnknownNeighbors(int idx, int[] board) {
        Set<Integer> set = new HashSet<>();
        List<Integer> neighbors = getNeighbors(idx);
        for (int nb : neighbors) {
            if (board[nb] == UNKNOWN) {
                set.add(nb);
            }
        }
        return set;
    }

    private int countFlaggedNeighbors(int idx, int[] board) {
        int count = 0;
        List<Integer> neighbors = getNeighbors(idx);
        for (int nb : neighbors) {
            if (board[nb] == FLAGGED) {
                count++;
            }
        }
        return count;
    }

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

    private boolean hasIntersection(Set<Integer> s1, Set<Integer> s2) {
        // パフォーマンスのための簡易チェック: ループしてcontains確認
        if (s1.size() > s2.size()) {
            for (Integer i : s2)
                if (s1.contains(i))
                    return true;
        } else {
            for (Integer i : s1)
                if (s2.contains(i))
                    return true;
        }
        return false;
    }

    private void revealSafeCell(int idx, int[] board) {
        if (completeBoard[idx] == -1) {
            // エラー: 安全と判断したが実際は地雷だった
            // ここでは実装上ありえないはずだが、ロジックバグ検出用に残す
            System.err.println("LOGIC ERROR: Safe determined but is Mine at " + idx);
        } else {
            board[idx] = completeBoard[idx];
        }
    }

    private void recordDifficulty(int idx, int level) {
        if (difficultyMap[idx] == -1) {
            difficultyMap[idx] = level;
        }
    }

    // --- 内部クラス: SolveResult ---
    private enum SolveResultType {
        NONE, SOLVED, NEW_HINT
    }

    private static class SolveResult {
        SolveResultType type;
        Map<Integer, Integer> determinedCells;
        VirtualHint newHint;

        static SolveResult none() {
            SolveResult r = new SolveResult();
            r.type = SolveResultType.NONE;
            return r;
        }

        static SolveResult solved(Map<Integer, Integer> det) {
            SolveResult r = new SolveResult();
            r.type = SolveResultType.SOLVED;
            r.determinedCells = det;
            return r;
        }

        static SolveResult newHint(VirtualHint h) {
            SolveResult r = new SolveResult();
            r.type = SolveResultType.NEW_HINT;
            r.newHint = h;
            return r;
        }
    }
}
