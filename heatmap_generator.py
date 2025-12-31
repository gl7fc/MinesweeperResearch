#!/usr/bin/env python3
"""
マインスイーパ難易度解析結果のヒートマップ可視化スクリプト

使い方:
    python heatmap_generator.py board_data.csv [output.png]
"""

import sys
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap, BoundaryNorm
import matplotlib.font_manager as fm


def setup_japanese_font():
    """日本語フォントを設定（利用可能な場合）"""
    japanese_fonts = ['IPAGothic', 'IPAPGothic', 'Noto Sans CJK JP', 'TakaoPGothic', 'VL Gothic']
    for font_name in japanese_fonts:
        try:
            fm.findfont(font_name, fallback_to_default=False)
            plt.rcParams['font.family'] = font_name
            return font_name
        except:
            continue
    return None


def parse_board_data(filename):
    """CSVファイルからボードデータを読み込む"""
    boards = {}
    current_section = None
    current_data = []
    size = None
    
    with open(filename, 'r') as f:
        for line in f:
            line = line.strip()
            
            if line.startswith('# size='):
                size = int(line.split('=')[1])
                continue
            
            if line.startswith('#') or not line:
                if current_section and current_data:
                    boards[current_section] = np.array(current_data)
                    current_data = []
                continue
            
            if line.startswith('[') and line.endswith(']'):
                if current_section and current_data:
                    boards[current_section] = np.array(current_data)
                current_section = line[1:-1]
                current_data = []
                continue
            
            if current_section:
                row = [int(x) for x in line.split(',')]
                current_data.append(row)
    
    if current_section and current_data:
        boards[current_section] = np.array(current_data)
    
    return boards, size


def create_heatmap_figure(boards, size, output_file=None):
    """3つのヒートマップを横並びで生成"""
    
    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    # fig.suptitle('Minesweeper Difficulty Analysis', fontsize=16, fontweight='bold', y=0.98)
    
    puzzle = boards.get('PuzzleBoard', np.zeros((size, size)))
    technique = boards.get('TechniqueLevel', np.zeros((size, size)))
    khint = boards.get('kHintDifficulty', np.zeros((size, size)))
    
    # --- 1. 問題盤面 (左) ---
    plot_puzzle_board(axes[0], puzzle, size)
    axes[0].set_title('Puzzle Board', fontsize=12, fontweight='bold')
    
    # --- 2. k-Hint Difficulty (中央) ---
    plot_khint_heatmap(axes[1], khint, puzzle, size)
    axes[1].set_title('k-Hint (Required Hints)', fontsize=12, fontweight='bold')
    
    # --- 3. Technique Level (右) ---
    plot_technique_heatmap(axes[2], technique, puzzle, size)
    axes[2].set_title('Technique Level', fontsize=12, fontweight='bold')
    
    plt.tight_layout()
    
    if output_file:
        plt.savefig(output_file, dpi=150, bbox_inches='tight', 
                    facecolor='white', edgecolor='none')
        print(f"✅ Heatmap saved to: {output_file}")
    
    plt.show()
    return fig


def plot_puzzle_board(ax, board, size):
    """問題盤面を描画（色なし、?セルは空白）"""
    # 全体を白で塗りつぶし
    display = np.ones((size, size))
    
    ax.imshow(display, cmap='Greys', vmin=0, vmax=1, alpha=0)
    
    # セルに値を表示（ヒントのみ、?は空白）
    for i in range(size):
        for j in range(size):
            val = board[i, j]
            if val >= 0:  # ヒント
                ax.text(j, i, str(val), ha='center', va='center', 
                       fontsize=11, fontweight='bold', color='#333333')
            # val == -1 の場合は何も表示しない（空白）
    
    setup_grid(ax, size)


def plot_technique_heatmap(ax, technique, puzzle, size):
    """Technique Levelのヒートマップ"""
    # カラーマップ: 薄い→濃い青（レベルが高いほど濃い）
    # 0=初期ヒント(グレー), 1-6=難易度, -1=未解決(赤)
    colors = ['#d0d0d0',  # 0: 初期ヒント（グレー）
              '#deebf7',  # 1: Lv1
              '#9ecae1',  # 2: Lv2
              '#4292c6',  # 3: Lv3
              '#2171b5',  # 4: Lv4
              '#08519c',  # 5: Lv5
              '#08306b',  # 6: Lv6
              '#ff6666']  # -1: 未解決（赤）
    
    display = technique.copy().astype(float)
    display = np.where(display == -1, 99, display)  # -1を99に変換
    
    cmap = ListedColormap(colors)
    bounds = [-0.5, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 99]
    norm = BoundaryNorm(bounds, cmap.N)
    
    ax.imshow(display, cmap=cmap, norm=norm)
    
    # セルに値を表示
    for i in range(size):
        for j in range(size):
            val = technique[i, j]
            pval = puzzle[i, j]
            
            if pval >= 0:  # 初期ヒント → グレー背景に数字
                ax.text(j, i, str(pval), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color='#555555')
            elif val == -1:  # 未解決
                ax.text(j, i, '*', ha='center', va='center', 
                       fontsize=11, color='white', fontweight='bold')
            elif val > 0:  # 難易度レベル
                text_color = 'white' if val >= 3 else '#333333'
                ax.text(j, i, str(val), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color=text_color)
    
    setup_grid(ax, size)


def plot_khint_heatmap(ax, khint, puzzle, size):
    """k-Hint Difficultyのヒートマップ"""
    # カラーマップ: 薄い→濃い緑
    # 0=初期ヒント(グレー), 1-8=必要ヒント数, -1=未解決(赤)
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
            
            if pval >= 0:  # 初期ヒント → グレー背景に数字
                ax.text(j, i, str(pval), ha='center', va='center', 
                       fontsize=10, fontweight='bold', color='#555555')
            elif val == -1:  # 未解決
                ax.text(j, i, '*', ha='center', va='center', 
                       fontsize=11, color='white', fontweight='bold')
            elif val > 0:  # 必要ヒント数
                text_color = 'white' if val >= 4 else '#333333'
                ax.text(j, i, str(val), ha='center', va='center', 
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
    if len(sys.argv) < 2:
        print("Usage: python heatmap_generator.py <board_data.csv> [output.png]")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None
    
    setup_japanese_font()
    
    print(f"Loading data from: {input_file}")
    boards, size = parse_board_data(input_file)
    print(f"Board size: {size}x{size}")
    
    create_heatmap_figure(boards, size, output_file)


if __name__ == '__main__':
    main()