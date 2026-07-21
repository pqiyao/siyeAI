<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="uiText.title" mode="light" @back="goBack" />
		<view class="hero">
			<text class="hero-title">{{ uiText.title }}</text>
			<text class="hero-sub">{{ uiText.subtitle }}</text>
		</view>
		<scroll-view scroll-y class="scroll">
			<view
				v-for="item in options"
				:key="item.code"
				class="row"
				:class="{ 'row--active': currentValue === item.value }"
				@tap="currentValue = item.value"
			>
				<view class="row-main">
					<text class="row-label">{{ item.label }}</text>
					<text class="row-code">{{ item.code }}</text>
				</view>
				<view class="row-check" :class="{ 'row-check--active': currentValue === item.value }"></view>
			</view>
		</scroll-view>
		<view class="footer">
			<fui-button background="#2f6f7e" radius="46rpx" @tap="submit">{{ uiText.confirm }}</fui-button>
		</view>
	</view>
</template>

<script>
	import fuiButton from '@/components/firstui/fui-button/fui-button.vue';
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
	import { refreshI18nViews } from '@/common/i18nRefresh.js';
	const { getLocaleOptions, getTavernUiText, getLanguageIndex } = require('@/common/tavernUiI18n.js');

	export default {
		components: {
			fuiButton,
			TavernNavBar
		},
		data() {
			return {
				options: getLocaleOptions(),
				currentValue: 1
			};
		},
		computed: {
			uiText() {
				return getTavernUiText('language');
			}
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) });
			},
			submit() {
				const clang =
					this.currentValue === 0
						? 'zh-hk'
						: this.currentValue === 1
							? 'zh-cn'
							: this.currentValue === 2
								? 'en'
								: this.currentValue === 3
									? 'ko'
									: 'ja';

				uni.setStorageSync('languageType', this.currentValue);
				uni.setStorageSync('userManuallySelectedLanguage', true);
				this.languageChange();
				refreshI18nViews(this.allText);

				const user = uni.getStorageSync('user');
				const token = user && user.token;
				if (token) {
					this.util.request('user/updLang', { token, clang }).catch(() => {});
				}

				uni.showToast({ title: this.uiText.confirm, icon: 'success' });
				setTimeout(() => {
					this.goBack();
				}, 350);
			}
		},
		onShow() {
			this.currentValue = getLanguageIndex();
		}
	};
</script>

<style scoped lang="scss">
	.page {
		min-height: 100vh;
		background:
			radial-gradient(circle at 14% 0%, rgba(139, 210, 223, 0.32), transparent 38%),
			radial-gradient(circle at 92% 6%, rgba(255, 215, 229, 0.38), transparent 34%),
			linear-gradient(180deg, #f7fbfd 0%, #eef7f8 48%, #f9f5ef 100%);
		display: flex;
		flex-direction: column;
	}

	.hero {
		margin: 22rpx 24rpx 12rpx;
		padding: 30rpx;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.74);
		border: 1rpx solid rgba(102, 146, 160, 0.18);
		box-shadow: 0 18rpx 44rpx rgba(76, 112, 128, 0.12);
		backdrop-filter: blur(18rpx);
		-webkit-backdrop-filter: blur(18rpx);
	}

	.hero-title {
		display: block;
		font-size: 42rpx;
		font-weight: 800;
		color: #183b4a;
	}

	.hero-sub {
		display: block;
		margin-top: 10rpx;
		font-size: 24rpx;
		line-height: 1.6;
		color: #607987;
	}

	.scroll {
		flex: 1;
		height: 0;
		padding: 10rpx 0 28rpx;
	}

	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 0 24rpx 18rpx;
		padding: 28rpx 30rpx;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.86);
		border: 1rpx solid rgba(84, 127, 140, 0.16);
		box-shadow:
			0 16rpx 34rpx rgba(61, 100, 112, 0.11),
			inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
	}

	.row--active {
		background:
			linear-gradient(135deg, rgba(228, 248, 250, 0.96), rgba(255, 255, 255, 0.92));
		border-color: rgba(47, 111, 126, 0.42);
		box-shadow:
			0 20rpx 42rpx rgba(47, 111, 126, 0.16),
			inset 0 0 0 2rpx rgba(47, 111, 126, 0.08);
	}

	.row-main {
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.row-label {
		font-size: 30rpx;
		font-weight: 700;
		color: #17313b;
	}

	.row-code {
		font-size: 22rpx;
		letter-spacing: 1rpx;
		color: #77909b;
		text-transform: uppercase;
	}

	.row-check {
		width: 34rpx;
		height: 34rpx;
		border-radius: 999rpx;
		border: 2rpx solid rgba(47, 111, 126, 0.28);
		background: #ffffff;
		box-shadow: inset 0 0 0 6rpx transparent;
	}

	.row-check--active {
		border-color: rgba(47, 111, 126, 0.96);
		background: linear-gradient(135deg, #47b9bd, #2f6f7e);
		box-shadow:
			0 0 0 6rpx rgba(47, 111, 126, 0.08),
			0 12rpx 24rpx rgba(47, 111, 126, 0.24);
	}

	.footer {
		flex-shrink: 0;
		padding: 20rpx 30rpx calc(28rpx + env(safe-area-inset-bottom));
		background: linear-gradient(180deg, rgba(247, 251, 253, 0), rgba(247, 251, 253, 0.92) 34%);
	}
</style>
