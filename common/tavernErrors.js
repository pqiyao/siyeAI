function looksLikeMojibake(input) {
	if (input == null) return false;
	var text = String(input || '');
	if (!text) return false;
	var replacement = String.fromCharCode(0xfffd);
	return text.indexOf(replacement) >= 0 || /[\uE000-\uF8FF]/.test(text);
}

function pickMessage(candidate) {
	if (candidate == null) return '';
	var text = String(candidate).trim();
	if (!text || looksLikeMojibake(text)) return '';
	return text;
}

function containsAny(text, keywords) {
	var value = String(text || '');
	for (var i = 0; i < keywords.length; i += 1) {
		if (value.indexOf(keywords[i]) >= 0) {
			return true;
		}
	}
	return false;
}

function getTavernErrorMessage(err, fallback) {
	var fb = pickMessage(fallback) || '请求失败，请稍后重试';
	if (err == null) {
		return fb;
	}
	var statusCode = Number(
		err.statusCode
		|| err.status
		|| (err.response && err.response.status)
		|| 0
	);
	if (statusCode === 413) {
		return '\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 10MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5';
	}
	if (typeof err === 'string') {
		var direct = pickMessage(err);
		if (direct) return direct;
	}
	var candidates = [
		err.message,
		err.errMsg,
		err.data && err.data.message,
		err.data && err.data.msg,
		err.response && err.response.data && err.response.data.message,
		err.response && err.response.data && err.response.data.msg
	];
	for (var i = 0; i < candidates.length; i += 1) {
		var message = pickMessage(candidates[i]);
		if (message) return message;
	}
	var rawBody = err.response && err.response.data != null ? String(err.response.data) : '';
	if (
		containsAny(rawBody, [
			'request entity too large',
			'payload too large',
			'body exceeded',
			'maximum upload size exceeded'
		])
	) {
		return '\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 10MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5';
	}
	return fb;
}

function resolveCommercialPrompt(err) {
	var message = getTavernErrorMessage(err, '');
	if (!message) return null;
	if (
		containsAny(message, [
			'今日聊天次数已用完',
			'今日聊天额度已用完',
			'聊天次数已用完',
			'聊天额度已用完',
			'聊天额度不足'
		])
	) {
		return {
			kind: 'chat_quota',
			title: '今日聊天额度已用完',
			message: '免费版今日聊天额度已达上限。开通会员后可立即恢复更高聊天额度，并解锁续写、重生等权益。',
			primaryText: '开通会员',
			primaryUrl: '/pages/user/myvip',
			secondaryText: '去充值',
			secondaryUrl: '/pages/user/pay'
		};
	}
	if (
		containsAny(message, [
			'今日生图额度不足',
			'今日生图次数已用完',
			'生图额度不足',
			'生图次数已用完'
		])
	) {
		return {
			kind: 'image_quota',
			title: '今日生图额度不足',
			message: '当前账号的生图额度不足。开通会员或充值后即可继续使用生图功能。',
			primaryText: '开通会员',
			primaryUrl: '/pages/user/myvip',
			secondaryText: '去充值',
			secondaryUrl: '/pages/user/pay'
		};
	}
	if (
		containsAny(message, [
			'仅会员可用',
			'需要VIP',
			'需要会员',
			'会员可用',
			'升级会员',
			'会员专属'
		])
	) {
		return {
			kind: 'vip_only',
			title: '当前内容需要会员权限',
			message: '这个角色或功能仅会员可用。开通会员后即可继续聊天、续写、重生或访问会员角色。',
			primaryText: '开通会员',
			primaryUrl: '/pages/user/myvip',
			secondaryText: '去充值',
			secondaryUrl: '/pages/user/pay'
		};
	}
	return null;
}

function isLikelyNetworkError(err) {
	if (err == null) return false;
	var text = String((err.message || err.errMsg || '') + '').toLowerCase();
	return (
		text.indexOf('network') >= 0 ||
		text.indexOf('timeout') >= 0 ||
		text.indexOf('timed out') >= 0 ||
		text.indexOf('request:fail') >= 0 ||
		text.indexOf('failed to fetch') >= 0
	);
}

module.exports = {
	getTavernErrorMessage: getTavernErrorMessage,
	resolveCommercialPrompt: resolveCommercialPrompt,
	isLikelyNetworkError: isLikelyNetworkError,
	looksLikeMojibake: looksLikeMojibake
};
