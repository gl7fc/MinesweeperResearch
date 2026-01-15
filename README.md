# Minesweeper Difficulty Analyzer

マインスイーパのパズル生成および各セルの「論理的な難易度」を定量的に解析するJava製ツールです。
Knuth's Algorithm X (Dancing Links) を用いた制約充足ソルバをバックエンドに使用し、パズルが論理的に解けるかどうか、また解くためにどのような思考やテクニックが必要かを判定します。

---

## 目次

1. [概要](#概要)
2. [主な機能](#主な機能)
3. [システムアーキテクチャ](#システムアーキテクチャ)
4. [難易度レベル定義](#難易度レベル定義)
5. [クラス詳細](#クラス詳細)
   - [Main.java](#mainjava)
   - [PuzzleGenerator.java](#puzzlegeneratorjava)
   - [PuzzleMinimizer.java](#puzzleminimizerjava)
   - [ConstraintBuilder.java](#constraintbuilderjava)
   - [DancingLinks.java](#dancinglinksjava)
   - [HintCountCalculator.java](#hintcountcalculatorjava)
   - [TechniqueAnalyzer.java](#techniqueanalyzerjava)
   - [Region.java](#regionjava)
   - [AnalysisLogger.java](#analysisloggerjava)
6. [実験インフラ](#実験インフラ)
7. [可視化機能](#可視化機能)
8. [処理フロー](#処理フロー)
9. [アルゴリズム詳細](#アルゴリズム詳細)
10. [研究成果](#研究成果)
11. [使用方法](#使用方法)

---

## 概要

本システムは、マインスイーパのパズルを2つの異なる観点から難易度解析することを目的としています。

### 解析の2つの観点

| 観点               | クラス                | 説明                                                               |
| ------------------ | --------------------- | ------------------------------------------------------------------ |
| **計算量的難易度** | `HintCountCalculator` | 同時に参照が必要なヒント数（k値）に基づく難易度                    |
| **認知的難易度**   | `TechniqueAnalyzer`   | 人間の論理的思考パターン（テクニックレベルLv1〜Lv6）に基づく難易度 |

この2つの解析を組み合わせることで、パズルの難易度を多角的に評価できます。

### 研究の重要な発見

実験により、**パズル難易度とヒント総数の相関は低い**（相関係数約-0.19）ことが判明しました。
これは、難易度がヒントの「数」ではなく「配置パターン」に依存することを示唆しています。

---

## 主な機能

### 1. パズル自動生成 & 最小化
- ランダムな盤面生成に加え、ヒント数を限界まで減らす「最小化アルゴリズム」を搭載
- 正解が一意に定まることを保証しながら、高難易度なパズルを作成
- **重複排除機能**: 同じ地雷配置から複数のユニークなパズルを生成可能

### 2. 難易度解析 (Dual Analysis)
- **A. Hint Count Difficulty (k-Hint)**: 同時参照が必要なヒント数に基づく計算量的難易度
- **B. Technique Level Difficulty**: 人間の思考パターン（定石）に基づく認知的難易度（Lv1〜Lv6）

### 3. 推論チェーン追跡
- **親子関係追跡**: どのセルの確定が次のセル確定の引き金になったかを記録
- **推論深度(depth)**: 初期ヒントからの論理的距離を計測
- **triggerCells**: 直前ラウンドで確定したセルのうち、推論に寄与したものを記録

### 4. 可視化機能
- **ヒートマップ生成**: 難易度分布を視覚的に表示（Python/matplotlib）
- **推論グラフ**: セル確定の親子関係をDAGで可視化（Python/Graphviz）

### 5. 実験インフラ
- **バッチ処理**: 複数の地雷配置・複数試行での大規模実験
- **CSV出力**: 解析結果の詳細ログ

---

## システムアーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Main.java                                  │
│                      (統合実行・結果表示)                              │
└─────────────────────────────────────────────────────────────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│  PuzzleGenerator    │  │  HintCountCalculator │  │  TechniqueAnalyzer  │
│  (盤面生成)          │  │  (k-Hint解析)        │  │  (テクニック解析)    │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
              │                     │                     │
              ▼                     │                     │
┌─────────────────────┐            │                     ▼
│  PuzzleMinimizer    │            │           ┌─────────────────────┐
│  (ヒント最小化)      │            │           │      Region         │
│  ・重複排除機能      │            │           │  (領域演算)          │
└─────────────────────┘            │           └─────────────────────┘
              │                     │                     │
              └─────────────────────┼───────────────────────┘
                                    │                     │
                                    ▼                     ▼
                          ┌─────────────────────────────────────────┐
                          │           ConstraintBuilder              │
                          │        (制約行列の構築)                   │
                          └─────────────────────────────────────────┘
                                            │
                                            ▼
                          ┌─────────────────────────────────────────┐
                          │            DancingLinks                  │
                          │         (Algorithm X 実装)               │
                          └─────────────────────────────────────────┘
                                            │
                          ┌─────────────────┴─────────────────┐
                          ▼                                   ▼
          ┌─────────────────────────────┐   ┌─────────────────────────────┐
          │       AnalysisLogger        │   │     可視化スクリプト          │
          │       (CSV出力)             │   │  (Python: heatmap, graph)   │
          └─────────────────────────────┘   └─────────────────────────────┘
```

---

## 難易度レベル定義

TechniqueAnalyzerは人間の論理的思考パターンを6段階のレベルで分類します。

### 基本テクニック（Lv1〜Lv3）

| レベル  | 名称                 | 説明                                | 例                                      |
| ------- | -------------------- | ----------------------------------- | --------------------------------------- |
| **Lv1** | Base Hint            | 1つのヒントから直接確定             | 残り地雷数＝空きセル数、または地雷数＝0 |
| **Lv2** | Subset（包含）       | 2つのRegionの包含関係から差分を確定 | 「1-1」「1-2」パターン                  |
| **Lv3** | Intersection（共通） | 2つのRegionの共通部分から推論       | 重なり領域の地雷数が確定する場合        |

### 背理法テクニック（Lv4〜Lv6）

背理法は「仮定を立て、矛盾を導出して確定させる」手法です。
矛盾検出に使用する推論の複雑さによって3段階に分類されます。

| レベル  | 名称                | 矛盾検出に使用する推論 | 説明                            |
| ------- | ------------------- | ---------------------- | ------------------------------- |
| **Lv4** | Contradiction (Lv1) | 仮定 + Lv1推論         | 仮定後、Lv1のみで矛盾を検出     |
| **Lv5** | Contradiction (Lv2) | 仮定 + Lv1〜Lv2推論    | 仮定後、Lv2まで使って矛盾を検出 |
| **Lv6** | Contradiction (Lv3) | 仮定 + Lv1〜Lv3推論    | 仮定後、Lv3まで使って矛盾を検出 |

### 背理法の処理フロー

```
1. 未確定セルに対して「MINE」または「SAFE」を仮定
2. 仮定した盤面で推論を実行（Lv4→Lv5→Lv6の順）
3. 矛盾が発生したら、仮定の逆が正解として確定
4. 1セル確定したら即座にLv1に戻る（整合性維持のため）
```

---

## クラス詳細

### Main.java

統合実行クラス。パズル生成から難易度解析までの一連の処理を実行します。

#### 処理フロー
1. `PuzzleGenerator`でランダムな正解盤面を生成
2. `PuzzleMinimizer`でヒントを最小化して問題盤面を作成
3. `TechniqueAnalyzer`でテクニックレベル解析を実行
4. 結果を表示・CSV出力

#### 主要メソッド

| メソッド                                                         | 説明                                                               |
| ---------------------------------------------------------------- | ------------------------------------------------------------------ |
| `main(String[] args)`                                            | エントリーポイント。サイズ・地雷数を指定してパズル生成・解析を実行 |
| `printAnalysis(int[] puzzleBoard, int[] difficulties, int size)` | 解析結果を盤面形式で表示                                           |

---

### PuzzleGenerator.java

ランダムなマインスイーパ盤面を生成するクラス。

#### 処理手順
1. n×nのセル番号リストを作成
2. リストをシャッフルし、先頭b個を地雷セルとして設定（値: -1）
3. 地雷でない各セルについて、隣接8方向の地雷数をカウントして設定

#### 主要メソッド

| メソッド                         | 引数                     | 戻り値  | 説明                 |
| -------------------------------- | ------------------------ | ------- | -------------------- |
| `generatePuzzle(int n, int b)`   | n: 盤面サイズ, b: 地雷数 | `int[]` | 完全な正解盤面を生成 |
| `printBoard(int[] board, int n)` | board: 盤面, n: サイズ   | void    | 盤面をコンソール出力 |

#### 隣接セル探索
```java
int[] dx = { -1, -1, -1, 0, 0, 1, 1, 1 };
int[] dy = { -1, 0, 1, -1, 1, -1, 0, 1 };
```
8方向（上下左右＋斜め4方向）の隣接セルを探索します。

---

### PuzzleMinimizer.java

正解盤面からヒントを削減し、一意解を持つ最小限のヒント集合を求めるクラス。

#### フィールド

| フィールド      | 型             | 説明                              |
| --------------- | -------------- | --------------------------------- |
| `board`         | `int[]`        | 処理中の盤面                      |
| `originalBoard` | `int[]`        | 元の盤面（リセット用）            |
| `n`             | `int`          | 盤面サイズ                        |
| `anslist`       | `List<String>` | 正解のラベルリスト（DLX出力形式） |

#### 処理手順
1. 正解盤面から地雷位置のラベルリスト（`anslist`）を作成
2. 地雷でないセル（Safeセル）をシャッフル
3. 各セルを順に「隠す」（UNKNOWN状態にする）ことを試行
4. 隠すたびにDLXソルバで一意解判定を実行
5. 解が複数存在する場合は隠す操作を取り消し

#### 主要メソッド

| メソッド                                                    | 説明                                         |
| ----------------------------------------------------------- | -------------------------------------------- |
| `minimizeHints()`                                           | ヒント最小化を実行し、問題盤面を返す         |
| `generateMultipleUniquePuzzles(int count)`                  | 同じ地雷配置から複数のユニークなパズルを生成 |
| `generateMultipleUniquePuzzles(int count, int maxAttempts)` | 最大試行回数を指定してユニークなパズルを生成 |
| `getHintCount()`                                            | 現在の盤面のヒント数を取得                   |
| `reset()`                                                   | 盤面を初期状態にリセット                     |

#### 重複排除機能

```java
// 同じ地雷配置から5つのユニークなパズルを生成
PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
List<int[]> puzzles = pm.generateMultipleUniquePuzzles(5);

// 内部でHashSetを使用して重複チェック
Set<Set<Integer>> generatedHintSets = new HashSet<>();
```

---

### ConstraintBuilder.java

マインスイーパの盤面を制約充足問題（Exact Cover Problem）の行列形式に変換するクラス。

#### 定数定義

| 定数      | 値  | 説明                   |
| --------- | --- | ---------------------- |
| `MINE`    | -1  | 未確定セル（変数候補） |
| `IGNORE`  | -2  | 計算対象外             |
| `FLAGGED` | -3  | 地雷確定               |

#### フィールド

| フィールド  | 型              | 説明                                   |
| ----------- | --------------- | -------------------------------------- |
| `board`     | `int[]`         | 盤面配列                               |
| `size`      | `int`           | 盤面サイズ                             |
| `blanks`    | `List<Integer>` | 未確定セル（変数）のインデックスリスト |
| `hintCells` | `List<Integer>` | ヒントセルのインデックスリスト         |

#### 内部クラス: Data

| フィールド   | 型        | 説明                           |
| ------------ | --------- | ------------------------------ |
| `matrix`     | `int[][]` | 制約行列                       |
| `constraint` | `int[]`   | 各列の制約値（何個選択するか） |
| `blanks`     | `int`     | 変数の数                       |

#### 制約行列の構造

```
列構成:
[変数列: blankCount個] + [ヒント制約列: hintCount×2個]

行構成:
各変数について2行（Safe行とMine行）

例: 変数セル12について
- 行 "12#0": セル12がSafeの場合
- 行 "12#1": セル12がMineの場合
```

#### 主要メソッド

| メソッド                | 説明                                                 |
| ----------------------- | ---------------------------------------------------- |
| `buildConstraints()`    | 制約行列と制約配列を構築して`Data`オブジェクトを返す |
| `findCells()`           | 盤面から変数セルとヒントセルを分類                   |
| `getNeighbors(int idx)` | 指定セルの隣接8セルを取得                            |
| `getRowLabels()`        | 行ラベル配列を返す（デバッグ・結果表示用）           |
| `exportToCSV(...)`      | 制約行列をCSV出力                                    |

---

### DancingLinks.java

Donald Knuth氏の Algorithm X を Dancing Links データ構造で実装したクラス。
制約充足問題（Exact Cover Problem）を高速に解きます。

#### 内部クラス: Node

双方向循環リストのノード。上下左右4方向のリンクを持ちます。

| フィールド         | 型           | 説明                 |
| ------------------ | ------------ | -------------------- |
| `L`, `R`, `U`, `D` | `Node`       | 左右上下の隣接ノード |
| `Row`              | `int`        | 所属する行番号       |
| `Col`              | `ColumnNode` | 所属する列ヘッダ     |

| メソッド             | 説明                     |
| -------------------- | ------------------------ |
| `hookDown(Node n1)`  | 自分の下にノードを挿入   |
| `hookRight(Node n1)` | 自分の右にノードを挿入   |
| `unlinkLR()`         | 左右リンクから自分を除去 |
| `relinkLR()`         | 左右リンクに自分を復元   |
| `unlinkUD()`         | 上下リンクから自分を除去 |
| `relinkUD()`         | 上下リンクに自分を復元   |

#### 内部クラス: ColumnNode

列ヘッダノード。`Node`を継承します。

| フィールド   | 型       | 説明                             |
| ------------ | -------- | -------------------------------- |
| `size`       | `int`    | この列に含まれるノード数         |
| `covered`    | `int`    | カバーされた回数                 |
| `constraint` | `int`    | この列の制約値（選択すべき行数） |
| `name`       | `String` | 列名                             |

| メソッド    | 説明                             |
| ----------- | -------------------------------- |
| `cover()`   | この列と関連する行を一時的に削除 |
| `uncover()` | cover()を取り消して復元          |

#### 主要メソッド

| メソッド                                       | 説明                                               |
| ---------------------------------------------- | -------------------------------------------------- |
| `makeDLXBoard(int[][] grid, int[] constraint)` | 制約行列からDLXデータ構造を構築                    |
| `runSolver()`                                  | 解の探索を開始                                     |
| `knuthsAlgorithmX(int k)`                      | Algorithm Xの再帰的探索                            |
| `selectColumnNode()`                           | 最小サイズの制約1列を選択（MRVヒューリスティック） |
| `getAnswer()`                                  | 解の行番号配列を返す                               |
| `SolutionsCount(int blanks)`                   | 発見した解の数を返す                               |

---

### HintCountCalculator.java

k-Hint難易度解析を行うクラス。「いくつのヒントを同時に参照すれば解けるか」を計算します。

#### フィールド

| フィールド         | 型                           | 説明                           |
| ------------------ | ---------------------------- | ------------------------------ |
| `initialPuzzle`    | `int[]`                      | 初期問題盤面                   |
| `completeBoard`    | `int[]`                      | 正解盤面                       |
| `size`             | `int`                        | 盤面サイズ                     |
| `difficultyMap`    | `int[]`                      | 各セルの難易度（k値）          |
| `requiredHintsMap` | `Map<Integer, Set<Integer>>` | 各セルの確定に必要なヒント集合 |

#### 主要メソッド

| メソッド                                  | 説明                                 |
| ----------------------------------------- | ------------------------------------ |
| `calculate()`                             | 難易度解析を実行                     |
| `executeRound(int k, int[] currentBoard)` | k個のヒント組み合わせで解析          |
| `tryDeduce(...)`                          | 局所解析（プロービング）             |
| `isScenarioPossible(...)`                 | 特定行を禁止した場合に解があるか判定 |
| `getRequiredHints(int cellIndex)`         | 指定セルの必要ヒント集合を取得       |
| `getAllRequiredHints()`                   | 全セルの必要ヒントマップを取得       |

#### 解析アルゴリズム

```
for k = 1 to maxK:
    for each k個のヒント組み合わせ subset:
        1. subsetの周囲にある未確定セルを取得
        2. 一時盤面を作成（subset以外のヒントをIGNORE）
        3. 制約行列を構築
        4. 各未確定セルについてプロービング:
           - Safeのみ可能 → Safe確定
           - Mineのみ可能 → Mine確定
        5. 確定したセルの難易度をkとして記録
    
    確定したセルを盤面に反映
```

---

### TechniqueAnalyzer.java

テクニックレベル難易度解析を行うクラス。人間の論理的思考パターンをシミュレートします。

#### フィールド

| フィールド                  | 型                           | 説明                                          |
| --------------------------- | ---------------------------- | --------------------------------------------- |
| `board`                     | `int[]`                      | 現在の盤面状態                                |
| `completeBoard`             | `int[]`                      | 正解盤面                                      |
| `difficultyMap`             | `int[]`                      | 各セルの難易度レベル                          |
| `size`                      | `int`                        | 盤面サイズ                                    |
| `regionPool`                | `Map<Set<Integer>, Region>`  | Region管理プール                              |
| `isDerivedRegionsGenerated` | `boolean`                    | Lv2/Lv3 Region生成済みフラグ                  |
| `cellToRequiredHints`       | `Map<Integer, Set<Integer>>` | HintCountCalculatorから取得した必要ヒント集合 |
| `logger`                    | `AnalysisLogger`             | ログ記録用                                    |
| `currentRound`              | `int`                        | 現在のラウンド番号                            |

#### 主要メソッド

##### 解析制御

| メソッド                                                                               | 説明                                     |
| -------------------------------------------------------------------------------------- | ---------------------------------------- |
| `analyze()`                                                                            | 解析のメインループ                       |
| `updateAndGenerateRegions()`                                                           | Regionの生成・メンテナンス               |
| `updateAndGenerateRegions(int[] targetBoard, Map<Set<Integer>, Region> targetPool)`    | 指定盤面・プールでRegion更新（背理法用） |
| `solveFromPool()`                                                                      | Regionプールから確定セルを探索           |
| `solveFromPool(int[] targetBoard, Map<Set<Integer>, Region> targetPool, int maxLevel)` | 指定レベルまでの推論（背理法用）         |
| `applyResult(Map<Integer, Integer> deduced)`                                           | 推論結果を盤面に適用                     |

##### Region操作

| メソッド                                               | 説明                                         |
| ------------------------------------------------------ | -------------------------------------------- |
| `createRegionFromHint(int hintIdx)`                    | ヒントセルからLv1 Regionを生成               |
| `createRegionFromHint(int hintIdx, int[] targetBoard)` | 指定盤面でRegion生成                         |
| `updateRegionState(Region original)`                   | 既存Regionを現在の盤面状態に更新             |
| `addToPool(Region newRegion)`                          | Regionをプールに追加（重複時は低レベル優先） |

##### 背理法（Lv4〜Lv6）

| メソッド                                                    | 説明                                 |
| ----------------------------------------------------------- | ------------------------------------ |
| `solveLv4()`                                                | Lv4背理法（仮定+Lv1推論で矛盾検出）  |
| `solveLv5()`                                                | Lv5背理法（仮定+Lv2推論で矛盾検出）  |
| `solveLv6()`                                                | Lv6背理法（仮定+Lv3推論で矛盾検出）  |
| `testContradictionWithLevel(int[] tempBoard, int maxLevel)` | 指定レベルまでの推論で矛盾検出       |
| `createMaskedBoard(Set<Integer> requiredHints)`             | 必要ヒントのみ残したマスク盤面を作成 |

#### 解析フロー（Region事前全列挙モデル）

```
初期化: 
  - 初期ヒントの難易度を0に設定
  - 初期ヒントをRound 0としてログ記録

while (変化あり):
    Round開始
    
    1. Regionの生成・メンテナンス:
       - Lv1 Region: 毎ラウンド全再生成
       - Lv2/Lv3 Region: 初回のみ生成、以降はメンテナンス
    
    2. Lv1→Lv2→Lv3の順でソルビング:
       - 同一レベルの全Regionを一括評価
       - 確定セルを収集（triggerCells、depthを記録）
       - Lv1で確定があればLv2以上は処理しない
    
    3. 確定なしの場合、背理法を試行:
       Lv4（仮定+Lv1推論）
         ↓ 確定なし
       Lv5（仮定+Lv2推論）
         ↓ 確定なし
       Lv6（仮定+Lv3推論）
    
    4. 確定セルを盤面に反映（1セルずつ）
    
    5. 確定があれば次ラウンドへ（Lv1から再開）
```

#### 背理法の実装詳細

```java
private Map<Integer, Integer> solveLv4() {
    // 未確定セルを順に検証
    for (int cellIdx : unknownCells) {
        // 正解の逆を仮定
        int wrongValue = isMine ? SAFE : FLAGGED;
        
        // 一時盤面を作成
        int[] tempBoard = Arrays.copyOf(board, board.length);
        tempBoard[cellIdx] = wrongValue;
        
        // Lv1推論で矛盾検出
        if (testContradictionWithLevel(tempBoard, LV_1)) {
            // 矛盾あり → 正解で確定
            return Map.of(cellIdx, correctValue);
        }
    }
    return Collections.emptyMap();
}
```

---

### Region.java

盤面上の「領域」を表す不変オブジェクト。セル集合と地雷数、生成レベルを保持します。

#### フィールド

| フィールド    | 型             | 説明                                         |
| ------------- | -------------- | -------------------------------------------- |
| `cells`       | `Set<Integer>` | 領域に含まれるセルのインデックス集合（不変） |
| `mines`       | `int`          | 領域内の地雷数                               |
| `originLevel` | `int`          | 生成されたテクニックレベル                   |
| `sourceHints` | `Set<Integer>` | 生成元となったヒントのインデックス集合       |
| `id`          | `int`          | Region通し番号（表示用）                     |

#### 主要メソッド

##### 集合演算

| メソッド                                | 説明                                |
| --------------------------------------- | ----------------------------------- |
| `isSubsetOf(Region other)`              | 自分がotherの部分集合かどうか       |
| `subtract(Region other, int newLevel)`  | 差集合Regionを生成（Lv2用）         |
| `intersect(Region other, int newLevel)` | 共通部分から新Regionを生成（Lv3用） |

##### アクセサ

| メソッド                 | 説明                       |
| ------------------------ | -------------------------- |
| `getCells()`             | セル集合を取得             |
| `getMines()`             | 地雷数を取得               |
| `getOriginLevel()`       | 生成レベルを取得           |
| `size()`                 | セル数を取得               |
| `getSourceHintsString()` | 生成元ヒントを文字列で取得 |

---

### AnalysisLogger.java

解析過程をログに記録し、CSVファイルに出力するクラス。

#### 内部クラス: LogEntry

| フィールド        | 型       | 説明                             |
| ----------------- | -------- | -------------------------------- |
| `round`           | `int`    | ラウンド番号（初期ヒントは0）    |
| `step`            | `int`    | ラウンド内ステップ番号           |
| `cellIdx`         | `int`    | 確定したセルのインデックス       |
| `result`          | `String` | 結果（"SAFE", "MINE", "HINT"）   |
| `difficultyLevel` | `int`    | 難易度レベル                     |
| `regionId`        | `int`    | 使用したRegionのID               |
| `regionContent`   | `String` | Regionの内容                     |
| `sourceHints`     | `String` | 生成元ヒント                     |
| `triggerCells`    | `String` | 推論の引き金となったセル         |
| `depth`           | `int`    | 推論深度（初期ヒントからの距離） |

#### 主要メソッド

| メソッド                                     | 説明                                       |
| -------------------------------------------- | ------------------------------------------ |
| `startNewRound()`                            | ラウンド開始時にステップカウンタをリセット |
| `logInitialHint(int cellIdx, int hintValue)` | 初期ヒントをRound 0として記録              |
| `logStep(...)`                               | 確定ステップをログに記録                   |
| `exportToCSV(String filename)`               | 蓄積されたログをCSV出力                    |

#### CSV出力形式

```csv
Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints,TriggerCells,GenerationDepth
0,1,5,HINT,0,-1,"-","","",0
0,2,12,HINT,0,-1,"-","","",0
1,1,15,SAFE,1,3,"{15, 16}=0","5","",1
1,2,23,MINE,2,7,"{23}=1","5,14","15",2
2,1,45,SAFE,4,-1,"Lv4-Contradiction","Assumed:MINE","23",3
...
```

---

## 実験インフラ

大規模実験のためのインフラストラクチャ。

### ExperimentRunner.java

バッチ処理を実行するクラス。

```java
// 実験設定
int[] mineCounts = {20, 25, 30};  // 地雷数のバリエーション
int layoutsPerMineCount = 100;    // 各地雷数での配置パターン数
int trialsPerLayout = 20;         // 各配置での試行回数

ExperimentRunner runner = new ExperimentRunner(size, mineCounts, layoutsPerMineCount, trialsPerLayout);
runner.runExperiments("output_directory");
```

### ExperimentResult.java

実験結果を格納するデータクラス。

| フィールド      | 説明           |
| --------------- | -------------- |
| `mineCount`     | 地雷数         |
| `layoutId`      | 配置パターンID |
| `trialId`       | 試行ID         |
| `hintCount`     | ヒント数       |
| `difficultyMap` | 難易度マップ   |
| `maxDifficulty` | 最大難易度     |
| `avgDifficulty` | 平均難易度     |

### 出力ファイル

```
output_directory/
├── summary.csv              # 全実験の集計結果
├── mines_20/
│   ├── layout_001/
│   │   ├── trial_01_analysis.csv
│   │   ├── trial_01_heatmap.png
│   │   ├── trial_01_graph.png
│   │   └── ...
│   └── ...
└── ...
```

---

## 可視化機能

### ヒートマップ生成（Python/matplotlib）

```bash
python heatmap_generator.py analysis_data.csv output.png
```

#### 出力内容
- 問題盤面（ヒントセルと未確定セル）
- k-Hint難易度ヒートマップ（緑グラデーション）
- テクニックレベルヒートマップ（青グラデーション）

#### 日本語ラベル
- タイトル: 「マインスイーパ難易度解析」
- 問題盤面: 「問題盤面」
- k-Hint: 「必要ヒント数 (k-Hint)」
- テクニックレベル: 「テクニックレベル」

### 推論グラフ生成（Python/Graphviz）

```bash
python graph_generator.py analysis_log.csv output.png
```

#### 出力内容
- ノード: セル（色分け: 灰=初期ヒント、緑=SAFE、赤=MINE）
- エッジ: 推論の親子関係（ラベル: 難易度レベル）
- 水平区切り線: 推論深度（depth）ごとの階層表示

---

## 処理フロー

### 全体フロー

```
1. パズル生成フェーズ
   PuzzleGenerator.generatePuzzle(size, bombs)
        ↓
   完全な正解盤面（全セルが数字 or 地雷）
        ↓
   PuzzleMinimizer.minimizeHints()
        ↓
   問題盤面（最小限のヒント）

2. 解析フェーズ
   TechniqueAnalyzer.analyze()
        ↓
   各セルの難易度レベル（Lv1〜Lv6）
        ↓
   AnalysisLogger.exportToCSV()
        ↓
   解析結果CSV

3. 可視化フェーズ
   Python scripts
        ↓
   ヒートマップ / 推論グラフ
```

### TechniqueAnalyzerの詳細フロー

```
┌─────────────────────────────────────────────────────────────────┐
│                      analyze() 開始                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  初期ヒントを難易度0、Round 0としてログ記録                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
           ┌──────────────────────────────────────┐
           │          while (changed)             │
           │              ループ                   │
           └──────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│          updateAndGenerateRegions()                              │
│  ・Lv1 Region: 全再生成                                          │
│  ・Lv2/Lv3 Region: メンテナンス（確定セル除去）                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              solveFromPool()                                     │
│  ・同一レベルのRegionを一括評価                                   │
│  ・triggerCells、depthを計算                                     │
│  ・mines==size → 全セルFLAGGED                                   │
│  ・mines==0 → 全セルSAFE                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
            確定あり                 確定なし
                │                       │
                ▼                       ▼
┌───────────────────────┐   ┌───────────────────────────────────┐
│    applyResult()      │   │     solveLv4() - Lv6()            │
│    changed = true     │   │  ・各セルに逆値を仮定               │
│    次ラウンドへ         │   │  ・レベル別に矛盾検出               │
└───────────────────────┘   │  ・1セル確定で即return              │
                            └───────────────────────────────────┘
                                        │
                              ┌─────────┴─────────┐
                              ▼                   ▼
                        確定あり              確定なし
                            │                   │
                            ▼                   ▼
              ┌───────────────────┐    ┌───────────────────┐
              │   applyResult()   │    │   changed = false │
              │   changed = true  │    │   ループ終了       │
              └───────────────────┘    └───────────────────┘
```

---

## アルゴリズム詳細

### Dancing Links (DLX) の動作原理

#### 1. データ構造

```
header ← → Col0 ← → Col1 ← → Col2 ← → ... ← → ColN ← → (header)
           ↑↓       ↑↓       ↑↓              ↑↓
          Node     Node     Node            Node
           ↑↓       ↑↓       ↑↓              ↑↓
          Node     Node     Node            Node
           ↑↓       ↑↓       ↑↓              ↑↓
          ...      ...      ...             ...
```

各Nodeは上下左右4方向に循環リンクを持ち、cover/uncover操作を O(1) で実行できます。

#### 2. cover操作

```java
void cover() {
    // 1. 列ヘッダを左右リンクから除去
    unlinkLR();
    
    // 2. この列の各ノードについて
    for (Node i = this.D; i != this; i = i.D) {
        // その行の他のノードを上下リンクから除去
        for (Node j = i.R; j != i; j = j.R) {
            j.unlinkUD();
            j.Col.size--;
        }
    }
}
```

### パフォーマンス最適化

#### Lv4の高速化

初期実装では30秒以上かかっていた処理を、**10ミリ秒未満**に改善。

**問題点**: `createMaskedBoard`が全未確定セルをDLX探索空間に残していた

**解決策**: 必要ヒントの周囲セルのみを探索対象とし、それ以外をIGNORE

```java
// 改善前: 全未確定セルが変数として残る
private int[] createMaskedBoard(Set<Integer> requiredHints) {
    int[] masked = Arrays.copyOf(board, board.length);
    // ヒントのマスクのみ
    for (int i = 0; i < masked.length; i++) {
        if (masked[i] >= 0 && !requiredHints.contains(i)) {
            masked[i] = IGNORE;
        }
    }
    return masked;
}

// 改善後: 関連セルのみを変数として残す
private int[] createMaskedBoard(Set<Integer> requiredHints) {
    int[] masked = new int[board.length];
    Arrays.fill(masked, IGNORE);  // 全てIGNOREで初期化
    
    // 必要ヒントをコピー
    for (int hintIdx : requiredHints) {
        masked[hintIdx] = board[hintIdx];
    }
    
    // 必要ヒントの周囲の未確定セルのみをコピー
    for (int hintIdx : requiredHints) {
        for (int nb : getNeighbors(hintIdx)) {
            if (board[nb] == MINE) {
                masked[nb] = MINE;
            }
        }
    }
    return masked;
}
```

---

## 研究成果

### 主要な発見

1. **ヒント数と難易度の相関は低い**
   - 相関係数: 約 -0.19
   - 難易度は「ヒントの数」ではなく「ヒントの配置パターン」に依存

2. **背理法の階層化の有効性**
   - Lv4-6の段階的な複雑さが、人間の思考プロセスをより正確にモデル化

3. **推論深度の重要性**
   - 初期ヒントからの論理的距離が認知負荷の指標として有効

---

## 定数定義

全クラス共通で使用されるセル状態の定数:

| 定数名    | 値   | 説明                                 |
| --------- | ---- | ------------------------------------ |
| `MINE`    | -1   | 未確定セル（変数）/ 正解盤面での地雷 |
| `IGNORE`  | -2   | 計算対象外（マスク済み）             |
| `FLAGGED` | -3   | 地雷確定                             |
| `SAFE`    | -4   | 安全確定（TechniqueAnalyzerのみ）    |
| `0以上`   | 0〜8 | ヒント数字（隣接地雷数）             |

---

## 使用方法

### 基本的な使用例

```java
public static void main(String[] args) {
    int size = 10;    // 10×10盤面
    int bombs = 30;   // 地雷30個

    // 1. 正解盤面を生成
    int[] board = PuzzleGenerator.generatePuzzle(size, bombs);
    
    // 2. 問題盤面を生成（ヒント最小化）
    PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
    int[] puzzle = pm.minimizeHints();
    
    // 3. テクニック解析を実行
    TechniqueAnalyzer analyzer = new TechniqueAnalyzer(puzzle, board, size);
    analyzer.analyze();
    
    // 4. 結果取得
    int[] difficulties = analyzer.getDifficultyMap();
    
    // 5. CSV出力
    analyzer.exportLogToCSV("analysis_log.csv");
}
```

### 複数パズル生成

```java
// 同じ地雷配置から5つのユニークなパズルを生成
PuzzleMinimizer pm = new PuzzleMinimizer(board, size);
List<int[]> puzzles = pm.generateMultipleUniquePuzzles(5);

for (int i = 0; i < puzzles.size(); i++) {
    System.out.println("Puzzle " + (i+1) + ": " + pm.countHints(puzzles.get(i)) + " hints");
}
```

### 可視化

```bash
# ヒートマップ生成
python heatmap_generator.py board_data.csv heatmap.png

# 推論グラフ生成
python graph_generator.py analysis_log.csv inference_graph.png
```

---

## プロジェクト構成

```
src/
├── Main.java                # 統合実行クラス
│
├── PuzzleGenerator.java     # [生成] ランダム盤面生成
├── PuzzleMinimizer.java     # [生成] 一意解を維持したヒント削減・重複排除
│
├── DancingLinks.java        # [Core] Algorithm X 実装
├── ConstraintBuilder.java   # [Core] マインスイーパ→Exact Cover行列変換
│
├── HintCountCalculator.java # [解析] k-Hint法の実装
├── TechniqueAnalyzer.java   # [解析] テクニックレベル法（Lv1-6）の実装
├── Region.java              # [解析] 領域演算ロジック
├── AnalysisLogger.java      # [Utility] CSVログ出力（親子関係・深度追跡）
│
├── ExperimentRunner.java    # [実験] バッチ処理実行
├── ExperimentResult.java    # [実験] 結果データ格納
│
scripts/
├── heatmap_generator.py     # [可視化] ヒートマップ生成
├── graph_generator.py       # [可視化] 推論グラフ生成
└── BoardExporter.java       # [Utility] 盤面データCSV出力
```

---

## 参考文献

- Knuth, D. E. (2000). "Dancing Links". Millennial Perspectives in Computer Science.
- マインスイーパの論理的解法パターンに関する研究