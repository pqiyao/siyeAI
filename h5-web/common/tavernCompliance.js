var AGE_CONFIRM_KEY = 'tavern_age_confirmed_v1';
var OFFICIAL_QQ_GROUP = '1083699002';
var OFFICIAL_QQ_QR = '/static/official-qq-group.jpg';

function storageGet(key) {
	try {
		return uni.getStorageSync(key);
	} catch (e) {
		return '';
	}
}

function storageSet(key, value) {
	try {
		uni.setStorageSync(key, value);
	} catch (e) {}
}

function hasAgeConfirmed() {
	return storageGet(AGE_CONFIRM_KEY) === 'yes';
}

function markAgeConfirmed() {
	storageSet(AGE_CONFIRM_KEY, 'yes');
}

function currentRoutePath() {
	try {
		var pages = getCurrentPages();
		var last = pages && pages.length ? pages[pages.length - 1] : null;
		return last && last.route ? '/' + last.route : '';
	} catch (e) {
		return '';
	}
}

function ensureAgeConfirmed() {
	if (hasAgeConfirmed()) {
		return Promise.resolve(true);
	}
	var route = currentRoutePath();
	if (route === '/pages/user/ageGate/ageGate') {
		return Promise.resolve(false);
	}
	return new Promise(function (resolve) {
		uni.showModal({
			title: '年龄确认',
			content: '本项目为 AI 角色扮演纯娱乐分享项目，非商用，不支持充值或在线支付。内容由模型生成且可能包含虚构情节。请确认你已满 18 周岁，并同意遵守用户协议与隐私政策后继续使用。',
			confirmText: '已满18岁',
			cancelText: '不同意',
			success: function (res) {
				if (res && res.confirm) {
					markAgeConfirmed();
					resolve(true);
					return;
				}
				uni.reLaunch({
					url: '/pages/user/ageGate/ageGate',
					fail: function () {
						resolve(false);
					},
					success: function () {
						resolve(false);
					}
				});
			},
			fail: function () {
				resolve(false);
			}
		});
	});
}

function getOfficialContact() {
	return {
		qqGroup: OFFICIAL_QQ_GROUP,
		qrImage: OFFICIAL_QQ_QR
	};
}

function getTermsSections() {
	return [
		{
			title: '项目定位',
			body: '四叶酒馆为 AI 角色扮演纯娱乐分享项目，当前非商用，不支持充值、在线支付或付费开通。页面中的会员、余额、权益等内容仅作为体验和功能展示，不构成实际交易服务。'
		},
		{
			title: '服务性质',
			body: '所有角色、剧情和回复均可能由模型生成，仅供娱乐体验与创作交流，不代表真实人物、真实观点或专业建议。请勿将 AI 生成内容用于医疗、法律、金融、心理等专业决策。'
		},
		{
			title: '年龄与使用限制',
			body: '用户应确认已满 18 周岁后再继续使用。未成年人请在监护人指导下停止访问本项目，不得使用可能不适合未成年人的角色扮演内容。'
		},
		{
			title: '内容合规要求',
			body: '用户不得上传、生成、传播涉黄、暴力恐怖、政治敏感、违法犯罪、侵权、诈骗、骚扰、未成年人不适宜等违规内容。平台可根据运营与合规需要限制、隐藏或删除相关内容。'
		},
		{
			title: 'AI 生成内容提示',
			body: 'AI 回复存在不准确、不稳定或虚构的可能。用户应自行判断内容，不应将 AI 生成内容作为医疗、法律、金融、心理等专业建议使用。'
		},
		{
			title: '账号与数据',
			body: '用户应妥善保管账号信息。平台可能为了提供聊天、收藏、角色卡、反馈与安全风控功能而保存必要的账号、设备、聊天和角色数据。'
		},
		{
			title: '服务调整',
			body: '项目可能根据模型成本、运营策略与合规要求调整登录、注册、角色创建、支付说明、模型接入等功能。当前不支持充值和在线支付；如用户自愿资助，相关费用将优先用于补充模型 token 和维持体验服务。'
		},
		{
			title: '联系与反馈',
			body: '如需体验协助、问题反馈或内容处理，请联系官方 QQ 群：' + OFFICIAL_QQ_GROUP + '。'
		}
	];
}

function getPrivacySections() {
	return [
		{
			title: '我们收集的信息',
			body: '为了提供基础体验，可能会收集账号信息、匿名设备标识、登录状态、体验权益状态、角色卡、收藏、聊天会话、用户反馈、上传图片和必要的访问日志。本项目当前为非商用娱乐分享，不支持充值交易。'
		},
		{
			title: '信息使用目的',
			body: '相关信息主要用于账号登录、角色聊天、收藏同步、用户自建角色、问题反馈、消息通知、合规审核、异常排查和服务体验优化。'
		},
		{
			title: '上传与生成内容',
			body: '用户上传的角色卡、图片、文本和聊天内容可能会被用于当前账号的功能展示、聊天上下文、故障排查和违规处理。本项目不会将这些内容作为商业数据出售，也不会以充值付费方式售卖用户生成内容。'
		},
		{
			title: '数据保存与删除',
			body: '平台会在实现服务目的所需期限内保存数据。用户如需删除账号或处理个人数据，可通过官方 QQ 群联系作者协助处理。'
		},
		{
			title: '安全措施',
			body: '平台会尽量通过权限控制、接口校验、日志排查和必要的数据隔离来保护用户数据，但互联网服务无法保证绝对安全。'
		},
		{
			title: '未成年人保护',
			body: '本项目不面向未成年人提供服务。若发现未成年人使用，建议立即停止访问；如监护人需要协助处理相关数据，可联系官方 QQ 群。'
		},
		{
			title: '联系方式',
			body: '官方 QQ 群：' + OFFICIAL_QQ_GROUP + '。如有隐私、内容合规、账号或数据删除问题，可通过该渠道联系。'
		}
	];
}

module.exports = {
	AGE_CONFIRM_KEY: AGE_CONFIRM_KEY,
	OFFICIAL_QQ_GROUP: OFFICIAL_QQ_GROUP,
	OFFICIAL_QQ_QR: OFFICIAL_QQ_QR,
	hasAgeConfirmed: hasAgeConfirmed,
	markAgeConfirmed: markAgeConfirmed,
	ensureAgeConfirmed: ensureAgeConfirmed,
	getOfficialContact: getOfficialContact,
	getTermsSections: getTermsSections,
	getPrivacySections: getPrivacySections
};
