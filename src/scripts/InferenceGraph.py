#!/usr/bin/env python3
"""
マインスイーパ推論グラフ可視化ツール
CSVから推論の親子関係をGraphviz DOT形式で出力する

★更新: 
- Height列を使用（Java側で計算済み）
- heightラベルを0から最大値まで等間隔で表示
- 同じ親を持つノードを1つのノードにマージ
"""

import csv
import sys
import subprocess
from pathlib import Path
from collections import defaultdict


# ノードの色定義
NODE_COLORS = {
    "HINT": "#CCCCCC",
    "SAFE": "#90EE90",
    "MINE": "#FF6B6B",
}

# エッジの色定義（Lv1〜Lv6）
EDGE_COLORS = {
    1: "#4169E1",
    2: "#228B22",
    3: "#FF8C00",
    4: "#DC143C",
    5: "#8B008B",
    6: "#8B4513",
}


def parse_csv(filepath: str) -> list[dict]:
    rows = []
    with open(filepath, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def parse_cell_list(cell_str: str) -> list[int]:
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
    if not value or value.strip() == "":
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


def build_graph_data(rows: list[dict], merge_siblings: bool = True) -> tuple[dict, list[tuple], dict]:
    """
    CSVデータからノードとエッジを構築
    
    Returns:
        nodes: {node_id: {"cells": [cell_indices], "result": str, "height": int, "level": int}}
        edges: [(from_node_id, to_node_id, level), ...]
        cell_to_node: {cell_index: node_id}
    """
    # まず全セルの情報を収集
    cell_data = {}
    raw_edges = []
    
    for row in rows:
        cell_idx_str = row.get("CellIndex", "")
        if not cell_idx_str or cell_idx_str.strip() == "" or cell_idx_str == "CellIndex":
            continue
        
        cell_idx = safe_int(cell_idx_str, -1)
        if cell_idx < 0:
            continue
            
        result = row.get("Result", "").strip()
        if result.startswith("HINT"):
            result = "HINT"
        
        height = safe_int(row.get("Height", ""), -1)
        if height < 0:
            height = safe_int(row.get("GenerationDepth", ""), 0)
        
        level = safe_int(row.get("DifficultyLevel", ""), 0)
        source_hints = parse_cell_list(row.get("SourceHints", ""))
        trigger_cells = parse_cell_list(row.get("TriggerCells", ""))
        
        parents = frozenset(source_hints + trigger_cells)
        
        cell_data[cell_idx] = {
            "result": result,
            "height": height,
            "level": level,
            "parents": parents,
        }
        
        # 生エッジを収集
        if level > 0:
            for src in source_hints:
                raw_edges.append((src, cell_idx, level))
            for src in trigger_cells:
                raw_edges.append((src, cell_idx, level))
    
    # ★同じ(height, parents, result)を持つセルをグループ化
    if merge_siblings:
        # グループキー: (height, parents, result) → セルリスト
        groups = defaultdict(list)
        for cell_idx, data in cell_data.items():
            # height=0（初期ヒント）はグループ化しない
            if data["height"] == 0:
                key = (data["height"], frozenset(), data["result"], cell_idx)  # 個別キー
            else:
                key = (data["height"], data["parents"], data["result"])
            groups[key].append(cell_idx)
    else:
        # グループ化しない場合は全て個別
        groups = {(cell_data[c]["height"], frozenset(), cell_data[c]["result"], c): [c] 
                  for c in cell_data}
    
    # ノードを作成
    nodes = {}
    cell_to_node = {}
    
    for group_key, cells in groups.items():
        cells_sorted = sorted(cells)
        node_id = f"g_{cells_sorted[0]}"  # グループの代表セルをID化
        
        # グループ内の最初のセルからデータを取得
        first_cell = cells_sorted[0]
        data = cell_data[first_cell]
        
        nodes[node_id] = {
            "cells": cells_sorted,
            "result": data["result"],
            "height": data["height"],
            "level": data["level"],
        }
        
        for c in cells_sorted:
            cell_to_node[c] = node_id
    
    # エッジをノードIDベースに変換（重複除去）
    edge_set = set()
    for src_cell, dst_cell, level in raw_edges:
        if src_cell not in cell_to_node or dst_cell not in cell_to_node:
            continue
        src_node = cell_to_node[src_cell]
        dst_node = cell_to_node[dst_cell]
        if src_node != dst_node:  # 自己ループは除外
            edge_set.add((src_node, dst_node, level))
    
    edges = list(edge_set)
    
    return nodes, edges, cell_to_node


def generate_dot(nodes: dict, edges: list[tuple], title: str = "Inference Graph") -> str:
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
    
    # 高さの範囲を取得
    if nodes:
        max_height = max(data["height"] for data in nodes.values())
    else:
        max_height = 0
    all_heights = list(range(max_height + 1))
    
    # 高さごとにノードをグループ化
    heights = defaultdict(list)
    for node_id, data in nodes.items():
        heights[data["height"]].append(node_id)
    
    # 高さラベル
    lines.append("    // Height labels")
    for height in all_heights:
        lines.append(f'    height_label_{height} [label="{height}", shape=plaintext, fontsize=24, fontcolor="#333333"];')
    lines.append("")
    
    # 右端アンカー
    lines.append("    // Right anchors")
    for height in all_heights:
        lines.append(f'    height_right_{height} [label="", shape=none, width=0, height=0];')
    lines.append("")
    
    # ノード定義
    lines.append("    // Nodes")
    for node_id, data in sorted(nodes.items()):
        cells = data["cells"]
        result = data["result"]
        color = NODE_COLORS.get(result, "#FFFFFF")
        
        # ラベル: セル番号をカンマ区切り
        cells_str = ",".join(str(c) for c in cells)
        
        if result == "HINT":
            label = cells_str
        else:
            label = f"{cells_str}\\n({result})"
        
        lines.append(f'    {node_id} [label="{label}", fillcolor="{color}"];')
    lines.append("")
    
    # 各高さのノードをrank=same
    for height in all_heights:
        node_ids = heights.get(height, [])
        lines.append(f"    // Height {height}")
        lines.append("    {")
        lines.append("        rank=same;")
        lines.append(f"        height_label_{height};")
        for node_id in sorted(node_ids):
            lines.append(f"        {node_id};")
        lines.append(f"        height_right_{height};")
        lines.append("    }")
        lines.append("")
    
    # 縦順序
    if len(all_heights) > 1:
        lines.append("    // Vertical ordering")
        for i in range(len(all_heights) - 1):
            h1, h2 = all_heights[i], all_heights[i + 1]
            lines.append(f"    height_label_{h1} -> height_label_{h2} [style=invis];")
        lines.append("")
    
    # エッジ
    lines.append("    // Edges")
    for from_node, to_node, level in sorted(edges):
        if from_node not in nodes or to_node not in nodes:
            continue
        
        color = EDGE_COLORS.get(level, "#000000")
        label = f"Lv{level}"
        
        from_height = nodes[from_node]["height"]
        to_height = nodes[to_node]["height"]
        
        constraint = "false" if (level == 1 and from_height == to_height) else "true"
        
        lines.append(f'    {from_node} -> {to_node} [label="{label}", color="{color}", constraint={constraint}];')
    
    lines.append("}")
    return "\n".join(lines)


def render_graph(dot_content: str, output_path: str):
    ext = Path(output_path).suffix.lower()
    format_map = {".png": "png", ".svg": "svg", ".pdf": "pdf", ".jpg": "jpg"}
    output_format = format_map.get(ext, "png")
    
    try:
        subprocess.run(
            ["dot", f"-T{output_format}", "-o", output_path],
            input=dot_content, text=True, capture_output=True, check=True
        )
        print(f"✅ Graph saved to: {output_path}")
    except subprocess.CalledProcessError as e:
        print(f"❌ Graphviz error: {e.stderr}")
        sys.exit(1)
    except FileNotFoundError:
        print("❌ Graphviz not found. Install: brew install graphviz")
        sys.exit(1)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 inference_graph.py <input.csv> [output.png] [--no-merge]")
        print("  --no-merge: Don't merge sibling nodes")
        sys.exit(1)
    
    input_path = sys.argv[1]
    merge_siblings = True
    output_path = None
    
    for arg in sys.argv[2:]:
        if arg == "--no-merge":
            merge_siblings = False
        elif not output_path:
            output_path = arg
    
    if not output_path:
        output_path = Path(input_path).stem + ".png"
    
    print(f"📖 Reading: {input_path}")
    rows = parse_csv(input_path)
    print(f"   Found {len(rows)} rows")
    
    nodes, edges, cell_to_node = build_graph_data(rows, merge_siblings)
    
    # 統計
    total_cells = sum(len(data["cells"]) for data in nodes.values())
    merged_count = sum(1 for data in nodes.values() if len(data["cells"]) > 1)
    print(f"   Cells: {total_cells}, Nodes: {len(nodes)} (merged: {merged_count}), Edges: {len(edges)}")
    
    if nodes:
        max_height = max(data["height"] for data in nodes.values())
        print(f"   Height range: 0-{max_height}")
    
    title = Path(input_path).stem
    dot_content = generate_dot(nodes, edges, title)
    render_graph(dot_content, output_path)


if __name__ == "__main__":
    main()