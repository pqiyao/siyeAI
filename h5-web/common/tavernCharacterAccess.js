const { getLanguageCode } = require('./tavernUiI18n.js');

function normalizePreviewBlurLevel(value) {
	var level = Number(value);
	if (!isFinite(level) || level <= 0) {
		return 0;
	}
	return level >= 2 ? 2 : 1;
}

function isPreviewBlurActive(card) {
	if (!card || typeof card !== 'object') {
		return false;
	}
	return !!card.preview_blur_active && normalizePreviewBlurLevel(card.preview_blur_vip_level) > 0;
}

function requiredVipTierLabel(card) {
	var level = normalizePreviewBlurLevel(card && card.preview_blur_vip_level);
	if (level >= 2) {
		return 'SVIP';
	}
	if (level >= 1) {
		return 'VIP';
	}
	return '';
}

function previewBlurBadgeText(card) {
	var code = getLanguageCode();
	var level = normalizePreviewBlurLevel(card && card.preview_blur_vip_level);
	if (level <= 0) {
		return '';
	}
	if (code === 'en') {
		return level >= 2 ? 'SVIP Clear' : 'VIP Clear';
	}
	if (code === 'ja') {
		return level >= 2 ? 'SVIPで鮮明' : 'VIPで鮮明';
	}
	if (code === 'ko') {
		return level >= 2 ? 'SVIP 해제 선명' : 'VIP 해제 선명';
	}
	return level >= 2 ? 'SVIP 清晰' : 'VIP 清晰';
}

function previewBlurHintText(card) {
	var code = getLanguageCode();
	var tier = requiredVipTierLabel(card);
	if (!tier) {
		return '';
	}
	if (code === 'en') {
		return tier + ' members can view this preview clearly';
	}
	if (code === 'ja') {
		return tier + '会員になると鮮明なプレビューを表示できます';
	}
	if (code === 'ko') {
		return tier + ' 회원이면 선명한 미리보기를 볼 수 있습니다';
	}
	return '开通 ' + tier + ' 后可查看清晰预览';
}

module.exports = {
	normalizePreviewBlurLevel: normalizePreviewBlurLevel,
	isPreviewBlurActive: isPreviewBlurActive,
	requiredVipTierLabel: requiredVipTierLabel,
	previewBlurBadgeText: previewBlurBadgeText,
	previewBlurHintText: previewBlurHintText
};
