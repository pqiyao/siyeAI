<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="uiText.title" mode="dark" @back="goBack" />
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
			<fui-button background="#a855f7" radius="46rpx" @tap="submit">{{ uiText.confirm }}</fui-button>
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
			radial-gradient(circle at top, rgba(168, 85, 247, 0.2), transparent 36%),
			linear-gradient(180deg, #0f1124 0%, #16162d 44%, #111321 100%);
		display: flex;
		flex-direction: column;
	}

	.hero {
		padding: 20rpx 30rpx 12rpx;
	}

	.hero-title {
		display: block;
		font-size: 42rpx;
		font-weight: 700;
		color: #f7e9ff;
	}

	.hero-sub {
		display: block;
		margin-top: 10rpx;
		font-size: 24rpx;
		line-height: 1.6;
		color: rgba(236, 225, 255, 0.74);
	}

	.scroll {
		flex: 1;
		height: 0;
		padding: 8rpx 0 28rpx;
	}

	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 0 24rpx 18rpx;
		padding: 28rpx 30rpx;
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.06);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		box-shadow: 0 18rpx 44rpx rgba(7, 10, 23, 0.34);
	}

	.row--active {
		background: linear-gradient(180deg, rgba(170, 92, 255, 0.18), rgba(255, 255, 255, 0.07));
		border-color: rgba(216, 168, 255, 0.46);
	}

	.row-main {
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.row-label {
		font-size: 30rpx;
		font-weight: 600;
		color: #ffffff;
	}

	.row-code {
		font-size: 22rpx;
		letter-spacing: 1rpx;
		color: rgba(233, 223, 255, 0.54);
		text-transform: uppercase;
	}

	.row-check {
		width: 34rpx;
		height: 34rpx;
		border-radius: 999rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.25);
		background: rgba(255, 255, 255, 0.06);
		box-shadow: inset 0 0 0 6rpx transparent;
	}

	.row-check--active {
		border-color: rgba(244, 198, 255, 0.96);
		background: linear-gradient(135deg, #ff63c3, #a855f7);
		box-shadow:
			0 0 0 6rpx rgba(255, 255, 255, 0.05),
			0 12rpx 26rpx rgba(184, 95, 255, 0.34);
	}

	.footer {
		flex-shrink: 0;
		padding: 20rpx 30rpx calc(28rpx + env(safe-area-inset-bottom));
	}
</style>
