public class Main {
    public static void main(String[] args) {
        int size = 5;
        int bombs = 10;
        int puzzles = 1;

        // サイズ・地雷数を指定してランダムなパズルを生成
        // ここでの board は「全てのセルが開かれた正解盤面」
        int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
        System.out.println("===== 初期盤面 (正解) =====");
        PuzzleGenerator.printBoard(board, size);

        for (int i = 0; i < puzzles; i++) {
            // パズルのヒントを最小化（問題作成）
            // ここでの puzzle は「ヒントが隠された問題盤面」
            PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
            int[] puzzle = pm.minimizeHints();

            System.out.println("\n===== 生成された問題 (盤面" + (i + 1) + ") =====");
            PuzzleGenerator.printBoard(puzzle, size);

            // ---------------------------------------------------
            // HintDifficultySolver を使用して難易度を判定する処理を追加
            // ---------------------------------------------------
            System.out.println("\n--- 難易度解析実行中... ---");

            // ★変更点: puzzle(問題) と board(正解) の両方を渡す
            // puzzle: ヒント以外は隠された状態
            // board: すべての数字と地雷がわかっている状態（答え合わせ用）
            HintDifficultySolver solver = new HintDifficultySolver(puzzle, board, size);

            // 解析実行
            solver.solve();

            // 結果取得
            int[] difficulties = solver.getHintRequired();

            // 結果表示
            System.out.println("--- 解析結果 ---");
            System.out.println(" [数字]: そのセルを解くのに必要なヒント数");
            System.out.println(" [ . ]: 元から表示されていたヒント");
            System.out.println(" [ * ]: 論理的に確定できなかったセル（運ゲー）\n");

            printAnalysis(puzzle, difficulties, size);
        }
    }

    // 解析結果を整形して表示するヘルパーメソッド
    private static void printAnalysis(int[] board, int[] difficulties, int size) {
        for (int i = 0; i < size * size; i++) {
            // 元の盤面でヒント(0以上)だった場所
            if (board[i] != -1) {
                System.out.printf(" . ");
            }
            // 空白セルだった場所
            else {
                int level = difficulties[i];
                if (level == -1) {
                    System.out.print(" * "); // 解けなかった場所
                } else {
                    System.out.printf(" %d ", level); // 難易度
                }
            }

            // 行末で改行
            if ((i + 1) % size == 0) {
                System.out.println();
            }
        }
    }
}