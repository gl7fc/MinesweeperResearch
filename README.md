# Minesweeper Difficulty Analyzer

マインスイーパのパズル生成および各セルの「論理的な難易度」を定量的に解析するJava製ツールです。
Knuth's Algorithm X (Dancing Links) を用いた制約充足ソルバをバックエンドに使用し、パズルが論理的に解けるかどうか、また解くためにどのような思考やテクニックが必要かを判定します。

---

## 目次

1. [概要](#概要)
2. [主な機能](#主な機能)
3. [システムアーキテクチャ](#システムアーキテクチャ)
4. [クラス詳細](#クラス詳細)
   - [Main.java](#mainjava)
   - [PuzzleGenerator.java](#puzzlegeneratorjava)
   - [PuzzleMinimizer.java](#puzzleminimizerjava)
   - [ConstraintBuilder.java](#constraintbuilderjava)
   - [DancingLinks.java](#dancinglinksjava)
   - [HintCountCalculator.java](#hintcountcalculatorjava)
   - [TechniqueAnalyzer.java](#techniqueanalyzerjava)
   - [Region.java](#regionjava)
   - [AnalysisLogger.java](#analysisloggerjava)
5. [処理フロー](#処理フロー)
6. [アルゴリズム詳細](#アルゴリズム詳細)
7. [定数定義](#定数定義)
8. [使用方法](#使用方法)

---

## 概要

本システムは、マインスイーパのパズルを2つの異なる観点から難易度解析することを目的としています。

### 解析の2つの観点

| 観点               | クラス                | 説明                                                       |
| ------------------ | --------------------- | ---------------------------------------------------------- |
| **計算量的難易度** | `HintCountCalculator` | 同時に参照が必要なヒント数（k値）に基づく難易度            |
| **認知的難易度**   | `TechniqueAnalyzer`   | 人間の論理的思考パターン（テクニックレベル）に基づく難易度 |

この2つの解析を組み合わせることで、パズルの難易度を多角的に評価できます。

---

## 主な機能

### 1. パズル自動生成 & 最小化
- ランダムな盤面生成に加え、ヒント数を限界まで減らす「最小化アルゴリズム」を搭載
- 正解が一意に定まることを保証しながら、高難易度なパズルを作成

### 2. 難易度解析 (Dual Analysis)
- **A. Hint Count Difficulty (k-Hint)**: 同時参照が必要なヒント数に基づく計算量的難易度
- **B. Technique Level Difficulty**: 人間の思考パターン（定石）に基づく認知的難易度（Lv1〜Lv4）

### 3. 詳細ログ出力
- 解析プロセスをCSV出力し、どのヒントからどのセルが確定したかを追跡可能

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
┌─────────────────────┐            │                     │
│  PuzzleMinimizer    │            │                     ▼
│  (ヒント最小化)      │            │           ┌─────────────────────┐
└─────────────────────┘            │           │      Region         │
              │                     │           │  (領域演算)          │
              └─────────────────────┼───────────┴─────────────────────┘
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
                                            ▼
                          ┌─────────────────────────────────────────┐
                          │           AnalysisLogger                 │
                          │          (CSV出力)                       │
                          └─────────────────────────────────────────┘
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

| フィールド | 型             | 説明                              |
| ---------- | -------------- | --------------------------------- |
| `board`    | `int[]`        | 処理中の盤面                      |
| `n`        | `int`          | 盤面サイズ                        |
| `anslist`  | `List<String>` | 正解のラベルリスト（DLX出力形式） |

#### 処理手順
1. 正解盤面から地雷位置のラベルリスト（`anslist`）を作成
2. 地雷でないセル（Safeセル）をシャッフル
3. 各セルを順に「隠す」（UNKNOWN状態にする）ことを試行
4. 隠すたびにDLXソルバで一意解判定を実行
5. 解が複数存在する場合は隠す操作を取り消し

#### 主要メソッド

| メソッド                                    | 説明                                 |
| ------------------------------------------- | ------------------------------------ |
| `minimizeHints()`                           | ヒント最小化を実行し、問題盤面を返す |
| `makeAnslist()`                             | 正解ラベルリストを作成               |
| `isUniqueSolvable(int[] board, int n)`      | DLXで一意解判定を実行                |
| `listEqual(List<String> a, List<String> b)` | ラベルリストの比較                   |

#### 一意解判定の詳細
```java
private boolean isUniqueSolvable(int[] board, int n) {
    // 1. 制約行列を構築
    ConstraintBuilder builder = new ConstraintBuilder(board, n);
    ConstraintBuilder.Data data = builder.buildConstraints();
    
    // 2. DLXで解を探索
    DancingLinks dlx = new DancingLinks(data.matrix, data.constraint);
    dlx.runSolver();
    
    // 3. 解のラベルリストを作成・ソート
    List<String> finalAnslist = ...;
    
    // 4. 正解リストと比較 & 解が1つだけか確認
    return listEqual(anslist, finalAnslist) && dlx.SolutionsCount(blanks) == 1;
}
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
| `getColumnLabels()`     | 列ラベル配列を返す                                   |
| `exportToCSV(...)`      | 制約行列をCSV出力                                    |

#### 制約行列構築の詳細

```java
// 変数制約: 各変数は0か1のどちらか一方を選択
for (int i = 0; i < blankCount; i++) {
    matrix[i * 2][i] = 1;      // Safe行
    matrix[i * 2 + 1][i] = 1;  // Mine行
    constraint[i] = 1;          // ちょうど1つ選択
}

// ヒント制約: 周囲の地雷数と安全数
for (int i = 0; i < hintCount; i++) {
    int hintIdx = hintCells.get(i);
    int effectiveHintValue = board[hintIdx];
    
    // FLAGGED（確定地雷）があればヒント値を減算
    for (int nb : neighbors) {
        if (board[nb] == FLAGGED) {
            effectiveHintValue--;
        }
    }
    
    // 安全列: variableCount - effectiveHintValue
    // 地雷列: effectiveHintValue
}
```

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

#### 主要フィールド

| フィールド       | 型           | 説明                   |
| ---------------- | ------------ | ---------------------- |
| `header`         | `ColumnNode` | 列ヘッダのルートノード |
| `answer`         | `List<Node>` | 探索中の部分解         |
| `solutionsCount` | `int`        | 発見した解の数         |
| `finalAnswer`    | `List<Node>` | 最終的な解             |

#### 主要メソッド

| メソッド                                       | 説明                                               |
| ---------------------------------------------- | -------------------------------------------------- |
| `makeDLXBoard(int[][] grid, int[] constraint)` | 制約行列からDLXデータ構造を構築                    |
| `runSolver()`                                  | 解の探索を開始                                     |
| `knuthsAlgorithmX(int k)`                      | Algorithm Xの再帰的探索                            |
| `selectColumnNode()`                           | 最小サイズの制約1列を選択（MRVヒューリスティック） |
| `getAnswer()`                                  | 解の行番号配列を返す                               |
| `SolutionsCount(int blanks)`                   | 発見した解の数を返す                               |

#### Algorithm Xの処理フロー

```
1. 制約0の列を事前にcover（既に満たされている）
2. 再帰探索開始:
   a. 全列がcoverされたら解発見
   b. 制約1の列から最小サイズの列cを選択
   c. 列cをcover
   d. 列cの各行rについて:
      - 行rを部分解に追加
      - 行rに含まれる各列の被覆カウントを増加
      - 制約に達した列をcover
      - 再帰呼び出し
      - バックトラック（復元）
   e. 列cをuncover
```

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

| メソッド                                                                           | 説明                                 |
| ---------------------------------------------------------------------------------- | ------------------------------------ |
| `calculate()`                                                                      | 難易度解析を実行                     |
| `executeRound(int k, int[] currentBoard)`                                          | k個のヒント組み合わせで解析          |
| `tryDeduce(List<Integer> subset, Set<Integer> targetUnknowns, int[] currentBoard)` | 局所解析（プロービング）             |
| `isScenarioPossible(int[][] matrix, int[] constraint, int forbiddenRow)`           | 特定行を禁止した場合に解があるか判定 |
| `getRequiredHints(int cellIndex)`                                                  | 指定セルの必要ヒント集合を取得       |
| `getAllRequiredHints()`                                                            | 全セルの必要ヒントマップを取得       |

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

#### プロービングの詳細

```java
// セルがSafeである可能性を判定
// → Mine行を禁止してDLX実行、解があればSafe可能
boolean canBeSafe = isScenarioPossible(matrix, constraint, rMine);

// セルがMineである可能性を判定
// → Safe行を禁止してDLX実行、解があればMine可能
boolean canBeMine = isScenarioPossible(matrix, constraint, rSafe);

// 判定
if (canBeSafe && !canBeMine) → Safe確定
if (!canBeSafe && canBeMine) → Mine確定
```

---

### TechniqueAnalyzer.java

テクニックレベル難易度解析を行うクラス。人間の論理的思考パターンをシミュレートします。

#### 難易度レベル定義

| レベル | 名称                    | 説明                                                           |
| ------ | ----------------------- | -------------------------------------------------------------- |
| `LV_1` | Base Hint               | 1つのヒントから直接確定（残り地雷数＝空きセル数 or 地雷数＝0） |
| `LV_2` | Subset（包含）          | 2つのRegionの包含関係から差分を確定                            |
| `LV_3` | Intersection（共通）    | 2つのRegionの共通部分から推論                                  |
| `LV_4` | Contradiction（背理法） | 仮定を立てて矛盾を検出                                         |

#### フィールド

| フィールド                  | 型                          | 説明                         |
| --------------------------- | --------------------------- | ---------------------------- |
| `board`                     | `int[]`                     | 現在の盤面状態               |
| `completeBoard`             | `int[]`                     | 正解盤面                     |
| `difficultyMap`             | `int[]`                     | 各セルの難易度レベル         |
| `size`                      | `int`                       | 盤面サイズ                   |
| `regionPool`                | `Map<Set<Integer>, Region>` | Region管理プール             |
| `isDerivedRegionsGenerated` | `boolean`                   | Lv2/Lv3 Region生成済みフラグ |
| `logger`                    | `AnalysisLogger`            | ログ記録用                   |
| `currentRound`              | `int`                       | 現在のラウンド番号           |

#### 主要メソッド

##### 解析制御

| メソッド                                     | 説明                           |
| -------------------------------------------- | ------------------------------ |
| `analyze()`                                  | 解析のメインループ             |
| `updateAndGenerateRegions()`                 | Regionの生成・メンテナンス     |
| `solveFromPool()`                            | Regionプールから確定セルを探索 |
| `applyResult(Map<Integer, Integer> deduced)` | 推論結果を盤面に適用           |

##### Region操作

| メソッド                             | 説明                                         |
| ------------------------------------ | -------------------------------------------- |
| `createRegionFromHint(int hintIdx)`  | ヒントセルからLv1 Regionを生成               |
| `updateRegionState(Region original)` | 既存Regionを現在の盤面状態に更新             |
| `addToPool(Region newRegion)`        | Regionをプールに追加（重複時は低レベル優先） |
| `reassignIds()`                      | Region IDを再割り当て                        |

##### Lv4解析（背理法）

| メソッド                              | 説明                                  |
| ------------------------------------- | ------------------------------------- |
| `solveLv4()`                          | Lv4背理法による推論（軽量版）         |
| `testContradiction(int[] tempBoard)`  | 仮置き後のLv1推論で矛盾検出           |
| `solveWithContradiction()`            | DLXベースの背理法推論                 |
| `testAssumptionWithDLX(...)`          | DLXによる仮定検証                     |
| `extractRequiredHints(int cellIndex)` | HintCountCalculatorから必要ヒント抽出 |
| `selectCandidateCells()`              | 背理法の候補セル選択                  |

#### 解析フロー（Region事前全列挙モデル）

```
初期化: 初期ヒントの難易度を0に設定

while (変化あり):
    Round開始
    
    1. Regionの生成・メンテナンス:
       - Lv1 Region: 毎ラウンド全再生成
       - Lv2/Lv3 Region: 初回のみ生成、以降はメンテナンス
    
    2. Lv1→Lv2→Lv3の順でソルビング:
       - Regionが mines == size なら全セルFLAGGED
       - Regionが mines == 0 なら全セルSAFE
       - Lv1で確定があればLv2以上は処理しない
    
    3. 確定なしの場合、Lv4（背理法）を試行:
       - 各未確定セルに逆の値を仮置き
       - Lv1推論で矛盾（地雷過多/不足）を検出
       - 矛盾あれば正解で確定
    
    4. 確定セルを盤面に反映
    
    5. 確定があれば次ラウンドへ
```

#### Lv2/Lv3 Region生成ロジック

```java
// 全Lv1 Regionペアについて:
for (Region rA, rB in baseRegions):
    
    // Lv2: 包含判定
    if (rA ⊆ rB):
        diff = rB - rA  // 差集合Region
        addToPool(diff)
    
    // Lv3: 共通判定
    else:
        intersections = rA.intersect(rB)
        // 共通部分・差分から新Regionを生成
        for (Region r in intersections):
            addToPool(r)
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

#### intersect()の詳細ロジック

```
入力: Region A, Region B

1. 共通部分 C = A ∩ B を計算
2. 差分 onlyA = A - C, onlyB = B - C を計算

3. Cに入りうる地雷数の範囲を計算:
   maxC = min(|C|, A.mines, B.mines)
   minC = max(0, A.mines - |onlyA|, B.mines - |onlyB|)

4. minC == maxC の場合（Cの地雷数が確定）:
   - Region C (mines=minC) を生成
   - Region onlyA (mines=A.mines-minC) を生成
   - Region onlyB (mines=B.mines-minC) を生成

5. そうでない場合:
   - onlyA, onlyBの地雷数が確定可能なら生成
```

---

### AnalysisLogger.java

解析過程をログに記録し、CSVファイルに出力するクラス。

#### 内部クラス: LogEntry

| フィールド        | 型       | 説明                       |
| ----------------- | -------- | -------------------------- |
| `round`           | `int`    | ラウンド番号               |
| `step`            | `int`    | ラウンド内ステップ番号     |
| `cellIdx`         | `int`    | 確定したセルのインデックス |
| `result`          | `String` | 結果（"SAFE" or "MINE"）   |
| `difficultyLevel` | `int`    | 難易度レベル               |
| `regionId`        | `int`    | 使用したRegionのID         |
| `regionContent`   | `String` | Regionの内容               |
| `sourceHints`     | `String` | 生成元ヒント               |

#### フィールド

| フィールド    | 型               | 説明                       |
| ------------- | ---------------- | -------------------------- |
| `logs`        | `List<LogEntry>` | ログエントリのリスト       |
| `stepCounter` | `int`            | ラウンド内ステップカウンタ |

#### 主要メソッド

| メソッド                       | 説明                                       |
| ------------------------------ | ------------------------------------------ |
| `startNewRound()`              | ラウンド開始時にステップカウンタをリセット |
| `logStep(...)`                 | 確定ステップをログに記録                   |
| `exportToCSV(String filename)` | 蓄積されたログをCSV出力                    |

#### CSV出力形式

```csv
Round,Step,CellIndex,Result,DifficultyLevel,RegionID,RegionContent,SourceHints
1,1,15,SAFE,1,3,"{15, 16}=0","5"
1,2,23,MINE,2,7,"{23}=1","5,14"
...
```

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
   各セルの難易度レベル（Lv1〜Lv4）
        ↓
   AnalysisLogger.exportToCSV()
        ↓
   解析結果CSV
```

### TechniqueAnalyzerの詳細フロー

```
┌─────────────────────────────────────────────────────────────────┐
│                      analyze() 開始                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              初期ヒントの難易度を0に設定                          │
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
│  ・Regionをレベル順にソート                                       │
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
│    applyResult()      │   │         solveLv4()                │
│    changed = true     │   │  ・各セルに逆値を仮置き             │
│    次ラウンドへ         │   │  ・testContradiction()で矛盾検出   │
└───────────────────────┘   │  ・矛盾あれば確定                   │
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

#### 3. uncover操作

cover操作の逆順で復元します。これによりバックトラッキングが効率的に行えます。

### k-Hint解析のプロービング

```
対象セルcellについて:

1. Mineシナリオのテスト:
   - Safe行を禁止（matrix[rSafe]を全て0に）
   - DLX実行
   - 解があれば「Mineは可能」

2. Safeシナリオのテスト:
   - Mine行を禁止（matrix[rMine]を全て0に）
   - DLX実行
   - 解があれば「Safeは可能」

3. 判定:
   - Safe可能 かつ Mine不可能 → Safe確定
   - Safe不可能 かつ Mine可能 → Mine確定
   - 両方可能 → 確定できない
   - 両方不可能 → 盤面に矛盾（エラー）
```

### Lv4背理法の軽量版アルゴリズム

```java
for (each unknownCell):
    // 正解の逆を仮置き
    wrongValue = isMine ? SAFE : FLAGGED;
    tempBoard[cell] = wrongValue;
    
    // Lv1推論をループ
    while (true):
        for (each hintCell):
            neighbors = getNeighbors(hintCell);
            unknowns = filterUnknown(neighbors);
            flaggedCount = countFlagged(neighbors);
            remainingMines = hintValue - flaggedCount;
            
            // 矛盾チェック
            if (remainingMines < 0):
                return CONTRADICTION;  // 地雷過多
            if (remainingMines > unknowns.size()):
                return CONTRADICTION;  // 地雷置き場不足
            
            // 確定処理
            if (remainingMines == 0):
                markAllAsSafe(unknowns);
            if (remainingMines == unknowns.size()):
                markAllAsFlagged(unknowns);
        
        if (no change): break;
    
    return NO_CONTRADICTION;
```

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

### 出力例

```
===== 初期盤面 (正解) =====
 2  2  2  *  1  1  2  *  2  1 
 *  *  2  1  1  1  *  2  2  * 
 *  4  2  1  1  2  2  3  2  2 
...

===== 生成された問題 =====
 ?  2  ?  ?  ?  1  ?  ?  ?  1 
 ?  ?  2  ?  1  ?  ?  2  ?  ? 
...

--- 解析結果 (Technique Level) ---
 .  1  1  1  .  1  1  1  1  . 
 1  1  .  1  .  1  1  .  1  1 
 1  .  1  .  1  .  2  .  .  . 
...
```

---

## プロジェクト構成

```
src/
├── Main.java                # 統合実行クラス
│
├── PuzzleGenerator.java     # [生成] ランダム盤面生成
├── PuzzleMinimizer.java     # [生成] 一意解を維持したヒント削減
│
├── DancingLinks.java        # [Core] Algorithm X 実装
├── ConstraintBuilder.java   # [Core] マインスイーパ→Exact Cover行列変換
│
├── HintCountCalculator.java # [解析] k-Hint法の実装
├── TechniqueAnalyzer.java   # [解析] テクニックレベル法の実装
├── Region.java              # [解析] 領域演算ロジック
└── AnalysisLogger.java      # [Utility] CSVログ出力
```

---

## 参考文献

- Knuth, D. E. (2000). "Dancing Links". Millennial Perspectives in Computer Science.
- マインスイーパの論理的解法パターンに関する研究