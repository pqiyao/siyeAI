<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="allText.我的页.黑名单" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll" @scrolltolower="onScrollToLower">
			<view
				class="item"
				v-for="(i, k) in list"
				:key="k"
			>
				<view class="item-main">
					<image :src="i.avatar" mode="aspectFill" class="item-avatar"></image>
					<view class="item-meta">
						<text class="item-name">{{ i.nickname }}</text>
						<text class="item-sub">{{ i.height }}cm·{{ i.weight }}kg·{{ i.occupation_arr }}</text>
					</view>
				</view>
				<view class="btn" @tap="canle(i.user_id)">
					{{ allText.首页.取消 }}
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	export default {
		components: { TavernNavBar },
		data() {
			return {
				page: 1,
				list: []
			};
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
			},
			onScrollToLower() {
				this.getblack();
			},
			getblack(e) {
				if (e == 1) {
					this.page = 1;
					this.list = [];
				}
				this.util
					.request('friend/lahei_list', {
						token: uni.getStorageSync('user').token,
						page: this.page
					})
					.then((res) => {
						this.list = this.list.concat(res);
						this.page++;
					});
			},
			canle(e) {
				this.util
					.request(
						'mi/forbid_user',
						{
							token: uni.getStorageSync('user').token,
							uid: e
						},
						'POST'
					)
					.then((res) => {
						this.util.showToast(res.msg);
						this.getblack(1);
					});
			}
		},
		onShow() {
			this.getblack(1);
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
		padding: 16rpx 24rpx 32rpx;
		box-sizing: border-box;
	}

	.item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 139rpx;
		padding: 24rpx 28rpx;
		margin-bottom: 16rpx;
		background: #fff;
		border-radius: 16rpx;
		box-shadow: $tavern-card-shadow;
	}

	.item-main {
		display: flex;
		align-items: center;
		flex: 1;
		min-width: 0;
	}

	.item-avatar {
		width: 100rpx;
		height: 100rpx;
		border-radius: 50%;
		flex-shrink: 0;
	}

	.item-meta {
		padding-left: 28rpx;
		min-width: 0;
	}

	.item-name {
		display: block;
		font-size: 30rpx;
		font-weight: 800;
		color: #333;
	}

	.item-sub {
		display: block;
		font-size: 22rpx;
		padding-top: 10rpx;
		color: #666;
	}

	.btn {
		width: 140rpx;
		height: 50rpx;
		border-radius: 25rpx;
		border: 1rpx solid #333333;
		text-align: center;
		line-height: 50rpx;
		font-size: 26rpx;
		flex-shrink: 0;
		margin-left: 16rpx;
	}
</style>
