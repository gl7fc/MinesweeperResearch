import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * ランダムなパズルを生成
 */
public class PuzzleGenerator {

    // 隣接するセル座標リスト
    private static final int[] dx = { -1, -1, -1, 0, 0, 1, 1, 1 };
    private static final int[] dy = { -1, 0, 1, -1, 1, -1, 0, 1 };

    /**
     * n×n の盤面と b 個の地雷を指定してパズル生成
     * 周囲が全て地雷のセルがないことを保証する
     */
    public static int[] generatePuzzle(int n, int b) {
        int maxAttempts = 1000; // 無限ループ防止

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int[] board = generateBoard(n, b);

            // 検証：周囲が全て地雷のセルがないか
            if (isValidPlacement(board, n)) {
                return board;
            }

            // 無効な配置だった場合、再試行
            System.out.println(
                    "  [PuzzleGenerator] Invalid placement detected, retrying... (attempt " + (attempt + 1) + ")");
        }

        // 最大試行回数を超えた場合、警告を出して最後に生成した盤面を返す
        System.out.println(
                "  [PuzzleGenerator] Warning: Could not find valid placement after " + maxAttempts + " attempts");
        return generateBoard(n, b);
    }

    /**
     * 地雷をランダム配置して盤面を生成（検証なし）
     */
    private static int[] generateBoard(int n, int b) {
        int size = n * n;
        int[] board = new int[size];

        // 1. セル番号を作成
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cells.add(i);
        }

        // 2. 地雷をランダム配置 (シャッフル → 先頭 b 個を地雷セルに)
        Collections.shuffle(cells);
        for (int i = 0; i < b; i++) {
            int index = cells.get(i);
            board[index] = -1; // 爆弾： -1
        }

        // 3. 地雷でない各セルの隣接地雷数を計算
        for (int i = 0; i < size; i++) {
            if (board[i] == -1) {
                continue; // 地雷セルは何もしない
            }
            int row = i / n;
            int col = i % n;
            int count = 0;

            for (int k = 0; k < 8; k++) {
                int ni = row + dx[k];
                int nj = col + dy[k];
                if (ni >= 0 && ni < n && nj >= 0 && nj < n) {
                    int neighborIndex = ni * n + nj;
                    if (board[neighborIndex] == -1) {
                        count++;
                    }
                }
            }
            board[i] = count;
        }

        return board;
    }

    /**
     * 盤面が有効かどうかを検証する
     * 無効な条件：周囲のセルが全て地雷のセルが存在する
     */
    private static boolean isValidPlacement(int[] board, int n) {
        int size = n * n;

        for (int i = 0; i < size; i++) {
            // 地雷セルはスキップ
            if (board[i] == -1) {
                continue;
            }

            int row = i / n;
            int col = i % n;

            // 隣接セルの数をカウント
            int neighborCount = 0;
            int mineNeighborCount = 0;

            for (int k = 0; k < 8; k++) {
                int ni = row + dx[k];
                int nj = col + dy[k];
                if (ni >= 0 && ni < n && nj >= 0 && nj < n) {
                    neighborCount++;
                    int neighborIndex = ni * n + nj;
                    if (board[neighborIndex] == -1) {
                        mineNeighborCount++;
                    }
                }
            }

            // 周囲のセルが全て地雷の場合は無効
            if (neighborCount > 0 && neighborCount == mineNeighborCount) {
                System.out.println("  [PuzzleGenerator] Cell " + i + " (row=" + row + ", col=" + col +
                        ") has all neighbors as mines (" + mineNeighborCount + "/" + neighborCount + ")");
                return false;
            }
        }

        return true;
    }

    /**
     * 盤面を表示する
     */
    public static void printBoard(int[] board, int n) {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == -1) {
                System.out.print(" * ");
            } else {
                System.out.printf(" %d ", board[i]);
            }
            if ((i + 1) % n == 0) {
                System.out.println();
            }
        }
    }
}