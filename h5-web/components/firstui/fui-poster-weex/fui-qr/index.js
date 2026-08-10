// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID：2 54  4，营业执照号：  9 1 4406 0   5M AC  0   Y3J   70Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
import QRCode from './lib/QRCode.js'
import ErrorCorrectLevel from './lib/ErrorCorrectLevel.js'

var qrcode = function(data, opt) {
	opt = opt || {};
	var qr = new QRCode(opt.typeNumber || -1,
						opt.errorCorrectLevel || ErrorCorrectLevel.H);
	qr.addData(data);
	qr.make();

	return qr;
};

qrcode.ErrorCorrectLevel = ErrorCorrectLevel;

export default qrcode;
