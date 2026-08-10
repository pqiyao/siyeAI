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
			content: '本应用提供 AI 角色扮演与内容生成服务，模型输出可能包含虚构、不准确或不适宜内容。请确认你已满 18 周岁，并同意遵守用户协议与隐私政策后继续使用。',
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
			title: '生效与接受',
			body: '本协议自 2026 年 7 月 30 日起生效。注册、登录、访问或使用四叶酒馆，即表示用户已阅读、理解并同意本协议及隐私政策；不同意相关内容的，请立即停止使用。'
		},
		{
			title: '服务性质',
			body: '四叶酒馆提供 AI 角色扮演、角色创建、聊天、语音、图片及相关内容生成工具。模型输出具有随机性，可能不准确、不完整、虚构或令人不适，不代表平台观点，也不构成医疗、法律、金融、心理或其他专业建议。用户应独立判断并承担使用结果。'
		},
		{
			title: '年龄与使用限制',
			body: '本服务仅面向已满 18 周岁的用户。未成年人不得注册、登录或使用本服务；监护人发现未成年人使用时，可通过官方联系渠道申请停止服务并处理相关账号数据。'
		},
		{
			title: '严格禁止的内容与行为',
			body: '严禁利用本服务制作、上传、诱导生成、保存或传播任何违法违规内容，包括但不限于涉及未成年人色情或性化、淫秽色情、暴力恐怖、违法犯罪、毒品赌博、诈骗引流、仇恨歧视、骚扰威胁、自残教唆、侵犯隐私、侵害知识产权、冒充他人、恶意攻击系统以及法律法规禁止的其他内容。严禁绕过安全措施、批量滥用接口、盗用账号或将服务用于违法用途。'
		},
		{
			title: '用户内容与责任',
			body: '用户对其创建、上传、导入、编辑、生成、保存、使用或分享的角色卡、头像、图片、提示词、世界设定、聊天内容及其他内容负责，并应确保拥有必要权利且不侵犯任何第三方合法权益。平台不对用户内容作真实性、准确性、合法性或适用性背书。因用户内容或使用行为产生的投诉、损失、侵权或违法责任，由责任方依法承担。'
		},
		{
			title: '内容处置与账号措施',
			body: '为维护安全、合规和正常运营，平台有权依据法律法规、监管要求、用户举报或合理风险判断，对涉嫌违规内容采取中止生成、限制展示、删除、保存必要记录等措施，并可对相关账号采取警告、限制功能、冻结或终止服务。涉嫌违法犯罪的，平台可依法配合有关机关处理。'
		},
		{
			title: '账号安全',
			body: '用户应提供真实有效的必要信息并妥善保管账号、密码和验证码，不得出借、出租、出售或转让账号。因用户保管不当、主动泄露或违规共享账号造成的损失，由用户自行承担；发现异常登录或账号被盗时，应及时修改密码并联系处理。'
		},
		{
			title: '会员、虚拟权益与支付',
			body: '部分功能可能通过会员、套餐、钻石、金币、免费次数或其他权益提供。商品内容、价格、有效期、赠送额度和消耗规则以购买页面展示为准。用户应在付款前核对商品和支付信息；支付由第三方支付服务完成。除法律法规另有规定或服务存在可核实异常外，已经使用、消耗或即时到账的数字化权益不支持无理由退换。发现重复扣款、未到账等问题时，可凭订单信息联系处理。'
		},
		{
			title: '第三方服务',
			body: '模型 API、语音合成、图片生成、支付、对象存储或外部链接可能由独立第三方提供。第三方服务的可用性、价格、内容政策和数据处理规则由相应服务方负责。用户使用自备 API 或跳转第三方网站前，应自行阅读并遵守其协议和隐私规则。'
		},
		{
			title: '知识产权',
			body: '应用界面、程序、标识和平台自有内容受法律保护。未经许可，不得复制、反向利用、出售或以其他方式侵害相关权益。用户保留其依法享有的原创内容权利，并授权平台在提供存储、展示、生成、审核、备份和故障处理所必要的范围内处理相关内容。'
		},
		{
			title: '服务变更与可用性',
			body: '受模型供应、网络、维护、安全、成本或政策调整影响，部分功能可能变更、中断或停止。平台将尽合理努力保障服务，但不承诺服务永久、连续或完全无误；在法律允许范围内，对不可抗力、第三方故障或用户自身原因造成的影响，按实际责任依法处理。'
		},
		{
			title: '协议更新',
			body: '本协议可能因功能、规则或法律要求变化而更新。重要变更将通过应用页面、弹窗、公告或其他合理方式提示。更新生效后继续使用服务，视为接受更新内容；不同意的，可停止使用并申请注销账号。'
		},
		{
			title: '联系与反馈',
			body: '如需订单核查、账号处理、侵权投诉、内容举报或其他帮助，可通过官方 QQ 群联系：' + OFFICIAL_QQ_GROUP + '。提交投诉时请提供必要的账号、订单或内容定位信息，避免提供无关敏感信息。'
		}
	];
}

