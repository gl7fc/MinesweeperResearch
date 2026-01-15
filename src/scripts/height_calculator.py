#!/usr/bin/env python3
"""
CSV高さ計算ツール
GenerationDepthとDifficultyLevelから「Height」列を計算して追加する

使用方法:
    python3 height_calculator.py input.csv [output.csv] [--config config.json]

設定ファイル (JSON) の形式:
{
    "lv1_depth1": 1,    // depth=1 かつ Lv1 の増分
    "lv1_other": 0,     // depth>1 かつ Lv1 の増分
    "lv2": 2,           // Lv2 の増分
    "lv3": 3,           // Lv3 の増分
    "lv4": 4,           // Lv4 の増分
    "lv5": 5,           // Lv5 の増分
    "lv6": 6            // Lv6 の増分
}
"""

import csv
import json
import sys
import os
import argparse

# デフォルト設定
DEFAULT_CONFIG = {
    "lv1_depth1": 1,  # depth=1 かつ Lv1
    "lv1_other": 0,   # depth>1 かつ Lv1
    "lv2": 1,
    "lv3": 2,
    "lv4": 6,
    "lv5": 6.6,
    "lv6": 7.2
}


def load_config(config_path=None):
    """設定ファイルを読み込む（なければデフォルト）"""
    config = DEFAULT_CONFIG.copy()
    
    if config_path and os.path.exists(config_path):
        with open(config_path, 'r') as f:
            user_config = json.load(f)
            config.update(user_config)
        print(f"設定ファイル読み込み: {config_path}")
    else:
        print("デフォルト設定を使用")
    
    return config


def safe_int(value, default=0):
    """安全に整数変換"""
    if value is None or value == '':
        return default
    try:
        return int(value)
    except (ValueError, TypeError):
        return default


def parse_trigger_cells(trigger_str):
    """TriggerCells文字列をセット（整数）に変換"""
    cells = set()
    if not trigger_str or trigger_str.strip() == '':
        return cells
    
    for part in trigger_str.split(','):
        part = part.strip()
        if part.isdigit():
            cells.add(int(part))
    
    return cells


def calculate_height(depth, level, parent_heights, config):
    """
    高さを計算する
    
    Args:
        depth: GenerationDepth
        level: DifficultyLevel (1-6)
        parent_heights: 親セルの高さのリスト
        config: 設定辞書
    
    Returns:
        計算された高さ
    """
    # 初期ヒント
    if depth == 0:
        return 0
    
    # 親の最大高さ
    if parent_heights:
        max_parent_height = max(parent_heights)
    else:
        max_parent_height = 0
    
    # レベルに応じた増分を決定
    if level == 1:
        if depth == 1:
            increment = config.get("lv1_depth1", 1)
        else:
            increment = config.get("lv1_other", 0)
    else:
        key = f"lv{level}"
        increment = config.get(key, level)
    
    return max_parent_height + increment


def process_csv(input_path, output_path, config):
    """CSVを処理してHeight列を追加"""
    
    # CSVを読み込み
    rows = []
    with open(input_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        for row in reader:
            rows.append(row)
    
    print(f"読み込み: {len(rows)} 行")
    
    # セルごとの高さを記録
    cell_heights = {}
    
    # 初期ヒントの高さを0に設定（CSVに含まれないヒントセル）
    # SourceHintsとTriggerCellsから収集
    hint_cells = set()
    for row in rows:
        # SourceHintsから収集
        source_str = row.get('SourceHints', '')
        if source_str and 'Hints:' in source_str:
            source_str = source_str.replace('Hints:', '')
        for part in source_str.split(','):
            part = part.strip()
            if part.isdigit():
                hint_cells.add(int(part))
        
        # TriggerCellsから収集
        trigger_cells = parse_trigger_cells(row.get('TriggerCells', ''))
        hint_cells.update(trigger_cells)
    
    # CSVに記載されているセルを除外
    csv_cells = set()
    for row in rows:
        cell_idx = safe_int(row.get('CellIndex', -1), -1)
        if cell_idx >= 0:
            csv_cells.add(cell_idx)
    
    # 初期ヒント = 参照されているがCSVに記載されていないセル
    initial_hints = hint_cells - csv_cells
    for cell in initial_hints:
        cell_heights[cell] = 0
    
    print(f"初期ヒント数: {len(initial_hints)}")
    
    # GenerationDepthでソート
    rows_sorted = sorted(rows, key=lambda r: safe_int(r.get('GenerationDepth', 0)))
    
    # 各行について高さを計算
    for row in rows_sorted:
        cell_idx = safe_int(row.get('CellIndex', -1), -1)
        depth = safe_int(row.get('GenerationDepth', 0))
        level = safe_int(row.get('DifficultyLevel', 1))
        trigger_cells = parse_trigger_cells(row.get('TriggerCells', ''))
        
        # 親の高さを取得
        parent_heights = []
        for tc in trigger_cells:
            if tc in cell_heights:
                parent_heights.append(cell_heights[tc])
        
        # 高さを計算
        height = calculate_height(depth, level, parent_heights, config)
        cell_heights[cell_idx] = height
        row['Height'] = height
    
    # 出力（元の順序を維持）
    output_fieldnames = fieldnames + ['Height']
    
    with open(output_path, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=output_fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
    
    print(f"出力: {output_path}")
    
    # 統計情報
    heights = [safe_int(row.get('Height', 0)) for row in rows]
    if heights:
        print(f"高さの範囲: {min(heights)} ~ {max(heights)}")


def main():
    parser = argparse.ArgumentParser(
        description='CSVにHeight列を追加するツール',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
設定ファイル (JSON) の例:
{
    "lv1_depth1": 1,
    "lv1_other": 0,
    "lv2": 2,
    "lv3": 3,
    "lv4": 4,
    "lv5": 5,
    "lv6": 6
}
        """
    )
    parser.add_argument('input', help='入力CSVファイル')
    parser.add_argument('output', nargs='?', help='出力CSVファイル（省略時は _with_height を付加）')
    parser.add_argument('--config', '-c', help='設定JSONファイル')
    
    args = parser.parse_args()
    
    # 出力ファイル名
    if args.output:
        output_path = args.output
    else:
        base, ext = os.path.splitext(args.input)
        output_path = f"{base}_with_height{ext}"
    
    # 設定読み込み
    config = load_config(args.config)
    print(f"設定: {config}")
    
    # 処理実行
    process_csv(args.input, output_path, config)


if __name__ == '__main__':
    main()