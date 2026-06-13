/**
 * 项目前端栈（h5 目录）：uni-app · Vue2 · uView1 · Vuex · colorui/firstui 部分组件
 * 业务文案：common/text/*.json
 */
var app = getApp();
var api = require('./api.js');
// Keys that should persist even when clearing auth-related storage
const PERSISTENT_KEYS = ['languageType', 'userManuallySelectedLanguage'];

function clearStoragePreserve(keys = PERSISTENT_KEYS) {
  try {
    const backup = {};
    keys.forEach(k => {
      backup[k] = uni.getStorageSync(k);
    });
    uni.clearStorageSync();
    Object.keys(backup).forEach(k => {
      const v = backup[k];
      if (v !== undefined && v !== null && v !== '') {
        uni.setStorageSync(k, v);
      }
    });
  } catch (e) {
    // Fallback: if anything goes wrong, at least avoid crashing
    try { uni.clearStorageSync(); } catch(_) {}
  }
}


/**
 * request 普通请求
 */

/**
 * request 普通请求
 */
function request(url, data = {}, dataType = '', method = "POST") {
	return new Promise(function(resolve, reject) {
		let languageList = ["zh-hk", "zh-cn","en","ko","ja"];
		
		let languageType = languageList[1];
		const langIdx = uni.getStorageSync('languageType');
		const langCodeMap = { 'zh-hk': 0, 'zh-cn': 1, en: 2, ko: 3, ja: 4 };
		if (langIdx !== undefined && langIdx !== null && langIdx !== '') {
			if (typeof langIdx === 'string' && langCodeMap[langIdx] !== undefined) {
				languageType = languageList[langCodeMap[langIdx]];
			} else {
				const n = Number(langIdx);
				if (!isNaN(n) && languageList[n]) {
					languageType = languageList[n];
				}
			}
		}
		const userStore = uni.getStorageSync('user');
		const userToken = (userStore && userStore.token) || '';
		uni.request({
			url: api.path + url,
			data: data,
			method: method,
			timeout: 20000,
			header: {
				'content-type': 'application/x-www-form-urlencoded',
				'Accept-Language': languageType,
				'token': userToken
			},
			success(res) {
				if (res && res.data && res.data.code == 1) {
					if (dataType) {
						resolve(res.data);
					} else {
						resolve(res.data.data);
					}
				}else if(res && res.data && res.data.code == 4002){
					resolve(res.data);
				}else if(res && res.data && res.data.code == 4003){
					resolve(res.data);
				}else if(res && res.data && res.data.code == 101){
					resolve(res.data);
				}else if (res && res.data && res.data.code == 10001) {
					clearStoragePreserve(); 
					toLogin();
					reject(new Error((res.data && res.data.msg) || '请重新登录'));
				}else if(res && res.data && res.data.code==10005){
					resolve(res.data);
				}else{
					const msg = (res && res.data && res.data.msg) || '请求失败';
					showToast(msg);
					reject(new Error(msg));
				}
			},
			fail(err) {
				const msg = '加载失败，请检查网络';
				showToast(msg);
				reject(err || new Error(msg));
			}
		})
	});
}

/**
 * 获取用户信息
 */
function getUserInfo(data = {}) {
	let that = this;
	return new Promise((resolve, reject) => {
		that.request(
			'/api/doctor/info', data, "POST"
		).then(res => {
			resolve(res);
		})
	});
}
function seeimg(list,index) {
	if(list[0].indexOf('http') == -1){
		for(let i = 0 ; i < list.length ; i++){
			list[i] = api.img + list[i];
			if(i == list.length-1){
				console.log(list)
				uni.previewImage({
					current: index,
					urls: list
				})
			}
		}
	}else{
		console.log(list)
		uni.previewImage({
			current: index,
			urls: list
		})
	}
}
/**
 * 跳转到登录页面,并清空缓存
 */
function toLogin(e) {
	uni.hideLoading()
	clearStoragePreserve();
	uni.reLaunch({
		url: "/pages/login/login"
	})
}
/**
 * 消息提示框
 */
function showToast(msg = '') {
	uni.showToast({
		title: msg,
		duration: 2000,
		icon: 'none'
	});
}
/**
 *  弹出提示信息结束后执行方法
 */
function showMsg(msg, callback) {
	uni.showToast({
		title: msg,
		icon: 'none',
		duration: 2000,
		success: function() {
			setTimeout(callback, 2000);
		}
	})
}
/**
 *  页面跳转
 */
function urlTo(e) {
	uni.navigateTo({
		url: e
	})
}
/**
 *  微信订阅消息
 */
