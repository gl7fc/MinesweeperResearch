import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// 実験用の盤面を生成する

public class ExperimentPuzzleGenerator {
    // 実験パラメータ
    private static final int SIZE = 10;
    private static final int MINE_COUNTS = 30;
    private static final int LAYOUTS = 1;
    private static final int PUZZLES_PER_LAYOUT = 2;

    // ディレクトリ設定

    // 出力ディレクトリ
    private static final String OUTPUT_DIR = generateOutputDir();

    // Pythonスクリプトのディレクトリ
    private static final String PYTHON_SCRIPT_DIR = "src/scripts/";

    private static String generateOutputDir() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd_HHmmss");
        return "results_" + now.format(formatter) + "/";
    }

}