#!/usr/bin/env python3
"""
マインスイーパ難易度解析結果のヒートマップ可視化スクリプト（初期盤面のみ）

使い方:
    python heatmap_generator.py <board_data.csv> <output.png>
"""

import sys
import numpy as np
import matplotlib.pyplot as plt
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
            fm.findfont(font_name, fallback_to_default=False)
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
    
    # 盤面データを構築
    puzzle_board = np.full((size, size), -1)
    
    for d in data:
        row = int(d['row'])
        col = int(d['col'])
        puzzle_board[row, col] = int(d['puzzle'])
    
    return puzzle_board, size


def create_heatmap_figure(puzzle, size, output_file):
    """初期盤面のみを生成"""
    
    fig, ax = plt.subplots(1, 1, figsize=(8, 8))
    
    plot_puzzle_board(ax, puzzle, size)
    ax.set_title('初期盤面', fontsize=14, fontweight='bold')
    
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
    puzzle, size = parse_board_data(input_file)
    print(f"Board size: {size}x{size}")
    
    create_heatmap_figure(puzzle, size, output_file)


if __name__ == '__main__':
    main()