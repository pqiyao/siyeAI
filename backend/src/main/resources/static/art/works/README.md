# サンプル画像の配置について

このディレクトリには作品画像を配置してください。

## 推奨仕様

### 作品画像（フル）
- パス: `/static/img/works/{id}.jpg`
- サイズ: 3000x4000px程度
- フォーマット: JPG, WebP推奨
- 容量: 500KB〜2MB程度

### サムネイル
- パス: `/static/img/works/{id}_thumb.jpg`
- サイズ: 600x800px程度
- フォーマット: JPG, WebP推奨
- 容量: 100KB〜300KB程度

### Hero画像
- パス: `/static/img/hero.jpg`
- サイズ: 1920x1080px
- フォーマット: JPG, WebP推奨
- 容量: 300KB〜800KB程度

## 現在のプレースホルダー

実際の画像が準備できるまで、SVGプレースホルダーを使用しています：
- `hero.svg` - Hero画像用
- `placeholder.svg` - 作品画像用

## 画像の追加方法

1. 作品画像を `static/img/works/` に配置
2. `data/works.json` の `image_path` と `thumbnail` を更新
3. `python generator.py` でサイトを再生成

## 画像最適化のヒント

- WebPフォーマット推奨（容量削減）
- サムネイルは必ず作成（ページ読み込み高速化）
- 過度な圧縮は避ける（品質維持）
- アスペクト比は3:4推奨
