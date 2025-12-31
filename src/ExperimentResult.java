/**
 * 1パズルの実験結果を保持するデータクラス
 */
public class ExperimentResult {
    // 識別情報
    public int mineCount; // 地雷数
    public int layoutId; // 地雷配置ID
    public int trialId; // 試行ID
    public int hintCount; // 最小ヒント数

    // k-Hint解析結果
    public int kMax; // k-hintの最大値
    public double kAvg; // k-hintの平均値
    public int[] kCounts; // k=1〜8 の各セル数

    // Technique解析結果
    public int lvMax; // 使用された最大レベル
    public double lvAvg; // レベルの平均値
    public int[] lvCounts; // Lv1〜Lv6 の各セル数
    public int unsolvedCount; // 未解決セル数

    public ExperimentResult() {
        this.kCounts = new int[8]; // k=1〜8
        this.lvCounts = new int[6]; // Lv1〜Lv6
    }

    /**
     * CSVヘッダー行を生成
     */
    public static String getCsvHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("mine_count,layout_id,trial_id,hint_count,");
        sb.append("k_max,k_avg,");
        for (int k = 1; k <= 8; k++) {
            sb.append("k" + k + "_count,");
        }
        sb.append("lv_max,lv_avg,");
        for (int lv = 1; lv <= 6; lv++) {
            sb.append("lv" + lv + "_count,");
        }
        sb.append("unsolved_count");
        return sb.toString();
    }

    /**
     * CSVデータ行を生成
     */
    public String toCsvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(mineCount).append(",");
        sb.append(layoutId).append(",");
        sb.append(trialId).append(",");
        sb.append(hintCount).append(",");
        sb.append(kMax).append(",");
        sb.append(String.format("%.2f", kAvg)).append(",");
        for (int k = 0; k < 8; k++) {
            sb.append(kCounts[k]).append(",");
        }
        sb.append(lvMax).append(",");
        sb.append(String.format("%.2f", lvAvg)).append(",");
        for (int lv = 0; lv < 6; lv++) {
            sb.append(lvCounts[lv]).append(",");
        }
        sb.append(unsolvedCount);
        return sb.toString();
    }
}