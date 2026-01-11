#!/usr/bin/env python3
"""
マインスイーパ難易度解析結果のヒートマップ可視化スクリプト

使い方:
    python heatmap_generator.py <board_data.csv> <output.png>
"""

import sys
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap, BoundaryNorm
import matplotlib.font_manager as fm


def setup_japanese_font():
    """日本語フォントを設定（利用可能な場合）"""
    japanese_fonts = [
        'Hiragino Sans',
        'Hiragino Kaku Gothic Pro',
        'Hiragino Maru Gothic Pro',
        'IPAGothic',
        'IPAPGothic', 
        'Noto Sans CJK JP',
        'TakaoPGothic',
        'MS Gothic',
        'Meiryo',
        'Yu Gothic',
    ]
    for font_name in japanese_fonts:
        try:
            fm.findfind(font_name, fallback_to_default=False)
            plt.rcParams['font.family'] = font_name
            return font_name
        except:
            continue
    return None


def parse_board_data(filename):
    """CSVファイルからボードデータを読み込む（新形式対応）"""
    import csv
    
    # CSVを読み込む
    data = []
    with open(filename, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            data.append(row)
    
    if not data:
        return {}, None
    
    # サイズを推定（最大のrow値+1）
    max_row = max(int(d['row']) for d in data)
    max_col = max(int(d['col']) for d in data)
    size = max(max_row, max_col) + 1
    
    # 各種盤面データを構築
    boards = {}
    boards['PuzzleBoard'] = np.full((size, size), -1)
    boards['TechniqueLevel'] = np.full((size, size), -1)
    boards['kHintDifficulty'] = np.full((size, size), -1)
    
    for d in data:
        row = int(d['row'])
        col = int(d['col'])
        boards['PuzzleBoard'][row, col] = int(d['puzzle'])
        boards['TechniqueLevel'][row, col] = int(d['technique_level'])
        boards['kHintDifficulty'][row, col] = int(d['k_hint'])
    
    return boards, size


def create_heatmap_figure(boards, size, output_file):
    """3つのヒートマップを横並びで生成"""
    
    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    
    puzzle = boards.get('PuzzleBoard', np.zeros((size, size)))
    technique = boards.get('TechniqueLevel', np.zeros((size, size)))
    khint = boards.get('kHintDifficulty', np.zeros((size, size)))
    
    # --- 1. 問題盤面 (左) ---
    plot_puzzle_board(axes[0], puzzle, size)
    axes[0].set_title('初期盤面', fontsize=12, fontweight='bold')
    
    # --- 2. k-Hint Difficulty (中央) ---
    plot_khint_heatmap(axes[1], khint, puzzle, size)
    axes[1].set_title('必要ヒント数', fontsize=12, fontweight='bold')
    
    # --- 3. Technique Level (右) ---
    plot_technique_heatmap(axes[2], technique, puzzle, size)
    axes[2].set_title('必要テクニック', fontsize=12, fontweight='bold')
    
    plt.tight_layout()
    
    plt.savefig(output_file, dpi=150, bbox_inches='tight', 
                facecolor='white', edgecolor='none')
    print(f"✅ Heatmap saved to: {output_file}")
    
    plt.close(fig)


def plot_puzzle_board(ax, board, size):
    """問題盤面を描画（色なし、?セルは空白）"""
    display = np.ones((size, size))
    
    ax.imshow(display, cmap='Greys', vmin=0, vmax=1, alpha=0)
    
    for i in range(size):
        for j in range(size):
            val = board[i, j]
            if val >= 0:
                ax.text(j, i, str(int(val)), ha='center', va='center', 
                       fontsize=11, fontweight='bold', color='#333333')
    
    setup_grid(ax, size)


def plot_technique_heatmap(ax, technique, puzzle, size):
    """Technique Levelのヒートマップ"""
    colors = ['#d0d0d0',  # 0: 初期ヒント（グレー）
              '#deebf7',  # 1: Lv1
              '#9ecae1',  # 2: Lv2
              '#4292c6',  # 3: Lv3
              '#2171b5',  # 4: Lv4
              '#08519c',  # 5: Lv5
              '#08306b',  # 6: Lv6
              '#ff6666']  # -1: 未解決（赤）
    
    display = technique.copy().astype(float)
    display = np.where(display == -1, 99, display)
    
    cmap = ListedColormap(colors)
    bounds = [-0.5, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 99]
    norm = BoundaryNorm(bounds, cmap.N)
    
    ax.imshow(display, cmap=cmap, norm=norm)
    
    for i in range(size):
        for j in range(size):
            val = technique[i, j]
            pval = puzzle[i, j]
            
            if pval >= 0:
                ax.text(j, i, str(int(pval)), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color='#555555')
            elif val == -1:
                ax.text(j, i, '*', ha='center', va='center', 
                       fontsize=11, color='white', fontweight='bold')
            elif val > 0:
                text_color = 'white' if val >= 3 else '#333333'
                ax.text(j, i, str(int(val)), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color=text_color)
    
    setup_grid(ax, size)


def plot_khint_heatmap(ax, khint, puzzle, size):
    """k-Hint Difficultyのヒートマップ"""
    colors = ['#d0d0d0',  # 0: 初期ヒント（グレー）
              '#e5f5e0',  # 1
              '#c7e9c0',  # 2
              '#a1d99b',  # 3
              '#74c476',  # 4
              '#41ab5d',  # 5
              '#238b45',  # 6
              '#006d2c',  # 7
              '#00441b',  # 8
              '#ff6666']  # -1: 未解決（赤）
    
    display = khint.copy().astype(float)
    display = np.where(display == -1, 99, display)
    
    cmap = ListedColormap(colors)
    bounds = [-0.5, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 99]
    norm = BoundaryNorm(bounds, cmap.N)
    
    ax.imshow(display, cmap=cmap, norm=norm)
    
    for i in range(size):
        for j in range(size):
            val = khint[i, j]
            pval = puzzle[i, j]
            
            if pval >= 0:
                ax.text(j, i, str(int(pval)), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color='#555555')
            elif val == -1:
                ax.text(j, i, '*', ha='center', va='center', 
                       fontsize=11, color='white', fontweight='bold')
            elif val > 0:
                text_color = 'white' if val >= 4 else '#333333'
                ax.text(j, i, str(int(val)), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color=text_color)
    
    setup_grid(ax, size)


def setup_grid(ax, size):
    """グリッド線の設定"""
    ax.set_xticks(np.arange(-0.5, size, 1), minor=True)
    ax.set_yticks(np.arange(-0.5, size, 1), minor=True)
    ax.grid(which='minor', color='#999999', linestyle='-', linewidth=0.5)
    ax.tick_params(which='minor', size=0)
    
    ax.set_xticks(np.arange(0, size, 1))
    ax.set_yticks(np.arange(0, size, 1))
    ax.set_xticklabels(np.arange(0, size, 1), fontsize=7)
    ax.set_yticklabels(np.arange(0, size, 1), fontsize=7)


def main():
    if len(sys.argv) < 3:
        print("Usage: python heatmap_generator.py <board_data.csv> <output.png>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    setup_japanese_font()
    
    print(f"Loading data from: {input_file}")
    boards, size = parse_board_data(input_file)
    print(f"Board size: {size}x{size}")
    
    create_heatmap_figure(boards, size, output_file)


if __name__ == '__main__':
    main()