function subScribeMsg(e) {
  return new Promise((resolve, reject) => {
    uni.getSetting({
      withSubscriptions: true,
      success(res) {
        console.log('1', res, '订阅信息', res.subscriptionsSetting);
        if (!res.subscriptionsSetting.mainSwitch) {
          uni.openSetting({
            success(res) {
              console.log('打开设置页', res.authSetting);
            }
          })
        } else {
          uni.requestSubscribeMessage({
            tmplIds: [e],
            success(res) {
              console.log('requestSubscribeMessage 订阅信息', res);
              resolve(res)
              if (res[e] == "accept") { // 用户点击确定后
                console.log('用户订阅点击确定按钮');
                // that.getSubMsg()
              } else {
                console.log('拒绝');
              }
            },
            fail(errMessage) {
              reject(errMessage)
              console.log("订阅消息 失败 ", errMessage);
            },
            complete() {
              // if (that.ordercode == null) return that.getOrder(appoint);
              // if (that.ordercode != null) return that.getPayOrder(appoint);
              // that.startDisabled = false
            }
          })
        }
      },
    })
  })
}
 
/**
 *手机掩码
 */
function phoneMask(phone) {
	if (phone) {
		return phone.substring(0, 3) + '****' + phone.substring(7);
	}
}
/**
 * 验证手机号
 */
function checkPhone(phone) {
	let zz = /^1[3456789]\d{9}$/;
	return zz.test(phone);
}
/**
 * 验证姓名
 */
function checkName(name) {
	let zz = /^[\u4E00-\u9FA5\uf900-\ufa2d·s]{2,20}$/; //验证姓名正则
	return zz.test(name);
}
/*  
验证日期
*/
function checkDate(date) {
	let shengri_zz = /^\d{4}-\d{2}-\d{2}$/; //日期验证
	return shengri_zz.test(date);
}

/*  
格式化时间戳
*/
function timeChange(timestamp) {
	var date = new Date(timestamp * 1000); //时间戳为10位需*1000，时间戳为13位的话不需乘1000
	var Y = date.getFullYear() + '-';
	var M = (date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1) + '-';
	var D = (date.getDate() < 10 ? '0' + date.getDate() : date.getDate()) + ' ';
	var h = (date.getHours() < 10 ? '0' + date.getHours() : date.getHours()) + ':';
	var m = (date.getMinutes() < 10 ? '0' + date.getMinutes() : date.getMinutes()) + ':';
	var s = date.getSeconds() < 10 ? '0' + date.getSeconds() : date.getSeconds();
	return Y + M + D + h + m + s;
}
/* 
 判断是否微信浏览
 */
function isWeiXin() {
	var ua = window.navigator.userAgent.toLowerCase();
	if (ua.match(/MicroMessenger/i) == 'micromessenger') {
		return true;
	} else {
		return false;
	}
}
/* 
		验证邮箱
	 */
function checkEmail(value) {
	return /^[a-z0-9]+([._\\-]*[a-z0-9])*@([a-z0-9]+[-a-z0-9]*[a-z0-9]+.){1,63}[a-z0-9]+$/.test(value)
}
/* 
		身份证验证
	 */
