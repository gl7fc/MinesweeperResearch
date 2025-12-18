# Lv4推論の設計書

## 目次
1. [概要](#概要)
2. [全体アーキテクチャ](#全体アーキテクチャ)
3. [詳細設計](#詳細設計)
4. [データフロー](#データフロー)
5. [実装ステップ](#実装ステップ)

---

## 概要

### 目的
Lv1-3（Base Hint, Subset, Intersection）で解けないセルに対して、背理法（Contradiction）による推論を行う。

### 基本原理
あるセルについて「MINE」「SAFE」の2つの仮定を立て、DLXソルバーで解の存在を確認する。
- 片方の仮定が矛盾（解なし）→ もう片方が確定
- 両方とも解がある → このセルはLv4では確定できない

### 制約範囲の決定
「必要ヒント抽出プログラム」を使用して、各セルの確定に必要な最小限のヒント集合を取得し、その範囲内でDLXを実行する。

---

## 全体アーキテクチャ

### コンポーネント構成

```
TechniqueAnalyzer
  │
  ├─ analyze()                    # メインループ
  │   └─ solveFromPool()          # Lv1-3推論
  │       └─ solveWithContradiction()  # ★Lv4推論（新規）
  │           │
  │           ├─ selectCandidateCells()     # 候補セル選択
  │           ├─ extractRequiredHints()     # 必要ヒント抽出
  │           ├─ testAssumptionWithDLX()    # 仮定検証
  │           └─ buildConstraints()         # DLX制約構築
  │
  ├─ HintExtractor (外部)         # 必要ヒント抽出プログラム
  └─ DLXSolver (外部)             # DLXソルバー
```

### 処理フロー

```
Round N:
  ┌─ Lv1-3推論
  │   ├─ Region生成・メンテナンス
  │   └─ solveFromPool()
  │       └─ 確定したセルあり？
  │           Yes → 盤面更新 → 次のラウンドへ
  │           No  → ↓
  │
  ├─ Lv4推論（新規）
  │   └─ solveWithContradiction()
  │       ├─ 候補セル選択（優先度順）
  │       └─ 各候補について:
  │           ├─ 必要ヒント抽出
  │           ├─ 「MINE仮定」でDLX検証
  │           ├─ 「SAFE仮定」でDLX検証
  │           └─ 片方が矛盾 → 確定 → return
  │
  └─ 確定したセルなし → 解析終了
```

---

## 詳細設計

### 1. 候補セル選択

#### メソッド: `selectCandidateCells()`

**目的**: 未確定セルから優先度の高いものを選択

**優先度の計算基準**:
1. 多くのRegionに含まれるセル（スコア +10）
2. Lv1 Regionに含まれる場合（追加 +5）
3. 確定済みヒントに隣接するセル（1つにつき +3）

**出力**: 
- 優先度降順にソートされた候補セルリスト
- 上位N個（例: 10個）または全て

```java
private List<Integer> selectCandidateCells() {
    // 1. 未確定セル（board[i] == MINE）を全て抽出
    // 2. 優先度計算（calculateCellPriority）
    // 3. 降順ソート
    // 4. 上位N個を返す
}

private int calculateCellPriority(int cell) {
    // Region含有数 × 10
    // + Lv1 Region含有数 × 5
    // + 隣接ヒント数 × 3
}
```

---

### 2. 必要ヒント抽出

#### メソッド: `extractRequiredHints(int cellIndex)`

**目的**: あるセルの確定に必要な最小限のヒント集合を取得

**入力**:
- `cellIndex`: 対象セルのインデックス

**出力**:
- `Set<Integer>`: 必要なヒントのインデックス集合
- 例: `{3, 5, 12}` → ヒント3, 5, 12があればこのセルが確定できる

**実装**:
```java
private Set<Integer> extractRequiredHints(int cellIndex) {
    // ★外部の必要ヒント抽出プログラムを呼び出し
    return HintExtractor.getRequiredHints(
        board,           // 現在の盤面
        completeBoard,   // 完全解
        size,            // 盤面サイズ
        cellIndex        // 対象セル
    );
}
```

**呼び出しタイミング**: 各セルについて1回のみ

---

### 3. DLX制約構築

#### メソッド: `buildConstraints(Set<Integer> requiredHints, int targetCell, int assumedValue)`

**目的**: DLXソルバーに渡す制約リストを構築

**入力**:
- `requiredHints`: 必要なヒントのインデックス集合
- `targetCell`: 仮定するセル
- `assumedValue`: 仮定する値（`FLAGGED` または `SAFE`）

**出力**:
- `List<DLXConstraint>`: 制約のリスト

**制約の種類**:

1. **ヒント由来の制約**（各必要ヒントについて）
   ```
   制約: {relevantCells} の中に remainingMines 個の地雷
   
   relevantCells = ヒントの周囲8セルのうち:
     - 未確定セル（board[i] == MINE）
     - 対象セル（targetCell）
   
   remainingMines = ヒントの数値 - 既に確定した地雷数
   ```

2. **対象セルの固定制約**
   ```
   assumedValue == FLAGGED の場合:
     制約: {targetCell} の中に 1 個の地雷
   
   assumedValue == SAFE の場合:
     制約: {targetCell} の中に 0 個の地雷
   ```

**実装例**:
```java
private List<DLXConstraint> buildConstraints(
        Set<Integer> requiredHints, 
        int targetCell, 
        int assumedValue) {
    
    List<DLXConstraint> constraints = new ArrayList<>();
    
    // 1. 各必要ヒントから制約生成
    for (int hintIndex : requiredHints) {
        int hintValue = board[hintIndex];
        List<Integer> neighbors = getNeighbors(hintIndex);
        
        Set<Integer> relevantCells = new HashSet<>();
        int alreadyFlagged = 0;
        
        for (int nb : neighbors) {
            if (nb == targetCell) {
                relevantCells.add(nb);
            } else if (board[nb] == MINE) {
                relevantCells.add(nb);
            } else if (board[nb] == FLAGGED) {
                alreadyFlagged++;
            }
        }
        
        int remainingMines = hintValue - alreadyFlagged;
        constraints.add(new DLXConstraint(relevantCells, remainingMines));
    }
    
    // 2. 対象セルの固定制約
    if (assumedValue == FLAGGED) {
        constraints.add(new DLXConstraint(Set.of(targetCell), 1));
    } else if (assumedValue == SAFE) {
        constraints.add(new DLXConstraint(Set.of(targetCell), 0));
    }
    
    return constraints;
}
```

---

### 4. 仮定検証

#### メソッド: `testAssumptionWithDLX(Set<Integer> requiredHints, int targetCell, int assumedValue)`

**目的**: ある仮定のもとで解が存在するかDLXで判定

**入力**:
- `requiredHints`: 必要なヒント集合
- `targetCell`: 対象セル
- `assumedValue`: 仮定する値

**出力**:
- `boolean`: 解が存在するか
  - `true`: 仮定は矛盾しない（解がある）
  - `false`: 仮定は矛盾する（解がない）

**実装**:
```java
private boolean testAssumptionWithDLX(
        Set<Integer> requiredHints,
        int targetCell,
        int assumedValue) {
    
    // 制約リスト構築
    List<DLXConstraint> constraints = buildConstraints(
        requiredHints, targetCell, assumedValue
    );
    
    // DLXで解の有無を判定
    return DLXSolver.hasSolution(constraints);
}
```

---

### 5. Lv4メイン処理

#### メソッド: `solveWithContradiction()`

**目的**: 背理法による推論を実行

**戻り値**: `Map<Integer, Integer>`
- 確定したセルのマップ（セルインデックス → 値）
- 空の場合、Lv4では何も確定できなかった

**処理フロー**:

```
1. 候補セル選択
   candidates = selectCandidateCells()

2. 各候補についてループ
   for each cell in candidates:
   
   a) 必要ヒント抽出（1回のみ）
      requiredHints = extractRequiredHints(cell)
   
   b) 仮定1: cell = MINE
      mineValid = testAssumptionWithDLX(requiredHints, cell, FLAGGED)
   
   c) 仮定2: cell = SAFE
      safeValid = testAssumptionWithDLX(requiredHints, cell, SAFE)
   
   d) 結果判定
      ┌─────────────────────────────────────┐
      │ mineValid  safeValid  結論          │
      ├─────────────────────────────────────┤
      │ false      true       SAFE確定      │
      │ true       false      MINE確定      │
      │ true       true       確定できない  │
      │ false      false      エラー        │
      └─────────────────────────────────────┘
      
      確定した場合:
        - ログ記録
        - return（このラウンド終了）
      
      確定しない場合:
        - 次の候補へ

3. 全候補を試して確定できず
   return empty
```

**実装骨格**:
```java
private Map<Integer, Integer> solveWithContradiction() {
    System.out.println("  [Lv4] Starting contradiction analysis...");
    
    List<Integer> candidates = selectCandidateCells();
    System.out.println("  [Lv4] Testing " + candidates.size() + " candidates");
    
    for (int cell : candidates) {
        System.out.println("  [Lv4] Testing cell " + cell);
        
        // 必要ヒント抽出
        Set<Integer> requiredHints = extractRequiredHints(cell);
        String hintsStr = requiredHints.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        
        System.out.println("    Required hints: " + hintsStr);
        
        // 仮定検証
        System.out.println("    Testing: cell = MINE");
        boolean mineValid = testAssumptionWithDLX(requiredHints, cell, FLAGGED);
        System.out.println("      → " + (mineValid ? "Valid" : "CONTRADICTION"));
        
        System.out.println("    Testing: cell = SAFE");
        boolean safeValid = testAssumptionWithDLX(requiredHints, cell, SAFE);
        System.out.println("      → " + (safeValid ? "Valid" : "CONTRADICTION"));
        
        // 結果判定
        if (!mineValid && safeValid) {
            System.out.println("  [Lv4] ★ Cell " + cell + " = SAFE");
            Map<Integer, Integer> result = new HashMap<>();
            result.put(cell, SAFE);
            logger.logStep(currentRound, cell, "SAFE", LV_4,
                          -1, "Contradiction(MINE→×)", hintsStr);
            return result;
            
        } else if (mineValid && !safeValid) {
            System.out.println("  [Lv4] ★ Cell " + cell + " = MINE");
            Map<Integer, Integer> result = new HashMap<>();
            result.put(cell, FLAGGED);
            logger.logStep(currentRound, cell, "MINE", LV_4,
                          -1, "Contradiction(SAFE→×)", hintsStr);
            return result;
            
        } else if (!mineValid && !safeValid) {
            System.err.println("  [Lv4] ERROR: Both assumptions invalid for cell " + cell);
            
        } else {
            System.out.println("  [Lv4] Cell " + cell + " cannot be determined");
        }
    }
    
    System.out.println("  [Lv4] No cells determined");
    return new HashMap<>();
}
```

---

### 6. 既存コードへの統合

#### `solveFromPool()` の修正

```java
private Map<Integer, Integer> solveFromPool() {
    Map<Integer, Integer> deduced = new HashMap<>();
    Map<Integer, Integer> deducedLevel = new HashMap<>();
    
    // === 既存のLv1-3推論 ===
    List<Region> sortedRegions = new ArrayList<>(regionPool.values());
    sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));
    
    for (Region r : sortedRegions) {
        // ... (既存のコード: Regionから確定セルを探す) ...
    }
    
    updateDifficultyMap(deducedLevel);
    
    // === Lv4推論の追加 ===
    if (deduced.isEmpty()) {
        // Lv1-3で何も解けなかった場合、Lv4を試す
        deduced = solveWithContradiction();
        
        if (!deduced.isEmpty()) {
            // Lv4で確定した場合、難易度マップを更新
            Map<Integer, Integer> lv4Map = new HashMap<>();
            for (int cell : deduced.keySet()) {
                lv4Map.put(cell, LV_4);
            }
            updateDifficultyMap(lv4Map);
        }
    }
    
    return deduced;
}
```

#### 定数の追加

```java
// TechniqueAnalyzer クラスに追加
public static final int LV_4 = 4; // 背理法 (Contradiction)
```

---

## データフロー

### セルの確定フロー

```
未確定セル (board[i] == MINE)
    ↓
候補選択（優先度順）
    ↓
Cell X を選択
    ↓
必要ヒント抽出
    ↓
{Hint A, Hint B, Hint C}
    ↓
┌─────────────────┬─────────────────┐
│  仮定1: X=MINE  │  仮定2: X=SAFE  │
│                 │                 │
│  制約構築        │  制約構築        │
│  ・Hint A制約   │  ・Hint A制約   │
│  ・Hint B制約   │  ・Hint B制約   │
│  ・Hint C制約   │  ・Hint C制約   │
│  ・X=1個の地雷  │  ・X=0個の地雷  │
│                 │                 │
│  DLX実行        │  DLX実行        │
│  ↓             │  ↓             │
│  解なし(×)     │  解あり(○)     │
└─────────────────┴─────────────────┘
           ↓
    X = SAFE 確定！
           ↓
    ログ記録（Lv4）
           ↓
    盤面更新
           ↓
    次のラウンドへ
```

### ログ出力例

```csv
Round,Cell,Type,Level,RegionID,RegionInfo,SourceHints
5,42,SAFE,4,-1,Contradiction(MINE→×),"3,5,12"
```

---

## 実装ステップ

### Phase 1: 基盤整備（外部依存なし）

**目的**: Lv4のメイン構造を実装し、ダミーで動作確認

#### Step 1.1: 定数とメソッドスケルトン追加
- [ ] `LV_4 = 4` 定数追加
- [ ] `solveWithContradiction()` メソッドスケルトン
- [ ] `solveFromPool()` にLv4呼び出し追加（コメントアウト状態）

**成果物**: コンパイルが通る状態

---

#### Step 1.2: 候補セル選択機能
- [ ] `selectCandidateCells()` 実装
  - 未確定セル抽出
  - 優先度計算ロジック
  - ソート処理
- [ ] `calculateCellPriority()` 実装
- [ ] デバッグ出力追加

**テスト方法**: 
```java
// analyze() の最初で
List<Integer> test = selectCandidateCells();
System.out.println("Candidates: " + test);
```

**成果物**: 優先度順の候補リストが出力される

---

### Phase 2: DLX連携準備（モック使用）

**目的**: DLX呼び出しまでの流れを実装

#### Step 2.1: 制約構築機能
- [ ] `DLXConstraint` クラス作成（仮）
  ```java
  class DLXConstraint {
      Set<Integer> cells;
      int mineCount;
  }
  ```
- [ ] `buildConstraints()` 実装
  - 必要ヒントから制約生成
  - 固定制約追加
- [ ] デバッグ出力で制約内容を確認

**テスト方法**:
```java
Set<Integer> hints = Set.of(3, 5, 12);
List<DLXConstraint> constraints = buildConstraints(hints, 42, FLAGGED);
// 制約内容を出力
```

**成果物**: 制約が正しく構築される

---

#### Step 2.2: DLXモックで動作確認
- [ ] `DLXSolver` モッククラス作成
  ```java
  class DLXSolver {
      static boolean hasSolution(List<DLXConstraint> constraints) {
          // とりあえずランダムで true/false
          return Math.random() > 0.5;
      }
  }
  ```
- [ ] `testAssumptionWithDLX()` 実装
- [ ] `solveWithContradiction()` 本体実装
  - 候補ループ
  - 2つの仮定検証
  - 結果判定
  - ログ記録
- [ ] `solveFromPool()` のLv4呼び出しを有効化

**テスト方法**: 実際に盤面でLv1-3が詰まった時にLv4が呼ばれるか確認

**成果物**: Lv4推論が（モックながら）動作する

---

### Phase 3: 必要ヒント抽出統合

**目的**: 実際の必要ヒント抽出機能を統合

#### Step 3.1: 必要ヒント抽出プログラム準備
- [ ] 既存の必要ヒント抽出プログラムを統合
  - インターフェース確認
  - 必要な入力形式確認
- [ ] `HintExtractor` クラス作成
  ```java
  class HintExtractor {
      static Set<Integer> getRequiredHints(
          int[] board, int[] solution, int size, int cellIndex) {
          // 実装
      }
  }
  ```

**テスト方法**: 既知のセルで必要ヒントが正しく抽出されるか確認

---

#### Step 3.2: Lv4に統合
- [ ] `extractRequiredHints()` 実装
- [ ] `solveWithContradiction()` に統合
- [ ] モックの代わりに実際の必要ヒント抽出を使用

**テスト方法**: デバッグ出力で抽出されたヒントが妥当か確認

**成果物**: 必要ヒントが正しく抽出される

---

### Phase 4: DLXソルバー統合

**目的**: 実際のDLXソルバーで矛盾判定を行う

#### Step 4.1: DLXソルバー準備
- [ ] 既存のDLXソルバーを統合
  - インターフェース確認
  - 制約形式の調整
- [ ] `DLXSolver.hasSolution()` 実装

**テスト方法**: 簡単な制約で解の有無が正しく判定されるか確認

---

#### Step 4.2: 完全統合
- [ ] モックDLXを実際のDLXに置き換え
- [ ] エラーハンドリング追加
- [ ] パフォーマンス測定

**テスト方法**: 実際の難問盤面で動作確認

**成果物**: Lv4推論が完全に動作する

---

### Phase 5: 最適化とログ強化

**目的**: 実用レベルに仕上げる

#### Step 5.1: パフォーマンス最適化
- [ ] 候補セル数の調整（MAX_CELLS_TO_TEST）
- [ ] 優先度計算の改善
- [ ] DLX呼び出し回数の削減（可能なら）

---

#### Step 5.2: ログとデバッグ出力の整備
- [ ] CSV出力フォーマット確認
- [ ] エラーケースのハンドリング
- [ ] デバッグ出力のON/OFF切り替え

**成果物**: 実用可能なLv4推論システム

---

### Phase 6: テストと検証

#### Step 6.1: テストケース作成
- [ ] Lv4が必要な盤面を複数用意
- [ ] 各レベルの推論が正しく動作するか確認
- [ ] ログが正しく出力されるか確認

---

#### Step 6.2: 統合テスト
- [ ] 複数の盤面で end-to-end テスト
- [ ] パフォーマンス測定
- [ ] エッジケースの確認

**成果物**: 完成したLv4推論システム

---

## 実装の順番まとめ

```
Phase 1: 基盤整備
  ├─ Step 1.1: スケルトン作成
  └─ Step 1.2: 候補セル選択

Phase 2: DLX連携準備
  ├─ Step 2.1: 制約構築
  └─ Step 2.2: DLXモック統合

Phase 3: 必要ヒント抽出統合
  ├─ Step 3.1: HintExtractor準備
  └─ Step 3.2: Lv4に統合

Phase 4: DLXソルバー統合
  ├─ Step 4.1: DLXSolver準備
  └─ Step 4.2: 完全統合

Phase 5: 最適化とログ強化
  ├─ Step 5.1: パフォーマンス最適化
  └─ Step 5.2: ログ整備

Phase 6: テストと検証
  ├─ Step 6.1: テストケース
  └─ Step 6.2: 統合テスト
```

**推奨**: Phase 1, 2をまず完成させて動作確認し、その後Phase 3以降を進める。