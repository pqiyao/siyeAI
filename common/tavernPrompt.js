/**
 * 与后端提示词构建逻辑一致，供直连 /api/v1/chat/completions 时使用。
 * 角色字段与 CharacterCardDto（JSON snake_case）对齐。
 */
function safe(s) {
	if (s == null || String(s).trim() === '') {
		return '（未设定）';
	}
	return String(s).trim();
}

function buildTavernSystemPrompt(char) {
	if (!char) {
		return 'You are a helpful assistant.';
	}
	var custom = char.system_prompt;
	if (custom != null && String(custom).trim() !== '') {
		var sb = String(custom).trim();
		var post = char.post_history_instructions;
		if (post != null && String(post).trim() !== '') {
			sb += '\n\n' + String(post).trim();
		}
		return sb;
	}
	var name = char.nickname || char.name || '';
	var parts = [];
	parts.push('你正在扮演角色「' + safe(name) + '」。');
	parts.push('人设：' + safe(char.persona));
	parts.push('当前场景：' + safe(char.scenario));
	var fm = char.first_message;
	if (fm != null && String(fm).trim() !== '') {
		parts.push('开场白风格参考（不要无故逐字重复）：' + String(fm).trim());
	}
	var ex = char.mes_example;
	if (ex != null && String(ex).trim() !== '') {
		parts.push('\n示例对话（语气与格式参考）：\n' + String(ex).trim());
	}
	parts.push('\n始终用角色视角回复，语言自然，默认使用中文。');
	return parts.join('\n');
}

module.exports = {
	buildTavernSystemPrompt: buildTavernSystemPrompt
};