function checkIDCard(idNum) {
	if (!idNum) {
		return false
	}
	// alert(idNum);
	var errors = new Array( // eslint-disable-line
		"alert('验证通过');",
		"alert('身份证号码位数不对');",
		"alert('身份证含有非法字符');",
		"alert('身份证号码校验错误');",
		"alert('身份证地区非法');"
	)
	// 身份号码位数及格式检验
	var re
	var len = idNum.length
	// 身份证位数检验
	if (len != 15 && len != 18) { // eslint-disable-line
		return false
	} else if (len == 15) { // eslint-disable-line
		re = new RegExp(/^(\d{6})()?(\d{2})(\d{2})(\d{2})(\d{3})$/)
	} else {
		re = new RegExp(/^(\d{6})()?(\d{4})(\d{2})(\d{2})(\d{3})([0-9xX])$/)
	}
	var area = {
		11: '北京',
		12: '天津',
		13: '河北',
		14: '山西',
		15: '内蒙古',
		21: '辽宁',
		22: '吉林',
		23: '黑龙江',
		31: '上海',
		32: '江苏',
		33: '浙江',
		34: '安徽',
		35: '福建',
		36: '江西',
		37: '山东',
		41: '河南',
		42: '湖北',
		43: '湖南',
		44: '广东',
		45: '广西',
		46: '海南',
		50: '重庆',
		51: '四川',
		52: '贵州',
		53: '云南',
		54: '西藏',
		61: '陕西',
		62: '甘肃',
		63: '青海',
		64: '宁夏',
		65: '新疆',
		71: '台湾',
		81: '香港',
		82: '澳门',
		91: '国外'
	}
	var idcard_array = new Array() // eslint-disable-line
	idcard_array = idNum.split('') // eslint-disable-line
	// 地区检验
	if (area[parseInt(idNum.substr(0, 2))] == null) {
		return false
	}
	// 出生日期正确性检验
	var a = idNum.match(re)
	if (a != null) { // eslint-disable-line
		var flag
		var DD
		if (len == 15) { // eslint-disable-line
			DD = new Date('19' + a[3] + '/' + a[4] + '/' + a[5])
			flag = DD.getYear() == a[3] && (DD.getMonth() + 1) == a[4] && DD.getDate() == a[5] // eslint-disable-line
		} else if (len == 18) { // eslint-disable-line
			DD = new Date(a[3] + '/' + a[4] + '/' + a[5])
			flag = DD.getFullYear() == a[3] && (DD.getMonth() + 1) == a[4] && DD.getDate() == a[
				5] // eslint-disable-line
		}
		if (!flag) {
			// return false;
			return false
		}
		// 检验校验位
		if (len == 18) { // eslint-disable-line
			var S = (parseInt(idcard_array[0]) + parseInt(idcard_array[10])) * 7 +
				(parseInt(idcard_array[1]) +
					parseInt(idcard_array[11])) * 9 +
				(parseInt(idcard_array[2]) +
					parseInt(idcard_array[12])) * 10 +
				(parseInt(idcard_array[3]) +
					parseInt(idcard_array[13])) * 5 +
				(parseInt(idcard_array[4]) +
					parseInt(idcard_array[14])) * 8 +
				(parseInt(idcard_array[5]) +
					parseInt(idcard_array[15])) * 4 +
				(parseInt(idcard_array[6]) +
					parseInt(idcard_array[16])) * 2 +
				parseInt(idcard_array[7]) * 1 +
				parseInt(idcard_array[8]) * 6 +
				parseInt(idcard_array[9]) * 3
			var Y = S % 11
			var M = 'F'
			var JYM = '10X98765432'
			M = JYM.substr(Y, 1) // 判断校验位
			// 检测ID的校验位
			if (M == idcard_array[17]) { // eslint-disable-line
				return true
				// return "";
			} else {
				// return false;
				return false
			}
		}
	} else {
		// return false;
		return false
	}
	return true
}
/* 
 APP检查更新
 */
function getVersion(e) {
	let _this = this;
	_this.request('', {
		type: api.appType
	}, 'POST').then(res => {
		if (1 == 1) {
			uni.showModal({
				title: '更新提示',
				content: '为了您的正常使用请您更新最新版本',
				showCancel: false, 
				confirmText: '立即更新',
				success: function(gdx) {
					if (gdx.confirm) {
						console.log(res.data.url)
						plus.runtime.openURL(res.data.url);
					}
				}
			});
		}
		if (e == 1) {
			showToast('您已是最新版本');
		}
	})
}
/* 
 微信,支付宝 app支付调取
 */
function appPay(type, data) {
	let payType;
	if (type == 0) {
		payType = 'wxpay';
	} else if (type == 1) {
		payType = 'alipay'
	}
	return new Promise(function(resolve, reject) {
		uni.requestPayment({
			provider: payType,
			orderInfo: data,
			success(res) {
				console.log(res)
				showToast('支付成功');
				resolve();
			},
			fail(err) {
				console.log(err)
				showToast('支付失败');
			}
		})
	});
}
/* 
 微信小程序支付
 */
function wxPay(data) {
	return new Promise(function(resolve, reject) {
		uni.requestPayment({
			provider: 'wxpay',
			timeStamp: data.timeStamp,
			nonceStr: data.nonceStr,
			package: data.package,
			signType: data.signType,
			paySign: data.paySign,
			success: function(res) {
				console.log('success:' + JSON.stringify(res));
				showToast('支付成功');
				resolve();
			},
			fail: function(err) {
				console.log('fail:' + JSON.stringify(err));
				showToast('支付失败');
			}
		});
	});
}
/* 
 微信小程序版本更新
 */
