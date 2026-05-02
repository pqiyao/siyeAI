<template>
	<view>
		<u-navbar title="" :background="background" :immersive="true" title-size="34" :title-bold="true"
			title-color="#fff"></u-navbar>
		<view class="bg">
			<view class="" style="text-align: center;">
				<image src="/static/logo.png" mode="" class="logo"></image>
			</view>

			<view class="input-box" style="">
				<input type="text" :placeholder="allText.登录页.请输入邮箱号" v-model="email"/>
			</view>


			<view class="input-boxs">
				<input type="text" :placeholder="allText.登录页.请输入验证码" maxlength="11" v-model="captcha"
					style="width: 45%;height: 80rpx;background: #F9FCFF;border: none;border-radius: 45rpx;font-size: 30rpx;padding-left: 49rpx;" />
				<!-- <image :src="code_url" mode="" style="width: 205rpx;height: 68rpx;padding-right: 31rpx;"
					@tap="getCap"></image> -->
				<view class="" style="font-size: 28rpx;padding-right: 18rpx;" @tap="getCode">
					 {{codeNun==60 ? allText.登录页.获取验证码 : codeNun+'s'}}
				</view>	
			</view>

			<view class="input-boxs">
				<input :type="inputshow ? 'text' : 'password'" :placeholder="allText.登录页.请输入新密码" maxlength="11" v-model="newpassword"
					style="width: 80%;height: 80rpx;background: #F9FCFF;border: none;border-radius: 45rpx;font-size: 30rpx;padding-left: 49rpx;" />
				<image :src="inputshow ? '/static/bieye.png' : '/static/eye.png'" mode=""
					style="width: 40rpx;height: 40rpx;margin-right: 38rpx;" @tap="inputshow=!inputshow"></image>
			</view>


			<view class="" style="margin: 54rpx 68rpx 50rpx 68rpx;">
				<fui-button background="#5A7EF6" radius="46rpx" @tap="forget">{{allText.登录页.立即重置密码}}</fui-button>
			</view>
		</view>

	</view>
</template>

<script>
	import fuiButton from "@/components/firstui/fui-button/fui-button.vue"
	export default {
		data() {
			return {
				inputshow: false,
				show: false,
				background: {
					backgroundColor: ''
				},
				code_id: '',
				code_url: '',
				email:'',
				captcha:'',
				newpassword:'',
				codeNun: 60
			}
		},
		components: {
			fuiButton
		},
		methods: {
			timer() {
				let _this = this;
				let type = 1;
				setInterval(function() {
					if (_this.codeNun == 0) {
						_this.codeNun = 60;
						type = 0;
					} else if (type == 1) {
						_this.codeNun--;
					}
				}, 1000)
			},
			getCode() {
				if (this.codeNun != 60) {
					return false;
				}
				if (this.email=='') {
					this.util.showToast(this.allText.登录页.请输入邮箱号)
					return false
				}
				this.util.request('/ems/send', {
					email:this.email,
					event:'mobilelogin',
			
				}).then(res => {
					this.timer();
				})
			},
			forget(){
				this.util.request('index/forgetPwd',{
					email:this.email,
					newpassword:this.newpassword,
					code:this.captcha,
					code_id:this.code_id
				},'POST').then(res=>{
					this.util.showToast(res.msg)
					setTimeout(()=>{uni.navigateBack()},1000)
				})
			},
			getCap() {
				this.util.request('index/getCaptchaUrl', {

				}).then(res => {
					this.code_id = res.code_id
					this.code_url = res.code_url
				})
			},
		},
		onShow() {
			this.getCap()
		}
	}
</script>

<style lang="scss">
	.bg {
		width: 100%;
		background: url(/static/login.png) no-repeat;
		background-size: 100% 100%;
		height: 1082rpx;
	}

	.btn {
		margin: 53rpx 169rpx 0rpx 169rpx;
		height: 86rpx;
		background: #5A7EF6;
		border-radius: 43rpx;
		text-align: center;
		line-height: 86rpx;
		font-size: 30rpx;
		color: #fff;
	}

	.logo {
		width: 260rpx;
		height: 260rpx;
		border-radius: 52rpx;
		margin: 204rpx auto 40rpx auto;

	}

	.foot {
		font-size: 30rpx;
		color: #666666;
		text-align: center;
	}

	.input-box {
		margin: 34rpx 68rpx;
		background-color: #F9FCFF;
		height: 90rpx;
		border-radius: 45rpx;

		input {
			height: 90rpx;

			font-size: 30rpx;
			padding-left: 54rpx;
		}
	}

	.input-boxs {
		margin: 34rpx 68rpx;
		background-color: #F9FCFF;
		height: 90rpx;
		border-radius: 45rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
</style>
