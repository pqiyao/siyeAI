INSERT INTO app_illustration_notice
  (category, type_label, title, content, points_json, important, enabled, sort_order, created_at)
SELECT 'review', '审核', '上传作品会先进入人工审核',
       '用户提交的插画、壁纸和角色图不会直接公开展示。管理员会先确认图片清晰度、公开展示适配度和基础内容安全。',
       '["通过后进入画廊展示","驳回后可调整信息重新提交","低清、水印过重、明显侵权内容会被拒绝"]',
       1, 1, 10, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_illustration_notice WHERE category = 'review' AND sort_order = 10
);

UPDATE app_illustration_notice
SET type_label = '审核',
    title = '上传作品会先进入人工审核',
    content = '用户提交的插画、壁纸和角色图不会直接公开展示。管理员会先确认图片清晰度、公开展示适配度和基础内容安全。',
    points_json = '["通过后进入画廊展示","驳回后可调整信息重新提交","低清、水印过重、明显侵权内容会被拒绝"]',
    important = 1,
    enabled = 1
WHERE category = 'review' AND sort_order = 10;

INSERT INTO app_illustration_notice
  (category, type_label, title, content, points_json, important, enabled, sort_order, created_at)
SELECT 'update', '更新', '画廊分页已调整为每页 18 个作品',
       '画廊页会按 18 个作品分页展示，方便桌面端浏览，也避免一次加载过多图片。',
       '[]',
       0, 1, 20, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_illustration_notice WHERE category = 'update' AND sort_order = 20
);

UPDATE app_illustration_notice
SET type_label = '更新',
    title = '画廊分页已调整为每页 18 个作品',
    content = '画廊页会按 18 个作品分页展示，方便桌面端浏览，也避免一次加载过多图片。',
    points_json = '[]',
    important = 0,
    enabled = 1
WHERE category = 'update' AND sort_order = 20;

INSERT INTO app_illustration_notice
  (category, type_label, title, content, points_json, important, enabled, sort_order, created_at)
SELECT 'rule', '规则', '15+ 与 18+ 内容会分级管理',
       '站点会对 15+、18+ 内容做明确访问控制。18+ 内容不会默认展示，只有输入有效临时密钥后才可以查看。',
       '["普通画廊默认不展示 18+ 内容","分级由后台审核时确认","临时密钥默认 10 分钟有效"]',
       1, 1, 30, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_illustration_notice WHERE category = 'rule' AND sort_order = 30
);

UPDATE app_illustration_notice
SET type_label = '规则',
    title = '15+ 与 18+ 内容会分级管理',
    content = '站点会对 15+、18+ 内容做明确访问控制。18+ 内容不会默认展示，只有输入有效临时密钥后才可以查看。',
    points_json = '["普通画廊默认不展示 18+ 内容","分级由后台审核时确认","临时密钥默认 10 分钟有效"]',
    important = 1,
    enabled = 1
WHERE category = 'rule' AND sort_order = 30;

INSERT INTO app_illustration_notice
  (category, type_label, title, content, points_json, important, enabled, sort_order, created_at)
SELECT 'update', '更新', '用户投稿入口已开放前端页面',
       '上传作品页已支持填写标题、昵称、分类、标签和说明。提交后作品会进入后台审核列表。',
       '[]',
       0, 1, 40, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_illustration_notice WHERE category = 'update' AND sort_order = 40
);

UPDATE app_illustration_notice
SET type_label = '更新',
    title = '用户投稿入口已开放前端页面',
    content = '上传作品页已支持填写标题、昵称、分类、标签和说明。提交后作品会进入后台审核列表。',
    points_json = '[]',
    important = 0,
    enabled = 1
WHERE category = 'update' AND sort_order = 40;
