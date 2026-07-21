<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="allText.我的页.修改密码" mode="dark" @back="goBack" />
		<view class="body">
			<view class="inp-wrap">
				<input
					type="password"
					:placeholder="allText.我的页.请输入原密码"
					class="inp"
					v-model="oldpwd"
				/>
			</view>
			<view class="inp-wrap">
				<input
					type="password"
					:placeholder="allText.我的页.请输入新密码"
					class="inp"
					v-model="newpwd"
				/>
			</view>
			<view class="inp-wrap">
				<input
					type="password"
					:placeholder="allText.我的页.再次输入新密码"
					class="inp"
					v-model="twopwd"
				/>
			</view>
		</view>
		<view class="footer">
			<fui-button background="#348FB8" radius="46rpx" :disabled="submitting" @tap="save">{{ submitting ? '提交中...' : allText.我的页.确定 }}</fui-button>
		</view>
	</view>
</template>

<script>
	import fuiButton from '@/components/firstui/fui-button/fui-button.vue';
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	export default {
		components: {
			fuiButton,
			TavernNavBar
		},
		data() {
			return {
				oldpwd: '',
				newpwd: '',
				twopwd: '',
				submitting: false
			};
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
			},
			goUserHome() {
				uni.switchTab({ url: '/pages/user/user' });
			},
			normalizeMessage(message) {
				const text = String(message || '').trim();
				if (!text) return '修改失败，请稍后重试';
				if (text.indexOf('Old password is incorrect') !== -1) return '原密码错误';
				if (text.indexOf('Password length must be 6-64') !== -1) return '新密码长度需为 6-64 位';
				if (text.indexOf('login expired') !== -1) return '登录已失效，请重新登录';
				if (text.indexOf('Current account has no password login') !== -1) return '当前账号暂不支持密码修改';
				if (text.indexOf('Password updated') !== -1 || text === 'ok') return '密码修改成功';
				return text;
			},
			save() {
				if (this.submitting) {
					return;
				}
				if (!this.oldpwd) {
					this.util.showToast('请输入原密码');
					return;
				}
				if (!this.newpwd) {
					this.util.showToast('请输入新密码');
					return;
				}
				if (this.newpwd != this.twopwd) {
					this.util.showToast('两次输入的新密码不一致');
					return;
				}
				if (this.newpwd.length < 6 || this.newpwd.length > 64) {
					this.util.showToast('新密码长度需为 6-64 位');
					return;
				}
				if (this.oldpwd === this.newpwd) {
					this.util.showToast('新密码不能与原密码相同');
					return;
				}
				this.submitting = true;
				this.util
					.request(
						'user/reset_pwd',
						{
							token: uni.getStorageSync('user').token,
							old_pwd: this.oldpwd,
							new_pwd: this.newpwd
						},
						'POST'
					)
					.then((res) => {
						const message = this.normalizeMessage(res && res.msg);
						this.util.showToast(message);
						if (res && Number(res.code) === 1) {
							this.oldpwd = '';
							this.newpwd = '';
							this.twopwd = '';
							setTimeout(() => {
								this.goUserHome();
							}, 900);
							return;
						}
						if (res && Number(res.code) === 4003) {
							setTimeout(() => {
								uni.reLaunch({ url: '/pages/login/login' });
							}, 900);
						}
					})
					.catch((err) => {
						this.util.showToast(this.normalizeMessage(err && err.message));
					})
					.finally(() => {
						this.submitting = false;
					});
			}
		}
	};
</script>

<style scoped lang="scss">
	.page {
		min-height: 100vh;
		background: $tavern-page-bg;
		display: flex;
		flex-direction: column;
	}

	.body {
		flex: 1;
		padding: 24rpx 24rpx 32rpx;
	}

	.inp-wrap {
		margin-bottom: 20rpx;
		background: rgba(255, 255, 255, 0.66);
		border-radius: 44rpx;
		height: 88rpx;
		box-shadow: 0 14rpx 28rpx rgba(67, 112, 142, 0.08);
	}

	.inp {
		height: 88rpx;
		font-size: 30rpx;
		padding-left: 36rpx;
		width: 100%;
		box-sizing: border-box;
		color: #16384d;
	}

	.footer {
		flex-shrink: 0;
		padding: 24rpx 105rpx calc(24rpx + env(safe-area-inset-bottom));
	}
</style>
