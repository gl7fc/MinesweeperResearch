#!/usr/bin/env python3
"""
マインスイーパ推論グラフ可視化ツール
CSVから推論の親子関係をGraphviz DOT形式で出力する

使い方:
    python InferenceGraph.py <input.csv> <output.png>
    
注意: この新バージョンを使用してください。argparseを使わないシンプル版です。
"""

import csv
import sys
import subprocess
from pathlib import Path


# ノードの色定義
NODE_COLORS = {
    "HINT": "#CCCCCC",  # グレー（初期ヒント）
    "SAFE": "#90EE90",  # ライトグリーン
    "MINE": "#FF6B6B",  # ライトレッド
}

# エッジの色定義（Lv1〜Lv6）
EDGE_COLORS = {
    1: "#4169E1",  # 青 (Royal Blue)
    2: "#228B22",  # 緑 (Forest Green)
    3: "#FF8C00",  # オレンジ (Dark Orange)
    4: "#DC143C",  # 赤 (Crimson)
    5: "#8B008B",  # 紫 (Dark Magenta)
    6: "#8B4513",  # 茶 (Saddle Brown)
}


def parse_csv(filepath: str) -> list[dict]:
    """CSVファイルを読み込んでリストで返す"""
    rows = []
    with open(filepath, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def parse_cell_list(cell_str: str) -> list[int]:
    """カンマ区切りのセル番号文字列をリストに変換（数値以外はスキップ）"""
    if not cell_str or cell_str.strip() == "":
        return []
    result = []
    for x in cell_str.split(","):
        x = x.strip()
        if not x:
            continue
        try:
            result.append(int(x))
        except ValueError:
            pass
    return result


def safe_int(value: str, default: int = 0) -> int:
    """文字列を安全にintに変換（空や不正値はdefaultを返す）"""
    if not value or value.strip() == "":
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


def build_graph_data(rows: list[dict]) -> tuple[dict, list[tuple]]:
    """
    CSVデータからノードとエッジを構築
    
    Returns:
        nodes: {cell_index: {"result": str, "depth": int, "level": int}}
        edges: [(from_cell, to_cell, level), ...]
    """
    nodes = {}
    edges = []
    
    for i, row in enumerate(rows):
        cell_idx_str = row.get("CellIndex", "")
        if not cell_idx_str or cell_idx_str.strip() == "" or cell_idx_str == "CellIndex":
            continue
        
        cell_idx = safe_int(cell_idx_str, -1)
        if cell_idx < 0:
            continue
            
        result = row.get("Result", "").strip()
        # "HINT(数字)" の形式から "HINT" を抽出
        if result.startswith("HINT"):
            result = "HINT"
        
        depth = safe_int(row.get("GenerationDepth", ""), 0)
        level = safe_int(row.get("DifficultyLevel", ""), 0)
        source_hints = parse_cell_list(row.get("SourceHints", ""))
        trigger_cells = parse_cell_list(row.get("TriggerCells", ""))
        
        # ノード追加
        nodes[cell_idx] = {
            "result": result,
            "depth": depth,
            "level": level,
        }
        
        # エッジ追加（初期ヒント以外）
        if level > 0:
            for src in source_hints:
                edges.append((src, cell_idx, level))
            for src in trigger_cells:
                edges.append((src, cell_idx, level))
    
    return nodes, edges


def generate_dot(nodes: dict, edges: list[tuple], title: str = "Inference Graph") -> str:
    """Graphviz DOT形式の文字列を生成"""
    lines = []
    lines.append("digraph InferenceGraph {")
    lines.append(f'    label="{title}";')
    lines.append("    labelloc=t;")
    lines.append("    fontsize=20;")
    lines.append("    rankdir=TB;")
    lines.append("    node [style=filled, fontname=\"Helvetica\"];")
    lines.append("    edge [fontname=\"Helvetica\", fontsize=10];")
    lines.append("    newrank=true;")
    lines.append("    splines=true;")
    lines.append("")
    
    # 深さごとにノードをグループ化
    depths = {}
    for cell_idx, data in nodes.items():
        d = data["depth"]
        if d not in depths:
            depths[d] = []
        depths[d].append(cell_idx)
    
    # 左端の深さラベル用ノードを定義
    lines.append("    // Depth labels (left side)")
    for depth in sorted(depths.keys()):
        lines.append(f'    depth_label_{depth} [label="{depth}", shape=plaintext, fontsize=24, fontname="Helvetica", fontcolor="#333333"];')
    lines.append("")
    
    # 水平線用の右端ノード（不可視）
    lines.append("    // Right anchor nodes (invisible)")
    for depth in sorted(depths.keys()):
        lines.append(f'    depth_right_{depth} [label="", shape=none, width=0, height=0];')
    lines.append("")
    
    # 各深さのノードをグループ化
    for depth in sorted(depths.keys()):
        cells = depths[depth]
        lines.append(f"    // Depth {depth} nodes")
        lines.append("    {")
        lines.append("        rank=same;")
        lines.append(f"        depth_label_{depth};")
        
        for cell_idx in sorted(cells):
            data = nodes[cell_idx]
            result = data["result"]
            
            color = NODE_COLORS.get(result, "#FFFFFF")
            
            if result == "HINT":
                label = f"{cell_idx}"
            else:
                label = f"{cell_idx}\\n({result})"
            
            lines.append(f'        {cell_idx} [label="{label}", fillcolor="{color}", shape=ellipse];')
        
        lines.append(f"        depth_right_{depth};")
        lines.append("    }")
        lines.append("")
    
    # 深さラベル間の順序を強制（不可視エッジ）
    lines.append("    // Force depth order")
    sorted_depths = sorted(depths.keys())
    for i in range(len(sorted_depths) - 1):
        d1 = sorted_depths[i]
        d2 = sorted_depths[i + 1]
        lines.append(f'    depth_label_{d1} -> depth_label_{d2} [style=invis, weight=100];')
    lines.append("")
    
    # エッジ定義
    lines.append("    // Edges")
    for from_cell, to_cell, level in edges:
        color = EDGE_COLORS.get(level, "#000000")
        label = f"Lv{level}"
        lines.append(f'    {from_cell} -> {to_cell} [label="{label}", color="{color}", fontcolor="{color}"];')
    
    lines.append("}")
    
    return "\n".join(lines)


def main():
    if len(sys.argv) < 3:
        print("Usage: python InferenceGraph.py <input.csv> <output.png>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    # 出力フォーマットを拡張子から判定
    output_path = Path(output_file)
    output_format = output_path.suffix[1:] if output_path.suffix else "png"
    
    # 処理実行
    print(f"Reading: {input_file}")
    rows = parse_csv(input_file)
    print(f"  -> {len(rows)} rows loaded")
    
    nodes, edges = build_graph_data(rows)
    print(f"  -> {len(nodes)} nodes, {len(edges)} edges")
    
    dot_content = generate_dot(nodes, edges, "Minesweeper Inference Graph")
    
    # Graphvizで直接画像出力
    try:
        result = subprocess.run(
            ["dot", f"-T{output_format}", "-o", output_file],
            input=dot_content,
            text=True,
            capture_output=True
        )
        if result.returncode != 0:
            print(f"Error: {result.stderr}")
            sys.exit(1)
        print(f"✅ Output: {output_file}")
    except FileNotFoundError:
        print("Error: Graphviz (dot) がインストールされていません")
        print("  macOS: brew install graphviz")
        print("  Ubuntu: sudo apt install graphviz")
        print("  Windows: https://graphviz.org/download/")
        sys.exit(1)


if __name__ == "__main__":
    main()