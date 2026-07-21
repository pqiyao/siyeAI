<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="allText.我的页.申请退款" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll">
			<view class="block">
				<text class="label">{{ allText.我的页.国家或地区 }}</text>
				<view class="field">
					<u-input v-model="country" border :placeholder="allText.我的页.国家或地区" />
				</view>
			</view>
			<view class="block">
				<text class="label">{{ allText.我的页.卡号 }}</text>
				<view class="field">
					<u-input v-model="id_no" border :placeholder="allText.我的页.卡号" />
				</view>
			</view>
			<view class="block">
				<text class="label">{{ allText.我的页.姓名 }}</text>
				<view class="field">
					<u-input v-model="username" border :placeholder="allText.我的页.姓名" />
				</view>
			</view>
			<view class="block">
				<text class="label">{{ allText.我的页.退款金额 }}</text>
				<view class="field">
					<u-input v-model="money" border :placeholder="allText.我的页.退款金额" />
				</view>
			</view>
			<view class="submit-wrap">
				<u-button type="primary" @click="createRefundLog">{{ allText.我的页.提交 }}</u-button>
			</view>
		</scroll-view>
		<u-popup v-model="show" border-radius="14" mode="center" @close="close">
			<view class="popup-box">
				<view class="popup-hd">Tips</view>
				<view class="popup-bd">
					{{ allText.我的页.先生您好已成功将您的资料汇总给财务部门您的资料正在审核中请您耐心等待 }}
				</view>
			</view>
		</u-popup>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	export default {
		components: { TavernNavBar },
		data() {
			return {
				show: false,
				country: '',
				id_no: '',
				username: '',
				money: ''
			};
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
			},
			close() {
				uni.navigateBack();
			},
			createRefundLog() {
				this.util
					.request('user/createRefundLog', {
						token: uni.getStorageSync('user').token,
						country: this.country,
						id_no: this.id_no,
						username: this.username,
						money: this.money
					})
					.then(() => {
						this.show = true;
					});
			}
		}
	};
</script>

<style scoped lang="scss">
	.page {
		height: 100vh;
		background: $tavern-page-bg;
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.scroll {
		flex: 1;
		height: 0;
		padding: 24rpx 30rpx 48rpx;
		box-sizing: border-box;
	}

	.block {
		margin-bottom: 28rpx;
		padding: 24rpx;
		background: #fff;
		border-radius: 16rpx;
		box-shadow: $tavern-card-shadow;
	}

	.label {
		font-size: 28rpx;
		color: #333;
	}

	.field {
		margin-top: 20rpx;
	}

	.submit-wrap {
		margin-top: 40rpx;
		padding: 0 12rpx;
	}

	.popup-box {
		width: 690rpx;
	}

	.popup-hd {
		padding: 20rpx;
		border-bottom: 1rpx solid #e2e2e2;
		font-weight: bold;
	}

	.popup-bd {
		padding: 20rpx;
	}
</style>
