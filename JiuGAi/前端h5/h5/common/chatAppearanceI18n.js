const COPY = {
	'zh-cn': {
		title: '聊天显示与气泡', livePreview: '实时效果', previewUser: '我也想再待一会儿。', bubbleAppearance: '气泡外观', followDefault: '跟随默认', customAppearance: '自定义外观',
		presetTitle: '主题预设', shapeText: '形状与文字', advancedVisual: '高级视觉', typographyStyle: '字体风格', surfaceStyle: '表面效果', contentToneStyle: '内嵌内容色调', thoughtItalic: '心理文字使用斜体', bubbleBorder: '气泡与边框', textColor: '文字颜色', resetTextAria: '恢复预设文字颜色',
		roleplayReading: '角色扮演阅读', segmentLabels: '段落标签', aiReplyFormat: 'AI 回复格式', restoreDefault: '恢复默认', saveApply: '保存并应用',
		dirty: '当前修改尚未应用到聊天页', applied: '当前外观已应用到聊天页', customAdjusted: '自定义调整', textCustomized: '字色自定义', followTheme: '跟随当前主题预设',
		customCount: '当前主题已单独调整 {count} 项，切换预设会恢复主题配色', defaultName: '默认', textReset: '文字颜色已跟随预设', resetDone: '已恢复默认', resetFailed: '恢复失败，请重试', saved: '已保存', saveFailed: '保存失败，请重试',
		globalScope: '全局配置', characterScope: '当前角色', inheritGlobal: '跟随全局', characterSpecific: '角色专属', resetPrepared: '已恢复默认，保存后生效',
		syncPending: '本地修改等待同步', syncOfflineSaved: '已离线保存，联网后将自动同步', syncOffline: '云同步暂不可用，当前显示本地配置', syncConflict: '云端与本地配置有冲突，本地修改已保留', syncRetry: '重试同步', resolveSync: '处理冲突', keepLocal: '保留本地并覆盖云端', useCloud: '采用云端配置', syncResolved: '配置冲突已处理',
		previewSource: '街灯落在石板路上，晚风很安静。\n\n“我想多陪你走一会儿。”\n\n（她把外套往肩上拢了拢，脚步慢下来。）\n\n（心里想着：也许这句话已经藏了很久。）', previewVoice: '播放语音', previewStatusTitle: '关系状态', previewStatusBody: '信任 72% · 剧情阶段：重逢',
		previewScenes: { text: '文字', media: '媒体', status: '状态' }, typographyProfiles: { light: '自然', balanced: '标准', emphasis: '强调', custom: '自定义' }, surfaceModes: { flat: '平面', softGradient: '柔光', legacyGlass: '玻璃' }, contentTones: { auto: '自动', light: '浅色', dark: '深色' },
		presets: { classic: ['原版基准', '冻结升级前的原聊天页外观。'], fengyue: ['风月轻语', '清晰高对比文字与轻透磨砂底框。'], night: ['深夜玻璃', '低饱和深色玻璃，边缘更柔和。'], soft: ['晨雾浅色', '浅色清晰气泡。'], novel: ['沉浸阅读', '宽版长文气泡。'], contrast: ['极致清晰', '高对比实色气泡。'], clear: ['轻透氛围', '低浓度透明气泡。'] },
		readModes: { original: '原文聊天', novel: '小说阅读', speechOnly: '只看台词', hideThought: '隐藏心理', softAction: '弱化动作' }, splitModes: { none: '单气泡', bubble: '分段气泡' },
		numberLabels: { fontSize: '字体大小', lineHeight: '行间距', radius: '气泡圆角', opacity: '气泡浓度', charMaxWidth: '角色气泡宽度', userMaxWidth: '我的气泡宽度', bubblePaddingY: '上下留白', bubblePaddingX: '左右留白', imagePadding: '图片气泡留白', backdropStrength: '背景遮罩', baseFontWeight: '默认字重', userFontWeight: '我的消息字重', speechFontWeight: '台词字重', actionFontWeight: '动作字重', thoughtFontWeight: '心理字重', narrationFontWeight: '旁白字重', surfaceBorderOpacity: '表面边框', sideBorderWidth: '侧边线宽度', sideBorderOpacity: '侧边线强度', shadowStrength: '阴影强度', blurRadius: '背景模糊' },
		colorLabels: { charBubbleColor: '角色气泡', userBubbleColor: '我的气泡', charBorderColor: '角色边框', userBorderColor: '我的边框', baseTextColor: '默认正文', userTextColor: '我的消息', speechColor: '台词', actionColor: '动作', thoughtColor: '心理', narrationColor: '旁白' },
		segmentTypes: { speech: '台词', action: '动作', thought: '心理', narration: '旁白' }
	},
	'zh-hk': {
		title: '聊天顯示與氣泡', livePreview: '即時效果', previewUser: '我也想再待一會兒。', bubbleAppearance: '氣泡外觀', followDefault: '跟隨預設', customAppearance: '自訂外觀',
		presetTitle: '主題預設', shapeText: '形狀與文字', advancedVisual: '進階視覺', typographyStyle: '字體風格', surfaceStyle: '表面效果', contentToneStyle: '內嵌內容色調', thoughtItalic: '心理文字使用斜體', bubbleBorder: '氣泡與邊框', textColor: '文字顏色', resetTextAria: '恢復預設文字顏色',
		roleplayReading: '角色扮演閱讀', segmentLabels: '段落標籤', aiReplyFormat: 'AI 回覆格式', restoreDefault: '恢復預設', saveApply: '儲存並套用',
		dirty: '目前修改尚未套用到聊天頁', applied: '目前外觀已套用到聊天頁', customAdjusted: '自訂調整', textCustomized: '字色自訂', followTheme: '跟隨目前主題預設',
		customCount: '目前主題已個別調整 {count} 項，切換預設會恢復主題配色', defaultName: '預設', textReset: '文字顏色已跟隨預設', resetDone: '已恢復預設', resetFailed: '恢復失敗，請重試', saved: '已儲存', saveFailed: '儲存失敗，請重試',
		globalScope: '全域設定', characterScope: '目前角色', inheritGlobal: '跟隨全域', characterSpecific: '角色專屬', resetPrepared: '已恢復預設，儲存後生效',
		syncPending: '本機修改正等待同步', syncOfflineSaved: '已離線儲存，連線後會自動同步', syncOffline: '雲端同步暫時無法使用，目前顯示本機設定', syncConflict: '雲端與本機設定有衝突，本機修改已保留', syncRetry: '重試同步', resolveSync: '處理衝突', keepLocal: '保留本機並覆蓋雲端', useCloud: '採用雲端設定', syncResolved: '設定衝突已處理',
		previewSource: '街燈落在石板路上，晚風很安靜。\n\n「我想多陪你走一會兒。」\n\n（她把外套往肩上攏了攏，腳步慢下來。）\n\n（心裡想着：也許這句話已經藏了很久。）', previewVoice: '播放語音', previewStatusTitle: '關係狀態', previewStatusBody: '信任 72% · 劇情階段：重逢',
		previewScenes: { text: '文字', media: '媒體', status: '狀態' }, typographyProfiles: { light: '自然', balanced: '標準', emphasis: '強調', custom: '自訂' }, surfaceModes: { flat: '平面', softGradient: '柔光', legacyGlass: '玻璃' }, contentTones: { auto: '自動', light: '淺色', dark: '深色' },
		presets: { classic: ['原版基準', '保留升級前的原聊天頁外觀。'], fengyue: ['風月輕語', '清晰高對比文字與輕透磨砂底框。'], night: ['深夜玻璃', '低飽和深色玻璃，邊緣更柔和。'], soft: ['晨霧淺色', '淺色清晰氣泡。'], novel: ['沉浸閱讀', '寬版長文氣泡。'], contrast: ['極致清晰', '高對比實色氣泡。'], clear: ['輕透氛圍', '低濃度透明氣泡。'] },
		readModes: { original: '原文聊天', novel: '小說閱讀', speechOnly: '只看台詞', hideThought: '隱藏心理', softAction: '弱化動作' }, splitModes: { none: '單氣泡', bubble: '分段氣泡' },
		numberLabels: { fontSize: '字體大小', lineHeight: '行距', radius: '氣泡圓角', opacity: '氣泡濃度', charMaxWidth: '角色氣泡寬度', userMaxWidth: '我的氣泡寬度', bubblePaddingY: '上下留白', bubblePaddingX: '左右留白', imagePadding: '圖片氣泡留白', backdropStrength: '背景遮罩', baseFontWeight: '預設字重', userFontWeight: '我的訊息字重', speechFontWeight: '台詞字重', actionFontWeight: '動作字重', thoughtFontWeight: '心理字重', narrationFontWeight: '旁白字重', surfaceBorderOpacity: '表面邊框', sideBorderWidth: '側邊線寬度', sideBorderOpacity: '側邊線強度', shadowStrength: '陰影強度', blurRadius: '背景模糊' },
		colorLabels: { charBubbleColor: '角色氣泡', userBubbleColor: '我的氣泡', charBorderColor: '角色邊框', userBorderColor: '我的邊框', baseTextColor: '預設正文', userTextColor: '我的訊息', speechColor: '台詞', actionColor: '動作', thoughtColor: '心理', narrationColor: '旁白' },
		segmentTypes: { speech: '台詞', action: '動作', thought: '心理', narration: '旁白' }
	},
	en: {
		title: 'Chat Display & Bubbles', livePreview: 'Live Preview', previewUser: 'I would like to stay a little longer too.', bubbleAppearance: 'Bubble Appearance', followDefault: 'Use Default', customAppearance: 'Custom Appearance',
		presetTitle: 'Theme Presets', shapeText: 'Shape & Text', advancedVisual: 'Advanced Visuals', typographyStyle: 'Typography', surfaceStyle: 'Surface', contentToneStyle: 'Embedded Content Tone', thoughtItalic: 'Italicize thoughts', bubbleBorder: 'Bubbles & Borders', textColor: 'Text Colors', resetTextAria: 'Restore preset text colors',
		roleplayReading: 'Roleplay Reading', segmentLabels: 'Segment Labels', aiReplyFormat: 'AI Reply Format', restoreDefault: 'Restore Default', saveApply: 'Save & Apply',
		dirty: 'Changes have not been applied to chat', applied: 'Appearance is applied to chat', customAdjusted: 'Custom', textCustomized: 'custom text colors', followTheme: 'Following the current theme preset',
		customCount: '{count} text colors are customized. Switching presets restores theme colors.', defaultName: 'Default', textReset: 'Text colors now follow the preset', resetDone: 'Defaults restored', resetFailed: 'Could not restore defaults', saved: 'Saved', saveFailed: 'Could not save. Please try again.',
		globalScope: 'Global', characterScope: 'Current Character', inheritGlobal: 'Use Global', characterSpecific: 'Character Specific', resetPrepared: 'Defaults restored. Save to apply.',
		syncPending: 'Local changes are waiting to sync', syncOfflineSaved: 'Saved offline and will sync when connected', syncOffline: 'Cloud sync is unavailable. Local settings are shown.', syncConflict: 'Cloud and local settings conflict. Local changes are preserved.', syncRetry: 'Retry Sync', resolveSync: 'Resolve', keepLocal: 'Keep Local and Replace Cloud', useCloud: 'Use Cloud Settings', syncResolved: 'Settings conflict resolved',
		previewSource: 'Streetlights fall across the stone road. The evening breeze is quiet.\n\n“I would like to walk with you a little longer.”\n\n(She pulls her coat closer and slows her steps.)\n\n(She thinks: perhaps these words have been hidden for a long time.)', previewVoice: 'Play voice', previewStatusTitle: 'Relationship', previewStatusBody: 'Trust 72% · Story stage: Reunion',
		previewScenes: { text: 'Text', media: 'Media', status: 'Status' }, typographyProfiles: { light: 'Natural', balanced: 'Balanced', emphasis: 'Emphasis', custom: 'Custom' }, surfaceModes: { flat: 'Flat', softGradient: 'Soft', legacyGlass: 'Glass' }, contentTones: { auto: 'Auto', light: 'Light', dark: 'Dark' },
		presets: { classic: ['Original', 'Keeps the chat appearance from before this upgrade.'], fengyue: ['Fengyue Whisper', 'Crisp high-contrast type over a light translucent frame.'], night: ['Night Glass', 'Soft, low-saturation dark glass.'], soft: ['Morning Mist', 'Clear, light-colored bubbles.'], novel: ['Immersive Reading', 'Wider bubbles for long-form reading.'], contrast: ['Maximum Clarity', 'Solid, high-contrast bubbles.'], clear: ['Light Atmosphere', 'Low-density transparent bubbles.'] },
		readModes: { original: 'Original Chat', novel: 'Novel Reading', speechOnly: 'Dialogue Only', hideThought: 'Hide Thoughts', softAction: 'Soften Actions' }, splitModes: { none: 'Single Bubble', bubble: 'Segmented Bubbles' },
		numberLabels: { fontSize: 'Font Size', lineHeight: 'Line Spacing', radius: 'Bubble Radius', opacity: 'Bubble Opacity', charMaxWidth: 'Character Bubble Width', userMaxWidth: 'My Bubble Width', bubblePaddingY: 'Vertical Padding', bubblePaddingX: 'Horizontal Padding', imagePadding: 'Image Bubble Padding', backdropStrength: 'Backdrop Shade', baseFontWeight: 'Body Weight', userFontWeight: 'My Message Weight', speechFontWeight: 'Dialogue Weight', actionFontWeight: 'Action Weight', thoughtFontWeight: 'Thought Weight', narrationFontWeight: 'Narration Weight', surfaceBorderOpacity: 'Surface Border', sideBorderWidth: 'Accent Width', sideBorderOpacity: 'Accent Strength', shadowStrength: 'Shadow Strength', blurRadius: 'Backdrop Blur' },
		colorLabels: { charBubbleColor: 'Character Bubble', userBubbleColor: 'My Bubble', charBorderColor: 'Character Border', userBorderColor: 'My Border', baseTextColor: 'Body Text', userTextColor: 'My Message', speechColor: 'Dialogue', actionColor: 'Action', thoughtColor: 'Thought', narrationColor: 'Narration' },
		segmentTypes: { speech: 'Dialogue', action: 'Action', thought: 'Thought', narration: 'Narration' }
	},
	ko: {
		title: '채팅 표시 및 말풍선', livePreview: '실시간 미리보기', previewUser: '저도 조금 더 머물고 싶어요.', bubbleAppearance: '말풍선 모양', followDefault: '기본값 사용', customAppearance: '사용자 지정',
		presetTitle: '테마 프리셋', shapeText: '모양 및 텍스트', advancedVisual: '고급 시각 설정', typographyStyle: '글자 스타일', surfaceStyle: '표면 효과', contentToneStyle: '내장 콘텐츠 톤', thoughtItalic: '생각을 기울임꼴로 표시', bubbleBorder: '말풍선 및 테두리', textColor: '텍스트 색상', resetTextAria: '프리셋 텍스트 색상 복원',
		roleplayReading: '롤플레이 읽기', segmentLabels: '문단 라벨', aiReplyFormat: 'AI 답변 형식', restoreDefault: '기본값 복원', saveApply: '저장 및 적용',
		dirty: '변경 사항이 채팅에 아직 적용되지 않았습니다', applied: '현재 모양이 채팅에 적용되었습니다', customAdjusted: '사용자 지정', textCustomized: '텍스트 색상 사용자 지정', followTheme: '현재 테마 프리셋 따르기',
		customCount: '텍스트 색상 {count}개를 별도로 조정했습니다. 프리셋을 바꾸면 테마 색상이 복원됩니다.', defaultName: '기본값', textReset: '텍스트 색상이 프리셋을 따릅니다', resetDone: '기본값을 복원했습니다', resetFailed: '복원하지 못했습니다. 다시 시도하세요.', saved: '저장했습니다', saveFailed: '저장하지 못했습니다. 다시 시도하세요.',
		globalScope: '전체 설정', characterScope: '현재 캐릭터', inheritGlobal: '전체 설정 사용', characterSpecific: '캐릭터 전용', resetPrepared: '기본값을 복원했습니다. 저장하면 적용됩니다.',
		syncPending: '로컬 변경 사항이 동기화를 기다리고 있습니다', syncOfflineSaved: '오프라인으로 저장했으며 연결되면 동기화됩니다', syncOffline: '클라우드 동기화를 사용할 수 없어 로컬 설정을 표시합니다', syncConflict: '클라우드와 로컬 설정이 충돌합니다. 로컬 변경 사항은 보존되었습니다.', syncRetry: '동기화 재시도', resolveSync: '충돌 처리', keepLocal: '로컬 유지 및 클라우드 덮어쓰기', useCloud: '클라우드 설정 사용', syncResolved: '설정 충돌을 처리했습니다',
		previewSource: '가로등이 돌길을 비추고 저녁바람은 고요하다.\n\n“조금 더 함께 걷고 싶어요.”\n\n(그녀는 코트를 여미고 천천히 걷는다.)\n\n(마음속으로 생각한다. 어쩌면 이 말은 오래 숨겨 두었을지도 모른다.)', previewVoice: '음성 재생', previewStatusTitle: '관계 상태', previewStatusBody: '신뢰 72% · 이야기 단계: 재회',
		previewScenes: { text: '텍스트', media: '미디어', status: '상태' }, typographyProfiles: { light: '자연스러움', balanced: '표준', emphasis: '강조', custom: '사용자 지정' }, surfaceModes: { flat: '평면', softGradient: '부드러움', legacyGlass: '유리' }, contentTones: { auto: '자동', light: '밝게', dark: '어둡게' },
		presets: { classic: ['원본 기준', '업그레이드 전 채팅 모양을 유지합니다.'], fengyue: ['풍월 속삭임', '선명한 고대비 글자와 가벼운 반투명 프레임입니다.'], night: ['밤의 유리', '부드럽고 채도가 낮은 어두운 유리 효과입니다.'], soft: ['아침 안개', '밝고 선명한 말풍선입니다.'], novel: ['몰입형 읽기', '긴 글에 맞는 넓은 말풍선입니다.'], contrast: ['최고 선명도', '대비가 높은 단색 말풍선입니다.'], clear: ['가벼운 분위기', '농도가 낮은 투명 말풍선입니다.'] },
		readModes: { original: '원문 채팅', novel: '소설 읽기', speechOnly: '대사만', hideThought: '생각 숨기기', softAction: '동작 약화' }, splitModes: { none: '단일 말풍선', bubble: '문단별 말풍선' },
		numberLabels: { fontSize: '글자 크기', lineHeight: '줄 간격', radius: '말풍선 모서리', opacity: '말풍선 농도', charMaxWidth: '캐릭터 말풍선 너비', userMaxWidth: '내 말풍선 너비', bubblePaddingY: '세로 여백', bubblePaddingX: '가로 여백', imagePadding: '이미지 말풍선 여백', backdropStrength: '배경 음영', baseFontWeight: '본문 굵기', userFontWeight: '내 메시지 굵기', speechFontWeight: '대사 굵기', actionFontWeight: '동작 굵기', thoughtFontWeight: '생각 굵기', narrationFontWeight: '서술 굵기', surfaceBorderOpacity: '표면 테두리', sideBorderWidth: '강조선 너비', sideBorderOpacity: '강조선 강도', shadowStrength: '그림자 강도', blurRadius: '배경 흐림' },
		colorLabels: { charBubbleColor: '캐릭터 말풍선', userBubbleColor: '내 말풍선', charBorderColor: '캐릭터 테두리', userBorderColor: '내 테두리', baseTextColor: '본문', userTextColor: '내 메시지', speechColor: '대사', actionColor: '동작', thoughtColor: '생각', narrationColor: '서술' },
		segmentTypes: { speech: '대사', action: '동작', thought: '생각', narration: '서술' }
	},
	ja: {
		title: 'チャット表示と吹き出し', livePreview: 'ライブプレビュー', previewUser: '私ももう少しここにいたいです。', bubbleAppearance: '吹き出しの外観', followDefault: '標準を使用', customAppearance: 'カスタム外観',
		presetTitle: 'テーマプリセット', shapeText: '形と文字', advancedVisual: '高度な表示', typographyStyle: '文字スタイル', surfaceStyle: '表面効果', contentToneStyle: '埋め込み内容の色調', thoughtItalic: '心理文を斜体にする', bubbleBorder: '吹き出しと枠線', textColor: '文字色', resetTextAria: 'プリセットの文字色に戻す',
		roleplayReading: 'ロールプレイ表示', segmentLabels: '段落ラベル', aiReplyFormat: 'AI返信形式', restoreDefault: '標準に戻す', saveApply: '保存して適用',
		dirty: '変更はまだチャットに適用されていません', applied: '現在の外観がチャットに適用されています', customAdjusted: 'カスタム調整', textCustomized: '文字色をカスタム', followTheme: '現在のテーマプリセットに従う',
		customCount: '文字色を{count}項目調整しています。プリセットを変えるとテーマ色に戻ります。', defaultName: '標準', textReset: '文字色をプリセットに戻しました', resetDone: '標準に戻しました', resetFailed: '復元できませんでした。もう一度お試しください。', saved: '保存しました', saveFailed: '保存できませんでした。もう一度お試しください。',
		globalScope: '全体設定', characterScope: '現在のキャラクター', inheritGlobal: '全体設定を使用', characterSpecific: 'キャラクター専用', resetPrepared: '標準に戻しました。保存すると適用されます。',
		syncPending: 'ローカル変更は同期待ちです', syncOfflineSaved: 'オフラインで保存しました。接続後に同期します', syncOffline: 'クラウド同期を利用できないため、ローカル設定を表示しています', syncConflict: 'クラウドとローカルの設定が競合しています。ローカル変更は保持されています。', syncRetry: '同期を再試行', resolveSync: '競合を解決', keepLocal: 'ローカルを保持してクラウドを上書き', useCloud: 'クラウド設定を使用', syncResolved: '設定の競合を解決しました',
		previewSource: '街灯が石畳を照らし、夜風は静かだ。\n\n「もう少し一緒に歩きたい。」\n\n（彼女はコートを寄せ、歩みをゆるめる。）\n\n（心の中で思う。この言葉はずっと隠していたのかもしれない。）', previewVoice: '音声を再生', previewStatusTitle: '関係ステータス', previewStatusBody: '信頼 72% · 物語段階：再会',
		previewScenes: { text: '文字', media: 'メディア', status: '状態' }, typographyProfiles: { light: '自然', balanced: '標準', emphasis: '強調', custom: 'カスタム' }, surfaceModes: { flat: 'フラット', softGradient: 'ソフト', legacyGlass: 'ガラス' }, contentTones: { auto: '自動', light: '明るい', dark: '暗い' },
		presets: { classic: ['オリジナル基準', 'アップグレード前のチャット表示を保持します。'], fengyue: ['風月のささやき', '明瞭な高コントラスト文字と軽い半透明フレームです。'], night: ['夜のガラス', '彩度を抑えた柔らかな暗色ガラスです。'], soft: ['朝霧ライト', '明るく見やすい吹き出しです。'], novel: ['没入読書', '長文向けの幅広い吹き出しです。'], contrast: ['最高の明瞭さ', '高コントラストの単色吹き出しです。'], clear: ['軽い雰囲気', '濃度を抑えた透明な吹き出しです。'] },
		readModes: { original: '原文チャット', novel: '小説表示', speechOnly: '台詞のみ', hideThought: '心理を隠す', softAction: '動作を弱める' }, splitModes: { none: '単一吹き出し', bubble: '段落別吹き出し' },
		numberLabels: { fontSize: '文字サイズ', lineHeight: '行間', radius: '角の丸み', opacity: '吹き出し濃度', charMaxWidth: 'キャラクター吹き出し幅', userMaxWidth: '自分の吹き出し幅', bubblePaddingY: '上下余白', bubblePaddingX: '左右余白', imagePadding: '画像吹き出し余白', backdropStrength: '背景シェード', baseFontWeight: '本文の太さ', userFontWeight: '自分の文の太さ', speechFontWeight: '台詞の太さ', actionFontWeight: '動作の太さ', thoughtFontWeight: '心理の太さ', narrationFontWeight: 'ナレーションの太さ', surfaceBorderOpacity: '表面の枠線', sideBorderWidth: '強調線の幅', sideBorderOpacity: '強調線の強さ', shadowStrength: '影の強さ', blurRadius: '背景ぼかし' },
		colorLabels: { charBubbleColor: 'キャラクター吹き出し', userBubbleColor: '自分の吹き出し', charBorderColor: 'キャラクター枠線', userBorderColor: '自分の枠線', baseTextColor: '本文', userTextColor: '自分のメッセージ', speechColor: '台詞', actionColor: '動作', thoughtColor: '心理', narrationColor: 'ナレーション' },
		segmentTypes: { speech: '台詞', action: '動作', thought: '心理', narration: 'ナレーション' }
	}
};

function getAppearanceCopy(languageCode) {
	return COPY[String(languageCode || '').toLowerCase()] || COPY['zh-cn'];
}

module.exports = { getAppearanceCopy };
