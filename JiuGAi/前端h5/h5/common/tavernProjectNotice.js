const NOTICE_COPY = {
	'zh-cn': {
		title: '体验说明',
		message: '当前站点是 AI 角色扮演纯娱乐分享项目，非商用，不支持充值、在线支付或付费开通。想继续体验可以联系作者；如果愿意自愿资助也非常欢迎，所有资助都会全部用于补充模型 Token，尽量把大家的体验维持得更稳定。',
		contactText: '联系作者',
		backText: '返回上一页'
	},
	'zh-hk': {
		title: '體驗說明',
		message: '目前站點是 AI 角色扮演純娛樂分享項目，非商用，不支援充值、在線支付或付費開通。想繼續體驗可以聯絡作者；如果願意自願資助也非常歡迎，所有資助都會全部用於補充模型 Token，讓整體體驗更穩定。',
		contactText: '聯絡作者',
		backText: '返回上一頁'
	},
	en: {
		title: 'Experience Notice',
		message: 'This site is a non-commercial AI role-play sharing project for entertainment only. Top-ups, online payments, and paid activation are not supported. If you want to continue the experience, please contact the author. Voluntary sponsorship is welcome, and every contribution will be used for model token costs.',
		contactText: 'Contact Author',
		backText: 'Go Back'
	}
};

function getProjectNoticeCopy(languageCode) {
	const code = String(languageCode || '').toLowerCase();
	return NOTICE_COPY[code] || NOTICE_COPY['zh-cn'];
}

module.exports = {
	getProjectNoticeCopy
};
