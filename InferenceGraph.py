#!/usr/bin/env python3
"""
マインスイーパ推論グラフ可視化ツール
CSVから推論の親子関係をGraphviz DOT形式で出力する
"""

import csv
import argparse
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

# レベル名
LEVEL_NAMES = {
    0: "Initial",
    1: "Base Hint",
    2: "Subset",
    3: "Intersection",
    4: "Contradiction+Lv1",
    5: "Contradiction+Lv2",
    6: "Contradiction+Lv3",
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
        # 数値のみ抽出（"Assumed:MINE" などはスキップ）
        try:
            result.append(int(x))
        except ValueError:
            # 数値でない場合はスキップ
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
        # 空行やヘッダー重複をスキップ
        cell_idx_str = row.get("CellIndex", "")
        if not cell_idx_str or cell_idx_str.strip() == "" or cell_idx_str == "CellIndex":
            print(f"  Warning: Skipping row {i+1} (empty or invalid CellIndex)")
            continue
        
        cell_idx = safe_int(cell_idx_str, -1)
        if cell_idx < 0:
            print(f"  Warning: Skipping row {i+1} (invalid CellIndex: {cell_idx_str})")
            continue
            
        result = row.get("Result", "").strip()
        depth = safe_int(row.get("GenerationDepth", ""), 0)  # GenerationDepthを使用
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
    lines.append("    rankdir=TB;")  # Top to Bottom
    lines.append("    node [style=filled, fontname=\"Helvetica\"];")
    lines.append("    edge [fontname=\"Helvetica\", fontsize=10];")
    lines.append("")
    
    # 深さごとにノードをグループ化
    depths = {}
    for cell_idx, data in nodes.items():
        d = data["depth"]
        if d not in depths:
            depths[d] = []
        depths[d].append(cell_idx)
    
    # 各深さをサブグラフとして定義（同じ深さに配置）
    for depth in sorted(depths.keys()):
        cells = depths[depth]
        lines.append(f"    // Depth {depth}")
        lines.append("    {")
        lines.append("        rank=same;")
        for cell_idx in sorted(cells):
            data = nodes[cell_idx]
            result = data["result"]
            level = data["level"]
            
            # ノードの色
            color = NODE_COLORS.get(result, "#FFFFFF")
            
            # ノードのラベル
            if result == "HINT":
                label = f"{cell_idx}"
            else:
                label = f"{cell_idx}\\n({result})"
            
            lines.append(f'        {cell_idx} [label="{label}", fillcolor="{color}"];')
        lines.append("    }")
        lines.append("")
    
    # エッジ定義
    lines.append("    // Edges")
    for from_cell, to_cell, level in edges:
        color = EDGE_COLORS.get(level, "#000000")
        label = f"Lv{level}"
        lines.append(f'    {from_cell} -> {to_cell} [label="{label}", color="{color}", fontcolor="{color}"];')
    
    lines.append("")
    
    # 凡例（Legend）
    lines.append("    // Legend")
    lines.append('    subgraph cluster_legend {')
    lines.append('        label="Legend";')
    lines.append('        fontsize=14;')
    lines.append('        style=rounded;')
    lines.append('        color="#888888";')
    lines.append("")
    lines.append("        // Node types")
    lines.append(f'        legend_hint [label="Initial Hint", fillcolor="{NODE_COLORS["HINT"]}", shape=box];')
    lines.append(f'        legend_safe [label="SAFE", fillcolor="{NODE_COLORS["SAFE"]}", shape=box];')
    lines.append(f'        legend_mine [label="MINE", fillcolor="{NODE_COLORS["MINE"]}", shape=box];')
    lines.append("")
    lines.append("        // Edge types (invisible edges for layout)")
    lines.append("        legend_hint -> legend_safe -> legend_mine [style=invis];")
    lines.append("")
    
    # エッジ凡例
    for lv in range(1, 7):
        color = EDGE_COLORS[lv]
        name = LEVEL_NAMES[lv]
        lines.append(f'        legend_lv{lv}_a [label="", shape=point, width=0.1];')
        lines.append(f'        legend_lv{lv}_b [label="Lv{lv}: {name}", shape=plaintext];')
        lines.append(f'        legend_lv{lv}_a -> legend_lv{lv}_b [label="Lv{lv}", color="{color}", fontcolor="{color}"];')
    
    lines.append("    }")
    
    lines.append("}")
    
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="マインスイーパ推論グラフ可視化ツール"
    )
    parser.add_argument(
        "input_csv",
        help="入力CSVファイルパス"
    )
    parser.add_argument(
        "-o", "--output",
        help="出力PNGファイルパス（省略時は入力ファイル名.png）"
    )
    parser.add_argument(
        "-t", "--title",
        default="Minesweeper Inference Graph",
        help="グラフのタイトル"
    )
    parser.add_argument(
        "-f", "--format",
        default="png",
        choices=["png", "svg", "pdf"],
        help="出力フォーマット（デフォルト: png）"
    )
    
    args = parser.parse_args()
    
    # 出力パス決定
    input_path = Path(args.input_csv)
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = input_path.with_suffix(f".{args.format}")
    
    # 処理実行
    print(f"Reading: {input_path}")
    rows = parse_csv(args.input_csv)
    print(f"  -> {len(rows)} rows loaded")
    
    nodes, edges = build_graph_data(rows)
    print(f"  -> {len(nodes)} nodes, {len(edges)} edges")
    
    dot_content = generate_dot(nodes, edges, args.title)
    
    # Graphvizで直接画像出力
    import subprocess
    try:
        result = subprocess.run(
            ["dot", f"-T{args.format}", "-o", str(output_path)],
            input=dot_content,
            text=True,
            capture_output=True
        )
        if result.returncode != 0:
            print(f"Error: {result.stderr}")
            return
        print(f"Output: {output_path}")
    except FileNotFoundError:
        print("Error: Graphviz (dot) がインストールされていません")
        print("  macOS: brew install graphviz")
        print("  Ubuntu: sudo apt install graphviz")
        print("  Windows: https://graphviz.org/download/")


if __name__ == "__main__":
    main()