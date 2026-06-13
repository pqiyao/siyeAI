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

function containsAnyLower(text, keywords) {
	var value = String(text || '').toLowerCase();
	for (var i = 0; i < keywords.length; i += 1) {
		if (value.indexOf(String(keywords[i]).toLowerCase()) >= 0) {
			return true;
		}
	}
	return false;
}

function looksLikeInternalEngineMessage(message) {
	var text = String(message || '');
	var lower = text.toLowerCase();
	return (
		containsAnyLower(lower, [
			'sillytavern',
			'comfy',
			'comfyui',
			'workflow',
			'stable-diffusion',
			'stable diffusion',
			'sd webui',
			'openai-compatible',
			'openai compatible',
			'upstream',
			'bad gateway',
			'gateway timeout',
			'connection refused',
			'connectexception',
			'sockettimeoutexception',
			'restclient',
			'httpclient',
			'stacktrace',
			'traceback',
			'localhost',
			'127.0.0.1',
			'0.0.0.0',
			'8188',
			'7860'
		]) ||
		/\bST\b/.test(text) ||
		/\bHTTP\s*\d{3}\b/i.test(text) ||
		/\/api\/sd/i.test(text)
	);
}

function looksLikeImageGenerationMessage(message) {
	return containsAnyLower(message, [
		'生图',
		'图片生成',
		'image generation',
		'generate image',
		'text-to-image',
		'reference image',
		'prompt enhancement',
		'提示词增强',
		'提示词优化',
		'workflow',
		'comfy'
	]);
}

function sanitizeUserFacingMessage(message, fallback) {
	var text = pickMessage(message);
	var fb = pickMessage(fallback) || '请求失败，请稍后重试';
	if (!text) return fb;
	var lower = text.toLowerCase();

	if (containsAnyLower(lower, ['network error', 'request:fail', 'failed to fetch', 'networkerror'])) {
		return '网络连接异常，请检查网络后重试';
	}
	if (containsAnyLower(lower, ['timeout', 'timed out', '超时'])) {
		return '请求等待超时，请稍后重试';
	}
	if (containsAnyLower(lower, ['invalid api key', 'incorrect api key', 'unauthorized', '401']) || containsAny(text, ['API Key 无效', 'API Key不可用'])) {
		return 'API Key 不可用，请到 AI 设置里检查后重试';
	}
	if (containsAnyLower(lower, ['model not found', 'model does not exist', 'does not exist']) || containsAny(text, ['模型不存在', '模型不可用'])) {
		return '当前模型不可用，请到 AI 设置里重新选择模型';
	}
	if (containsAny(text, ['提示词增强失败', '提示词优化失败'])) {
		return '图片生成准备失败，请检查 AI 设置里的聊天模型，或稍后重试';
	}
	if (looksLikeInternalEngineMessage(text)) {
		if (looksLikeImageGenerationMessage(text)) {
			return '图片生成服务暂时不可用，请稍后重试';
		}
		return fb;
	}
	if (text.length > 160) {
		return fb;
	}
	return text;
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
		return '\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 28MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5';
	}
	if (typeof err === 'string') {
		var direct = pickMessage(err);
		if (direct) return sanitizeUserFacingMessage(direct, fb);
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
		if (message) return sanitizeUserFacingMessage(message, fb);
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
		return '\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a 28MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5';
	}
	var normalized = String(rawBody || '').toLowerCase();
	if (containsAny(normalized, ['illegal operation'])) {
		return '当前模型不支持这个语音能力，请到 AI 设置里分开填写 STT/TTS 模型';
	}
	if (containsAny(normalized, ['unsupported', 'not support']) && containsAny(normalized, ['audio', 'speech', 'voice', 'tts', 'transcribe'])) {
		return '当前模型不支持语音能力，请检查 STT/TTS 模型配置';
	}
	if (containsAny(normalized, ['model not found', 'does not exist']) && containsAny(normalized, ['audio', 'speech', 'voice', 'tts', 'transcribe', 'model'])) {
		return '当前语音模型不可用，请检查 AI 设置里的模型名称';
	}
	if (containsAny(normalized, ['invalid voice', 'voice not found'])) {
		return '当前音色不可用，请到 AI 设置里更换 TTS 音色';
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
	looksLikeMojibake: looksLikeMojibake,
	sanitizeUserFacingMessage: sanitizeUserFacingMessage
};
