// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 2  544，营业执照号： 914 40    60 5 M   A  C 0 Y 3J70 Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
import math from './math.js'

function QRPolynomial(num, shift) {

	if (num.length == undefined) {
		throw new Error(num.length + "/" + shift);
	}

	var offset = 0;

	while (offset < num.length && num[offset] == 0) {
		offset++;
	}

	this.num = new Array(num.length - offset + shift);
	for (var i = 0; i < num.length - offset; i++) {
		this.num[i] = num[i + offset];
	}
}

QRPolynomial.prototype = {

	get : function(index) {
		return this.num[index];
	},

	getLength : function() {
		return this.num.length;
	},

	multiply : function(e) {

		var num = new Array(this.getLength() + e.getLength() - 1);

		for (var i = 0; i < this.getLength(); i++) {
			for (var j = 0; j < e.getLength(); j++) {
				num[i + j] ^= math.gexp(math.glog(this.get(i) ) + math.glog(e.get(j) ) );
			}
		}

		return new QRPolynomial(num, 0);
	},

	mod : function(e) {

		if (this.getLength() - e.getLength() < 0) {
			return this;
		}

		var ratio = math.glog(this.get(0) ) - math.glog(e.get(0) );

		var num = new Array(this.getLength() );

		for (var i = 0; i < this.getLength(); i++) {
			num[i] = this.get(i);
		}

		for (var i = 0; i < e.getLength(); i++) {
			num[i] ^= math.gexp(math.glog(e.get(i) ) + ratio);
		}

		// recursive call
		return new QRPolynomial(num, 0).mod(e);
	}
};

export default QRPolynomial;