function wxUpdate() {
	console.log('版本更新',wx.canIUse('getUpdateManager'))
	if (wx.canIUse('getUpdateManager')) {
		const updateManager = wx.getUpdateManager()
		updateManager.onCheckForUpdate(function(res) {
			console.log(res,'----')
			// 请求完新版本信息的回调
			if (res.hasUpdate) {
				updateManager.onUpdateReady(function() { 
					wx.showModal({
						title: '更新提示',
						content: '新版本已经准备好，是否重启应用？',
						success: function(res) {
							if (res.confirm) {
								// 新的版本已经下载好，调用 applyUpdate 应用新版本并重启
								updateManager.applyUpdate()
							}
						}
					})
				})
				updateManager.onUpdateFailed(function() {
					// 新的版本下载失败
					wx.showModal({
						title: '已经有新版本了哟~',
						content: '新版本已经上线啦~，请您删除当前小程序，重新搜索打开哟~',
					})
				})
			}
		})
	} else {
		// 如果希望用户在最新版本的客户端上体验您的小程序，可以这样子提示
		wx.showModal({
			title: '提示',
			content: '当前微信版本过低，无法使用该功能，请升级到最新微信版本后重试。'
		})
	}
}
/* 
 预览图片
 e:图片地址 可单张字符串或数组
 index:图片索引
 item:多数据数组情况下传 图片字段名
 */
function lookImg(e, index = 0, item) {
	let url;
	if (Array.isArray(e)) {
		url = e;
	} else {
		url = [e];
	}
	let urls = [];
	if (item) {
		url.map(i => {
			urls.push(i[item]);
		})
		url = urls;
	}
	uni.previewImage({
		urls: url,
		current: index
	})
}
/* 
 腾讯逆解析 传经纬度获取地址
 */
function getDistrict(latitude, longitude) {
	return new Promise((resolve, reject) => {
		// let keys = 'VCIBZ-WKSCX-DY74A-TCM3S-GLX7S-IABIU'//测试key
		let keys = 'Z2WBZ-7BB33-PSF3E-32VEW-SZMGO-6UBKK'
		wx.request({
			url: `https://apis.map.qq.com/ws/geocoder/v1/?location=${latitude},${longitude}&key=${keys}`,
			header: {
				'Content-Type': 'application/json'
			},
			success: function(res) {
				console.log(res)
				resolve(res.data.result)
			}
		})
	})
}
/* 
 上传文件uploadFile
 */
function uploadFile(img) {
	uni.showLoading({
		title: '上传中'
	});
	let _this = this;
	const token = uni.getStorageSync('user').token;
	return new Promise(function(resolve, reject) {
		uni.uploadFile({
			url: api.path + 'common/upload',
			filePath: img,
			name: 'file',
			formData: {
				token: token
			},
			success: (uploadFileRes) => {
				console.log(uploadFileRes)
				let up = JSON.parse(uploadFileRes.data);
				if (up.code == 1) {
					resolve(up.data);
					uni.hideLoading();
				} else {
					_this.showToast(up.msg);
				}
			}
		});
	});
}
/* 
获取当前之后第几天的日期
 */
function getLaterDay(day) {
	var today = new Date();
	var targetday_milliseconds = today.getTime() + 1000 * 60 * 60 * 24 * day;
	today.setTime(targetday_milliseconds); //注意，这行是关键代码
	var tYear = today.getFullYear();
	var tMonth = today.getMonth();
	var tDate = today.getDate();
	tMonth = doHandleMonth(tMonth + 1);
	tDate = doHandleMonth(tDate);
	return tYear + '-' + tMonth + "-" + tDate;

	function doHandleMonth(month) {
		var m = month;
		if (month.toString().length == 1) {
			m = '0' + month;
		}
		return m;
	}
}
/* 
格式化秒时间戳 为00:00:00格式 
 */
function secChange(time) {
	let h = parseInt(time / 3600);
	let min = parseInt((time - h * 3600) / 60);
	let s = parseInt(time - h * 3600 - min * 60);
	h = (h < 9 ? '0' : '') + h;
	min = (min < 9 ? '0' : '') + min;
	s = (s < 9 ? '0' : '') + s;
	return h + ':' + min + ':' + s;
}
/* 
 返回上级页面 time延迟执行的时间
 */
function backTo(e = 1, time = 0) {
	setTimeout(function() {
		uni.navigateBack({
			delta: e
		})
	}, time);
}
/**
 * 安全返回：有页面栈则 navigateBack，否则跳转 tabBar 页（Tab 根页、直开子页、H5 刷新后 navigateBack 会失败）
 * @param {string} fallbackTabUrl tabBar 路径，如 /pages/tavern/tavernInbox
 * @param {number} delta 返回层数，默认 1
 */
function safeNavigateBack(fallbackTabUrl, delta = 1) {
	uni.navigateBack({
		delta,
		fail: () => {
			if (fallbackTabUrl) {
				uni.switchTab({ url: fallbackTabUrl });
			}
		}
	});
}
/* 
 从本地选择图片
 */