function getPrivacySections() {
	return [
		{
			title: '生效与适用范围',
			body: '本隐私政策自 2026 年 7 月 30 日起生效，适用于四叶酒馆提供的账号、角色、聊天、语音、图片、会员、支付及相关服务。使用服务即表示已阅读本政策；如不同意，请停止使用并可通过官方联系渠道申请处理账号数据。'
		},
		{
			title: '我们处理的信息',
			body: '为提供服务，可能处理账号资料、登录凭证状态、匿名设备标识、语言和基础设备信息、角色卡、收藏、聊天会话、提示词、上传图片、语音、反馈工单、会员与权益状态、订单金额、支付渠道、交易状态以及必要的访问和安全日志。我们不会要求或保存用户的银行卡密码、支付密码等完整支付凭证。'
		},
		{
			title: '信息使用目的',
			body: '相关信息用于注册登录、身份验证、角色与聊天功能、内容生成、语音和图片处理、权益计费、订单核对、通知反馈、数据同步、内容安全、反滥用、故障排查和服务优化。除实现功能、安全合规或法律要求所必需外，不将个人信息用于无关目的。'
		},
		{
			title: '设备权限',
			body: '仅在用户主动使用对应功能时申请必要权限：相机用于拍摄并上传图片，相册或文件权限用于选择头像、角色卡和聊天附件，麦克风用于录音、语音输入或自建音色，网络权限用于连接服务。拒绝非必要权限不影响其他基础功能；用户可在系统设置中随时关闭权限。'
		},
		{
			title: 'AI、语音与图片服务',
			body: '为完成用户主动发起的生成请求，必要的聊天上下文、提示词、角色设定、文本、语音或图片可能被传输给所选模型 API、语音合成或图片生成服务。使用自备 API 时，相关数据将直接发送至用户配置的服务地址。不同服务可能具有独立的数据保存和内容规则，用户应谨慎提交敏感信息。'
		},
		{
			title: '支付与交易信息',
			body: '支付由第三方支付服务处理。为创建订单、确认到账、发放权益、退款核查和防止欺诈，可能保存订单号、商品、金额、支付渠道、创建时间、支付状态和第三方交易标识。完整支付账号、银行卡信息和支付密码由支付服务方依其规则处理。'
		},
		{
			title: '第三方服务与外部链接',
			body: '服务可能使用模型 API、支付、对象存储、内容分发、语音或图片处理等第三方能力，也可能包含外部网站链接。仅在实现用户所选功能所需范围内传输必要数据。第三方独立处理的信息受其自身协议约束，离开本应用后请查看对应隐私规则。'
		},
		{
			title: '内容安全与必要记录',
			body: '为识别违法违规内容、处理举报、保障账号和系统安全，可能对公开内容、用户提交内容和必要日志进行自动或人工检查，并在合理范围内保留处置记录。请勿在角色卡、聊天、图片、语音或反馈中提交身份证件、支付密码等无关敏感信息。'
		},
		{
			title: '数据保存与删除',
			body: '数据在提供服务、保障安全、处理订单争议及履行法律义务所需的最短期限内保存。用户可通过账号与安全页面注销账号，或通过官方 QQ 群申请查询、更正、删除相关数据。完成身份核验后，将在合理期限内处理；法律要求保留的交易、安全或争议记录会在期限届满后删除或匿名化。'
		},
		{
			title: '安全措施',
			body: '通过访问控制、身份校验、传输保护、接口权限、必要隔离、备份和安全日志等措施保护数据。互联网服务无法保证绝对安全；如发现账号或数据异常，请及时修改密码并通过官方联系渠道反馈。'
		},
		{
			title: '未成年人保护',
			body: '本服务不面向未成年人。若发现未成年人注册或使用，应立即停止访问；监护人可通过官方 QQ 群申请核实并处理相关账号和数据。'
		},
		{
			title: '政策更新',
			body: '因功能、第三方服务或法律要求变化，本政策可能更新。重要变更将通过应用页面、弹窗、公告或其他合理方式提示，并标明新的生效日期。不同意更新内容的，可停止使用并申请注销账号。'
		},
		{
			title: '联系方式',
			body: '官方 QQ 群：' + OFFICIAL_QQ_GROUP + '。如需行使个人信息相关权利，或反馈隐私、账号、订单、侵权和内容合规问题，可通过该渠道联系。为保护账号安全，处理请求前可能需要进行必要的身份核验。'
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
