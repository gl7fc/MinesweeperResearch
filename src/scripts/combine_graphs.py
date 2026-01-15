#!/usr/bin/env python3
"""
ヒント数ごとのフォルダ（hints_32, hints_33, ...）から画像を読み込み、
横並び×縦積みで1枚の画像に結合するスクリプト
"""

from PIL import Image, ImageDraw, ImageFont
import os
import glob
import sys

def combine_by_hint_folders(base_folder, output_path, spacing=10, label_width=500, scale=0.25):
    """
    ヒント数フォルダごとに画像を横並びにして縦に積み重ねる
    
    Args:
        base_folder: hints_XX フォルダがある親ディレクトリ
        output_path: 出力ファイルパス
        spacing: 画像間のスペース（ピクセル）
        label_width: 左側ラベル領域の幅
        scale: 画像の縮小率（0.25 = 25%サイズ, 0.5 = 50%サイズ）
    """
    
    # ラベル幅をスケールに合わせて調整
    label_width = max(40, int(label_width * scale))
    
    print(f"🔧 縮小率: {int(scale * 100)}%")
    
    # hints_XX フォルダを探索（数値順にソート）
    hint_folders = sorted(glob.glob(os.path.join(base_folder, "hints_*")),
                          key=lambda x: int(x.split("_")[-1]))
    
    if not hint_folders:
        print(f"❌ {base_folder} に hints_XX フォルダが見つかりません")
        return
    
    print(f"📁 {len(hint_folders)} 個のフォルダを検出")
    
    # 各行（ヒント数ごと）の画像を格納
    rows_data = []
    
    for folder in hint_folders:
        hint_num = folder.split("_")[-1]
        
        # フォルダ内のPNG画像を取得（ファイル名順）
        images_paths = sorted(glob.glob(os.path.join(folder, "*.png")))
        
        if not images_paths:
            print(f"  ⚠️ hints_{hint_num}: 画像なし、スキップ")
            continue
        
        # 画像を読み込み＆縮小
        images = []
        for p in images_paths:
            img = Image.open(p)
            if scale != 1.0:
                new_size = (int(img.width * scale), int(img.height * scale))
                img = img.resize(new_size, Image.Resampling.LANCZOS)
            images.append(img)
        print(f"  ✓ hints_{hint_num}: {len(images)} 枚")
        
        rows_data.append({
            'hint_num': hint_num,
            'images': images
        })
    
    if not rows_data:
        print("❌ 処理する画像がありません")
        return
    
    # 各行の寸法を計算
    row_dimensions = []
    for row in rows_data:
        images = row['images']
        # 行の幅 = 全画像の幅の合計 + スペース
        row_width = sum(img.width for img in images) + spacing * (len(images) - 1)
        # 行の高さ = 最大の画像の高さ
        row_height = max(img.height for img in images)
        row_dimensions.append((row_width, row_height))
    
    # キャンバスサイズを決定
    canvas_width = label_width + max(dim[0] for dim in row_dimensions) + spacing
    canvas_height = sum(dim[1] for dim in row_dimensions) + spacing * (len(rows_data) + 1)
    
    print(f"\n📐 出力サイズ: {canvas_width} x {canvas_height}")
    
    # キャンバス作成（白背景）
    canvas = Image.new('RGBA', (canvas_width, canvas_height), (255, 255, 255, 255))
    draw = ImageDraw.Draw(canvas)
    
    # フォント設定（システムフォントを試行、スケールに応じてサイズ調整）
    font_size = max(12, int(300 * scale))  # 最小12px
    try:
        # macOS
        font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", font_size)
    except:
        try:
            # Linux
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", font_size)
        except:
            try:
                # Windows
                font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", font_size)
            except:
                font = ImageFont.load_default()
                print("  ⚠️ デフォルトフォントを使用")
    
    # 各行を配置
    y_offset = spacing
    
    for i, row in enumerate(rows_data):
        hint_num = row['hint_num']
        images = row['images']
        row_height = row_dimensions[i][1]
        
        # ラベルを描画（垂直中央揃え）
        label = str(hint_num)
        bbox = draw.textbbox((0, 0), label, font=font)
        text_width = bbox[2] - bbox[0]
        text_height = bbox[3] - bbox[1]
        
        label_x = (label_width - text_width) // 2
        label_y = y_offset + (row_height - text_height) // 2
        draw.text((label_x, label_y), label, fill=(0, 0, 0, 255), font=font)
        
        # 画像を横に配置
        x_offset = label_width
        for img in images:
            # 垂直中央揃え
            img_y = y_offset + (row_height - img.height) // 2
            canvas.paste(img, (x_offset, img_y))
            x_offset += img.width + spacing
        
        y_offset += row_height + spacing
    
    # 保存（拡張子で判定）
    if output_path.lower().endswith('.pdf'):
        # PDF出力（RGBに変換が必要）
        canvas_rgb = canvas.convert('RGB')
        canvas_rgb.save(output_path, 'PDF', resolution=150)
        print(f"\n✅ 完成: {output_path} (PDF)")
    else:
        canvas.save(output_path)
        print(f"\n✅ 完成: {output_path}")
    
    print(f"   合計 {sum(len(r['images']) for r in rows_data)} 枚の画像を結合")


if __name__ == "__main__":
    # 使い方: python combine_inference_graphs.py [入力フォルダ] [出力ファイル] [縮小率]
    # 例: python combine_inference_graphs.py ./data result.pdf 0.25
    #     python combine_inference_graphs.py ./data result.png 0.5
    
    input_folder = sys.argv[1] if len(sys.argv) >= 2 else "/Users/blueb/Library/CloudStorage/GoogleDrive-rsu.merrypink@gmail.com/マイドライブ/2025/卒研/作業/251208/results_260112_121729/mines_30/layout_001/visualizations/"
    output_file = sys.argv[2] if len(sys.argv) >= 3 else "combined_inference_graphs.pdf"  # デフォルトPDF
    scale = float(sys.argv[3]) if len(sys.argv) >= 4 else 0.5  # デフォルト25%
    
    print(f"🔍 入力: {input_folder}")
    print(f"📄 出力: {output_file}\n")
    
    combine_by_hint_folders(input_folder, output_file, scale=scale)