import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

// ランダムなパズルを生成
public class PuzzleGenerator {
    // n×n の盤面と b 個の地雷を指定してパズル生成
    public static int[] generatePuzzle(int n, int b) {
        int size = n * n;
        int[] board = new int[size];

        // 1. セル番号を作成
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < n * n; i++) {
            cells.add(i);
        }

        // 1. 地雷をランダム配置 (シャッフル → 先頭 b 個を地雷セルに)
        Collections.shuffle(cells);
        for (int i = 0; i < b; i++) {
            int index = cells.get(i);
            board[index] = -1; // 爆弾： -1
        }

        // 3. 地雷でない各セルの隣接地雷数を計算
        // 隣接するセル座標リスト
        int[] dx = { -1, -1, -1, 0, 0, 1, 1, 1 };
        int[] dy = { -1, 0, 1, -1, 1, -1, 0, 1 };

        for (int i = 0; i < size; i++) {
            if (board[i] == -1) // 地雷セルは何もしない
                continue;
            int row = i / n; // 行番号
            int col = i % n; // 列番号
            int count = 0; // 地雷数カウンタ

            for (int k = 0; k < 8; k++) {
                int ni = row + dx[k];
                int nj = col + dy[k];
                if (ni >= 0 && ni < n && nj >= 0 && nj < n) { // 盤面の端を除外
                    int neighborIndex = ni * n + nj; // 隣接セルの座標から番号を計算
                    if (board[neighborIndex] == -1) {
                        count++;
                    }
                }
            }
            board[i] = count;
        }

        // 4. 盤面(ヒント付き)を返す
        return board;
    }

    public static void printBoard(int[] board, int n) {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == -1) {
                System.out.print(" * ");
            } else {
                System.out.printf(" %d ", board[i]);
            }
            if ((i + 1) % n == 0)
                System.out.println();
        }
    }
}
