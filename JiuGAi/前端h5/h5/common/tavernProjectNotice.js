const NOTICE_COPY = {
	'zh-cn': {
		title: '充值入口暂时关闭',
		message: '当前暂不开放充值和会员购买入口，请稍后再试。已有订单的支付结果、到账权益和历史记录不会受此开关影响。',
		contactText: '联系客服',
		backText: '返回上一页'
	},
	'zh-hk': {
		title: '充值入口暫時關閉',
		message: '目前暫不開放充值與會員購買入口，請稍後再試。既有訂單的支付結果、到帳權益與歷史記錄不受此開關影響。',
		contactText: '聯絡客服',
		backText: '返回上一頁'
	},
	en: {
		title: 'Purchases Temporarily Unavailable',
		message: 'Top-ups and membership purchases are currently unavailable. Existing order results, credited benefits, and order history are not affected by this setting.',
		contactText: 'Contact Support',
		backText: 'Go Back'
	},
	ko: {
		title: '충전 기능이 일시 중지되었습니다',
		message: '현재 충전 및 멤버십 구매를 이용할 수 없습니다. 기존 주문의 결제 결과, 지급된 혜택 및 주문 내역은 이 설정의 영향을 받지 않습니다.',
		contactText: '고객 지원',
		backText: '뒤로'
	},
	ja: {
		title: 'チャージ機能は一時停止中です',
		message: '現在、チャージとメンバーシップ購入はご利用いただけません。既存注文の決済結果、付与済み特典、注文履歴はこの設定の影響を受けません。',
		contactText: 'サポートに連絡',
		backText: '戻る'
	}
};

function getProjectNoticeCopy(languageCode) {
	const code = String(languageCode || '').toLowerCase();
	return NOTICE_COPY[code] || NOTICE_COPY['zh-cn'];
}

module.exports = {
	getProjectNoticeCopy
};