function addImg(num = 1, type) {
	let sourceType;
	if (type == 1) {
		sourceType = ['album'];
	} else if (type == 2) {
		sourceType = ['camera'];
	} else {
		sourceType = ['album', 'camera'];
	}
	return new Promise((resolve, reject) => {
		uni.chooseImage({
			count: num, //默认9
			sourceType: sourceType, //从相册选择
			success: function(res) {
				if (num == 1) {
					resolve(res.tempFilePaths[0]);
				} else {
					resolve(res.tempFilePaths);
				}
			}
		});
	})
}
/* 
 从本地选择视频
 */
function addVideo() {
	return new Promise((resolve, reject) => {
		uni.chooseVideo({
			success: function(res) {
				resolve(res.tempFilePath);
			}
		});
	})
}
/* 
 计算现在距未来某个节点的时间（倒计时）
 type:1时startTime为日期格式  2时startTime为秒级时间戳格式
 */
function getLiveTimeCount(startTime, type = 1) {
	if (type == 1) {
		let transedPreTime = startTime.replace(/-/g, '/'); //这里转化时间格式为以/分隔形式
		let preTime = new Date(transedPreTime).getTime();
	} else {
		let preTime = new Date(startTime * 1000).getTime();
	}
	let nowTime = new Date().getTime();
	let obj = null;
	if (preTime - nowTime > 0) {
		let time = (preTime - nowTime) / 1000;
		let day = parseInt(time / (60 * 60 * 24));
		let hou = parseInt(time % (60 * 60 * 24) / 3600);
		let min = parseInt(time % (60 * 60 * 24) % 3600 / 60);
		let sec = parseInt(time % (60 * 60 * 24) % 3600 % 60);
		obj = {
			day: day < 10 ? '0' + day : day,
			hou: hou < 10 ? '0' + hou : hou,
			min: min < 10 ? '0' + min : min,
			sec: sec < 10 ? '0' + sec : sec
		};
	}
	return obj; //倒计时计算后的时间  {日：时：分：秒}
}
/* 
 复制文本到剪切板
 */
function copy(e) {
	uni.setClipboardData({
		data: e,
		success: function() {}
	});
}
/* 
 拨打电话
 */
function dial(e) {
	uni.makePhoneCall({
		phoneNumber: e
	});
}
/*
根据身份证号验证男女
*/ 
function getGenderFromIdCard(idCard) {
    // 定义男女性别对应的数字编码
    var genderCode = idCard.substr(-2, 1);
    
    if (genderCode % 2 === 0) {
        return 0;//女
    } else {
        return 1;//男
    }
}
/* 
截取h5地址的某一参数值 
 */
function GetQueryString(name) {
	var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
	var r = window.location.search.substr(1).match(reg);
	if (r != null) return (decodeURIComponent(r[2]));
	return null;
}
/**
 * h5 跳转地图导航跳转地图app
 * 根据地图类型、位置获取不同的地图页面跳转链接
 * @param {*} mapType 地图类型
 * @param {*} location 经纬度 lat:纬度 lng:经度
 * @param {*} address 详细地址
 */
function getMapApp(mapType, lat, lng, address) {
	let url = '';
	switch (mapType) {
		case '腾讯地图':
			url = 'https://apis.map.qq.com/uri/v1/marker?marker=coord:' + lat + ',' + lng + ';addr:' +
				address + ';title:' + address + '&referer=keyfree';
			break;
		case '高德地图':
			url = 'https://uri.amap.com/marker?position=' + lng + ',' + lat + '&name=' + address +
				'&callnative=1';
			break;
		case '百度地图':
			url = 'http://api.map.baidu.com/marker?location=' + lat + ',' + lng + '&title=' + address + '&content=' +
				address + '&output=html&src=webapp.reformer.appname&coord_type=gcj02';
			break;
		default:
			break;
	}
	window.location.href = url;
}
module.exports = {
	request,
	toLogin,
	showToast,
	showMsg,
	checkPhone,
	checkName,
	checkDate,
	urlTo,
	phoneMask,
	getUserInfo,
	timeChange,
	isWeiXin,
	checkEmail,
	checkIDCard,
	getVersion,
	appPay,
	wxPay,
	wxUpdate,
	lookImg,
	getDistrict,
	uploadFile,
	getLaterDay,
	secChange,
	backTo,
	safeNavigateBack,
	addImg,
	addVideo,
	getLiveTimeCount,
	copy,
	dial,
	GetQueryString,
	getMapApp,
	api,
	seeimg,
	getGenderFromIdCard,
	subScribeMsg
}
