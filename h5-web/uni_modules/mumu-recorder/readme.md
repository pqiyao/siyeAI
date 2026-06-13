## 插件简绍

### 实现原理

> 通过 navigator.mediaDevices.getUserMedia(需要https环境) 这个api调用麦克风，获取到到音频流数据。
>
> 通过 MediaRecorder 这个构造函数对音频流进行接收，完成录制后会返回一个存储`Blob`内容的录制数据。


### 使用环境

需要https环境才能使用，本地测试可以在 manifest.json  中点击源码展示，找到h5 ，添加："devServer" : { "https" : true}

**请勿使用 UC浏览器 与 夸克等阿里旗下的浏览器，发现他们使用的内核都较低，无法正常获取音频流，并且都有对接音频流截取的插件，导致无法正常获取音频流的数据。在微信中可以正常使用，推荐在微信内打开演示案例 **

需要https环境才能使用！！！

需要https环境才能使用！！！

需要https环境才能使用！！！

### 插件使用

**插件已支持 uni_modules 支持组件easycom，以下代码演示的是普通使用**

``` html
<!-- HTML -->
	<view>
    <audio :src='recorder.localUrl' v-if='recorder' name='本地录音' controls="true"></audio>
    <view @click='handlerOnCahnger'>
			{{!status?'开始录音':'结束录音'}}
		</view>
		<mumu-recorder ref='recorder' @success='handlerSuccess' @error='handlerError'></mumu-recorder>
	</view>
```

``` javascript
// js
	import MumuRecorder from '@/uni_modules/mumu-recorder/components/mumu-recorder/mumu-recorder.vue'
	export default {
		components: { MumuRecorder },
		data() {
			return {
				status: false,
				recorder: null
			}
		},
		onLoad() {

		},
		methods: {
			handlerSave() {
				let tag = document.createElement('a')
				tag.href = this.recorder.localUrl
				tag.download = '录音'
				tag.click()
			},
			handlerOnCahnger() {
				if (this.status) {
					this.$refs.recorder.stop()
				} else {
					this.$refs.recorder.start()
				}
				this.status = !this.status
			},
			handlerSuccess(res) {
				console.log(res)
				this.recorder = res
			},
			handlerError(code) {
				switch (code) {
					case '101':
						uni.showModal({
							content: '当前浏览器版本较低，请更换浏览器使用，推荐在微信中打开。'
						})
						break;
					case '201':
						uni.showModal({
							content: '麦克风权限被拒绝，请刷新页面后授权麦克风权限。'
						})
						break
					default:
						uni.showModal({
							content: '未知错误，请刷新页面重试'
						})
						break
				}
			}
		}
	}
```

### 相关API

##### 组件内部方法（$refs 调用）

| 方法名 | 说明     | 参数 |
| ------ | -------- | ---- |
| start  | 开始录音 | 无   |
| stop   | 结束录音 | 无   |



##### 事件（Events）

| 事件名  | 说明                 | 回调参数                                                     |
| ------- | -------------------- | ------------------------------------------------------------ |
| success | 停止录音后调用此事件 | 返回录音数据，是一个对象<br />{ data: 音频的 blob 数据，上传请使用这个			<br />duration: 当前音频长度<br/>localUrl: 当前音频的本地链接，可直接通过 audio 标签进行播放 } |
| error   | 组件内部发生错误     | 错误码：<100 当前不是https环境> <101 浏览器不支持> <201 麦克风权限被拒绝> <500  未知错误> |

### 案例演示

![enter description here](https://h5plugin.mumudev.top/public/recorder/qrcode.png)

## 支持作者

![支持作者](https://student.mumudev.top/wxMP.jpg)