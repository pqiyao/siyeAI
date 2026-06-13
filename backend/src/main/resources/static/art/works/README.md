# Artwork Asset Guidelines

This directory is reserved for static artwork assets served by the backend.

Before publishing or redistributing the repository, confirm every file in this area is either original, properly licensed, or a replaceable placeholder.

## Recommended Image Layout

Full artwork:

```text
static/art/works/{id}.jpg
```

Suggested properties:

- Around 3000x4000 px for high-quality artwork.
- JPG or WebP.
- Keep file size reasonable for web delivery.

Thumbnail:

```text
static/art/works/{id}_thumb.jpg
```

Suggested properties:

- Around 600x800 px.
- JPG or WebP.
- Optimized for list pages and previews.

Hero or banner image:

```text
static/art/works/hero.jpg
```

Suggested properties:

- Around 1920x1080 px.
- JPG or WebP.
- Optimized for fast initial loading.

## Placeholder Policy

If real artwork cannot be redistributed, replace it with placeholder SVG/JPG/WebP assets and document how users can provide their own files.

## Optimization Tips

- Prefer WebP for public deployments when browser support is acceptable.
- Always provide thumbnails for list pages.
- Avoid oversized images in the public repository.
- Keep aspect ratios consistent across related assets.
- Strip unnecessary metadata before release.
