import java.util.*;

/**
 * Lv.1: 単一ヒント解決 (Basic Single-Hint Logic) ソルバー
 * ファイル名: SimpleSolver_v1.java
 */
public class SimpleSolver {

    // 定数定義 (HintCountCalculator等と統一)
    public static final int UNKNOWN = -1;
    public static final int IGNORE = -2;
    public static final int FLAGGED = -3;

    private int size;
    private int[] completeBoard; // 正解盤面 (SAFE確定時の値参照用)

    public SimpleSolver(int size, int[] completeBoard) {
        this.size = size;
        this.completeBoard = completeBoard;
    }

    /**
     * 現在の盤面に対してLv.1の推論を行う
     * 
     * @param currentBoard 現在の盤面状態
     * @return 確定したセル情報 (インデックス -> 確定後の値)
     */
    public Map<Integer, Integer> solve(int[] currentBoard) {
        Map<Integer, Integer> result = new HashMap<>();
        List<HintInfo> hints = new ArrayList<>();

        // 1. ヒントセルの抽出とHintInfo生成
        for (int i = 0; i < currentBoard.length; i++) {
            if (currentBoard[i] >= 0) { // ヒント数字 (0-8)
                HintInfo info = new HintInfo(i, currentBoard[i], currentBoard, size);
                hints.add(info);
            }
        }

        // 2. 各ヒントについて判定ロジック適用
        for (HintInfo hint : hints) {
            // UNKNOWNな隣接セルがなければスキップ
            if (hint.unknownNeighbors.isEmpty()) {
                continue;
            }

            // A. All-Mines Rule (残り全埋め)
            // 例: 「3」の周りに未開封が3つ → 全部地雷
            if (hint.remainingMines == hint.unknownNeighbors.size()) {
                for (int idx : hint.unknownNeighbors) {
                    // 地雷確定 (FLAGGED)
                    result.put(idx, FLAGGED);
                }
            }
            // B. All-Safe Rule (残り全安全)
            // 例: 「1」の周りに既に旗が1つ → 残りは全部安全
            else if (hint.remainingMines == 0) {
                for (int idx : hint.unknownNeighbors) {
                    // 安全確定 (正解盤面の値を公開)
                    int trueVal = completeBoard[idx];
                    // 万が一地雷だったらエラーだが、ここでは正解盤面を信じる
                    result.put(idx, trueVal);
                }
            }
        }

        return result;
    }

    // --- ヘルパークラス ---
    private static class HintInfo {
        int index; // 未使用だがデバッグ用に保持
        int value;
        Set<Integer> unknownNeighbors;
        int flagCount;
        int remainingMines;

        HintInfo(int index, int value, int[] board, int size) {
            this.index = index;
            this.value = value;
            this.unknownNeighbors = new HashSet<>();
            this.flagCount = 0;

            // 周囲の情報を収集
            List<Integer> neighbors = getNeighbors(index, size);
            for (int nb : neighbors) {
                int state = board[nb];
                if (state == UNKNOWN) {
                    this.unknownNeighbors.add(nb);
                } else if (state == FLAGGED) {
                    this.flagCount++;
                }
            }
            this.remainingMines = value - flagCount;
        }
    }

    // 周囲8セルの座標取得
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
}