export function compareVersion(v1, v2) {
	v1 = v1.split('.')
	v2 = v2.split('.')
	const len = Math.max(v1.length, v2.length)
	while (v1.length < len) {
		v1.push('0')
	}
	while (v2.length < len) {
		v2.push('0')
	}
	for (let i = 0; i < len; i++) {
		const num1 = parseInt(v1[i], 10)
		const num2 = parseInt(v2[i], 10)

		if (num1 > num2) {
			return 1
		} else if (num1 < num2) {
			return -1
		}
	}
	return 0
}

export const ENV_TYPE = () => {
	let {SDKVersion} = uni.getSystemInfoSync()
	if(!uni.getEnv) {
		uni.getEnv = () => {
			// #ifdef MP-ALIPAY
			SDKVersion = my.SDKVersion
			return 'ALIPAY'
			// #endif
			// #ifdef MP-WEIXIN
			return 'WEAPP'
			// #endif
			// #ifdef MP-TOUTIAO
			SDKVersion = my.SDKVersion
			return 'TT'
			// #endif
			return 'uni'
		}
	}
	const type = {
		'WEAPP': '2.9.7',
		'TT': '1.78.0',
		'ALIPAY': '2.7.0',
	}[uni.getEnv()]
	return type && SDKVersion && compareVersion(SDKVersion, type) >= 1
}
// #ifdef MP
export const prefix = () => {
	// #ifdef MP-TOUTIAO
	return tt
	// #endif
	// #ifdef MP-WEIXIN
	return wx
	// #endif
	// #ifdef MP-BAIDU
	return swan
	// #endif
	// #ifdef MP-ALIPAY
	return my
	// #endif
	// #ifdef MP-QQ
	return qq
	// #endif
	// #ifdef MP-360
	return qh
	// #endif
}
// #endif

/**
 * base64转路径
 * @param {Object} base64
 */
export function base64ToPath(base64) {
	const [, format, bodyData] = /data:image\/(\w+);base64,(.*)/.exec(base64) || [];

	return new Promise((resolve, reject) => {
		// #ifdef MP
		const fs = uni.getFileSystemManager()
		//自定义文件名
		if (!format) {
			reject(new Error('ERROR_BASE64SRC_PARSE'))
		}
		const time = new Date().getTime();
		let pre = prefix()
		const filePath = `${pre.env.USER_DATA_PATH}/${time}.${format}`
		//let buffer = base64ToArrayBuffer(bodyData)
		fs.writeFile({
			filePath,
			data: base64.replace(/^data:\S+\/\S+;base64,/, ''),
			encoding: 'base64',
			// data: buffer,
			// encoding: 'binary',
			success() {
				resolve(filePath)
			},
			fail(err) {
				reject(err)
			}
		})
		// #endif

		// #ifdef H5
		// mime类型
		let mimeString = base64.split(',')[0].split(':')[1].split(';')[0];
		//base64 解码
		let byteString = atob(base64.split(',')[1]);
		//创建缓冲数组
		let arrayBuffer = new ArrayBuffer(byteString.length);
		//创建视图
		let intArray = new Uint8Array(arrayBuffer);
		for (let i = 0; i < byteString.length; i++) {
			intArray[i] = byteString.charCodeAt(i);
		}
		resolve(URL.createObjectURL(new Blob([intArray], {
			type: mimeString
		})))
		// #endif

		// #ifdef APP-PLUS
		const bitmap = new plus.nativeObj.Bitmap('bitmap' + Date.now())
		bitmap.loadBase64Data(base64, () => {
			if (!format) {
				reject(new Error('ERROR_BASE64SRC_PARSE'))
			}
			const time = new Date().getTime();
			const filePath = `_doc/uniapp_temp/${time}.${format}`
			bitmap.save(filePath, {},
				() => {
					bitmap.clear()
					resolve(filePath)
				},
				(error) => {
					bitmap.clear()
					reject(error)
				})
		}, (error) => {
			bitmap.clear()
			reject(error)
		})
		// #endif
	})
}

const svgaBus = new Map()
export function getfileBase64(url){
	return new Promise((resolve, reject)=>{
		plus.io.resolveLocalFileSystemURL(url, entry => {
			var reader = null;
			entry.file( file => {
				reader = new plus.io.FileReader();
				reader.onloadend = ( read )=> {
					resolve(read.target.result)
				};
				reader.readAsDataURL( file );
			}, reject );
		},reject)
	})
}
export function toLoadPath(path) {
	return new Promise(resolve => {
		// #ifndef APP-VUE || APP-NVUE
		if(!/^\/static/.test(path)) {
			resolve(path)
		}
		// #endif
		// #ifdef H5
		resolve(path)
		// #endif
		// #ifdef APP-VUE || APP-NVUE
		let {osName} = uni.getSystemInfoSync()
		const success = (res) => {
			if(osName === 'ios') {
				res.getParent((parentDicEntry) => {
					res.moveTo(
						{ fullPath: parentDicEntry.fullPath + "/" },
						`${new Date().getTime()}.svga`,
						(newPath) => {
							getfileBase64(newPath.fullPath).then((base64) => {
								svgaBus.set(path, base64);
								resolve(base64);
							});
						}
					)
				})
			} else {
				svgaBus.set(path, res.fullPath);
				resolve(res.fullPath)
			}
		}
		const error = (err) => {
			uni.showToast({
				title: err,
				duration: 2000
			});
			console.error(err)
		}
		if(svgaBus.has(path)) {
			resolve(svgaBus.get(path))
		} else if(/^http|\/\//.test(path)){
			// !/^\/static/.test(path) && (!/^_www/.test(path) || !/^_doc/.test(path))
			uni.downloadFile({
				url: path,
				success(res) {
					plus.io.resolveLocalFileSystemURL(res.tempFilePath, success, error)
					// #ifdef APP-VUE
					// svgaBus.set(path, res.tempFilePath)
					// resolve(res.tempFilePath)
					// #endif
					// #ifdef APP-NVUE
					// plus.io.resolveLocalFileSystemURL(res.tempFilePath, success, error)
					// #endif
				}
			})
		} else {
			plus.io.resolveLocalFileSystemURL((/^(?:_www|_doc|file:\/\/|\/storage\/)/.test(path)) ?  path :  '_www' + path, (res) => {
				// #ifdef APP-VUE
				svgaBus.set(path, res.fullPath)
				resolve(res.fullPath)
				// #endif
				// #ifdef APP-NVUE
				success(res)
				// #endif
			}, error)
		}
		// #endif
		// #ifndef APP-VUE || APP-NVUE || H5
		resolve(path)
		// #endif
	})
	
}