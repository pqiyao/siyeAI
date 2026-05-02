<template>
	<view class="page">
		<tavern-nav-bar :title="allText.我的页.修改密码" mode="dark" @back="goBack" />
		<view class="body">
			<view class="inp-wrap">
				<input
					type="text"
					:placeholder="allText.我的页.请输入原密码"
					class="inp"
					v-model="oldpwd"
				/>
			</view>
			<view class="inp-wrap">
				<input
					type="text"
					:placeholder="allText.我的页.请输入新密码"
					class="inp"
					v-model="newpwd"
				/>
			</view>
			<view class="inp-wrap">
				<input
					type="text"
					:placeholder="allText.我的页.再次输入新密码"
					class="inp"
					v-model="twopwd"
				/>
			</view>
		</view>
		<view class="footer">
			<fui-button background="#5A7EF6" radius="46rpx" @tap="save">{{ allText.我的页.确定 }}</fui-button>
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
				twopwd: ''
			};
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
			},
			save() {
				if (this.newpwd != this.twopwd) {
					this.util.showToast('The two inputs are inconsistent');
				}
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
						this.util.showToast(res.msg);
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
		background: #eeeeee;
		border-radius: 44rpx;
		height: 88rpx;
	}

	.inp {
		height: 88rpx;
		font-size: 30rpx;
		padding-left: 36rpx;
		width: 100%;
		box-sizing: border-box;
	}

	.footer {
		flex-shrink: 0;
		padding: 24rpx 105rpx calc(24rpx + env(safe-area-inset-bottom));
	}
</style>
