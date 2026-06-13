-- Public placeholder seed data for the illustration gallery.
-- Replace these rows and static assets with artwork you have rights to redistribute.
INSERT INTO app_illustration_work (
    title, slug, category, tags_json, description, cover_url, image_url,
    content_level, status, source, recommended, sort_order, reviewed_at, created_at
) VALUES
('Sample Gallery Card', 'sample-gallery-card', 'Sample', '["sample","placeholder"]',
 'A safe placeholder record for local development and public demos.',
 '/art/works/placeholder.svg', '/art/works/placeholder.svg',
 'NORMAL', 'PUBLISHED', 'ADMIN', 1, 1, '2026-01-01 12:00:00', '2026-01-01 12:00:00'),
('Upload Your Artwork', 'upload-your-artwork', 'Sample', '["replace-me"]',
 'Use the admin console or database migration files to add your own redistributable artwork.',
 '/art/works/placeholder.svg', '/art/works/placeholder.svg',
 'NORMAL', 'PUBLISHED', 'ADMIN', 0, 2, '2026-01-01 12:00:00', '2026-01-01 12:00:00')
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    category = VALUES(category),
    tags_json = VALUES(tags_json),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    image_url = VALUES(image_url),
    content_level = VALUES(content_level),
    status = VALUES(status),
    source = VALUES(source),
    recommended = VALUES(recommended),
    sort_order = VALUES(sort_order),
    reviewed_at = VALUES(reviewed_at),
    deleted = 0;
