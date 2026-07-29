<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="pageTitle" mode="dark" @back="goBack" />
		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view v-if="loading" class="empty empty--loading">
				<view class="loading-line"></view>
				<view class="loading-line"></view>
				<view class="loading-line"></view>
			</view>
			<view v-else-if="!list.length" class="empty">
				<text class="empty-txt">{{ loadFailed ? loadErrorText : emptyText }}</text>
				<view v-if="loadFailed" class="empty-retry" @tap="load">{{ retryText }}</view>
			</view>
			<view
				v-for="item in list"
				:key="item.id"
				class="card"
			>
				<view
					v-if="item.imagePreview"
					class="card-cover-wrap"
					role="button"
					aria-label="查看活动图片"
					@tap.stop="previewAdImage(item)"
				>
					<image
						class="card-cover"
						:src="item.imagePreview"
						mode="aspectFill"
						lazy-load
					></image>
					<view class="cover-preview-icon">
						<text class="cuIcon-search"></text>
					</view>
				</view>
				<view class="card-hd">
					<text class="tag">{{ tagText }}</text>
					<text class="time">{{ item.timeText }}</text>
				</view>
				<text class="card-title">{{ item.title || untitledText }}</text>
				<text v-if="item.content" class="card-body">{{ item.content }}</text>
				<view
					v-if="item.linkUrl"
					class="card-link"
					hover-class="card-link--pressed"
					role="button"
					@tap.stop="openAd(item)"
				>
					<text>{{ openLinkText }}</text>
					<text class="cuIcon-right card-link-icon"></text>
				</view>
			</view>
			<view class="pad"></view>
		</scroll-view>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	const tavernApi = require('@/common/tavernApi.js');
	const tavernErrors = require('@/common/tavernErrors.js');
	const tavernInboxBadge = require('@/common/tavernInboxBadge.js');

	const TAB_PAGES = [
		'/pages/index/index',
		'/pages/tavern/tavern',
		'/pages/tavern/tavernInbox',
		'/pages/user/user'
	];

	export default {
		components: { TavernNavBar },
		data() {
			return {
				list: [],
				loading: false,
				loadFailed: false,
				loadErrorText: ''
			};
		},
		computed: {
			t() {
				return this.allText['\u9152\u9986\u9875'] || {};
			},
			pageTitle() {
				return this.t['\u6d3b\u52a8\u63a8\u8350'] || '\u6d3b\u52a8\u63a8\u8350';
			},
			emptyText() {
				return this.t['\u6682\u65e0\u6d3b\u52a8\u63a8\u8350'] || '\u6682\u65e0\u6d3b\u52a8\u63a8\u8350';
			},
			retryText() {
				return this.t['\u53d1\u73b0\u70b9\u51fb\u91cd\u8bd5'] || '\u70b9\u51fb\u91cd\u8bd5';
			},
			tagText() {
				return this.t['\u6d3b\u52a8\u6807\u7b7e'] || '\u6d3b\u52a8';
			},
			untitledText() {
				return this.t['\u672a\u547d\u540d\u6d3b\u52a8'] || '\u672a\u547d\u540d\u6d3b\u52a8';
			},
			openLinkText() {
				return this.t['\u67e5\u770b\u8be6\u60c5'] || '\u67e5\u770b\u8be6\u60c5';
			}
		},
		onShow() {
			this.load();
		},
		methods: {
			goBack() {
				this.util.safeNavigateBack('/pages/tavern/tavernInbox');
			},
			formatTime(value) {
				if (!value) return '';
				const d = new Date(value);
				if (isNaN(d.getTime())) return '';
				const pad = (n) => String(n).padStart(2, '0');
				return (
					d.getFullYear() +
					'-' +
					pad(d.getMonth() + 1) +
					'-' +
					pad(d.getDate()) +
					' ' +
					pad(d.getHours()) +
					':' +
					pad(d.getMinutes())
				);
			},
			load() {
				this.loading = true;
				this.loadFailed = false;
				this.loadErrorText = '';
				if (!tavernApi.jgEnabled()) {
					this.list = [];
					this.loading = false;
					return;
				}
				tavernApi
					.fetchInboxAds(50)
					.then((rows) => {
						this.list = (Array.isArray(rows) ? rows : []).map((item) => ({
							...item,
							imagePreview: item.imageUrl
								? tavernApi.resolveJgAssetUrl(item.imageUrl) || ''
								: '',
							timeText: this.formatTime(item.updatedAt || item.createdAt)
						}));
						return this.markAdsRead();
					})
					.catch((e) => {
						this.list = [];
						this.loadFailed = true;
						this.loadErrorText = tavernErrors.getTavernErrorMessage(
							e,
							this.t['\u52a0\u8f7d\u5931\u8d25'] || '\u52a0\u8f7d\u5931\u8d25'
						);
					})
					.finally(() => {
						this.loading = false;
					});
			},
			markAdsRead() {
				if (typeof tavernApi.markInboxAdsReadAll !== 'function') {
					return tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi);
				}
				return tavernApi
					.markInboxAdsReadAll(tavernApi.getClientUid())
					.then(() =>
						tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi, { adUnread: 0 })
					)
					.catch(() => tavernInboxBadge.refreshCombinedInboxBadge(this, tavernApi));
			},
			previewAdImage(item) {
				const current = item && item.imagePreview ? String(item.imagePreview).trim() : '';
				if (!current) return;
				const urls = this.list
					.map((entry) => (entry && entry.imagePreview ? String(entry.imagePreview).trim() : ''))
					.filter(Boolean);
				uni.previewImage({
					current,
					urls: urls.length ? urls : [current]
				});
			},
			copyExternalLink(link) {
				uni.setClipboardData({
					data: link,
					success: () => {
						uni.showToast({
							title:
								this.t['\u8bf7\u5728\u6d4f\u89c8\u5668\u6253\u5f00'] ||
								'\u94fe\u63a5\u5df2\u590d\u5236\uff0c\u8bf7\u5728\u6d4f\u89c8\u5668\u4e2d\u7c98\u8d34\u6253\u5f00',
							icon: 'none'
						});
					},
					fail: () => {
						uni.showToast({
							title: this.t['\u8df3\u8f6c\u5931\u8d25'] || '\u65e0\u6cd5\u6253\u5f00\u94fe\u63a5',
							icon: 'none'
						});
					}
				});
			},
			openAd(item) {
				if (!item) return;
				const link = item.linkUrl != null ? String(item.linkUrl).trim() : '';
				if (!link) return;
				if (/^https?:\/\//i.test(link)) {
					/* #ifdef APP-PLUS */
					try {
						if (
							typeof plus !== 'undefined' &&
							plus.runtime &&
							typeof plus.runtime.openURL === 'function'
						) {
							plus.runtime.openURL(link, () => this.copyExternalLink(link));
							return;
						}
					} catch (e) {
						this.copyExternalLink(link);
						return;
					}
					/* #endif */
					/* #ifdef H5 */
					if (typeof window !== 'undefined' && window.open) {
						window.open(link, '_blank');
						return;
					}
					/* #endif */
					this.copyExternalLink(link);
					return;
				}
				if (link.charAt(0) !== '/') {
					return;
				}
				const pathOnly = link.split('?')[0];
				if (TAB_PAGES.indexOf(pathOnly) >= 0) {
					uni.switchTab({ url: pathOnly });
					return;
				}
				uni.navigateTo({
					url: link,
					fail: () => {
						uni.showToast({
							title: this.t['\u8df3\u8f6c\u5931\u8d25'] || '\u65e0\u6cd5\u6253\u5f00\u94fe\u63a5',
							icon: 'none'
						});
					}
				});
			}
		}
	};
</script>

<style scoped lang="scss">
	$page:
		radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
		radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
		linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
	$card: rgba(255, 255, 255, 0.88);
	$text: #244b66;
	$muted: #687f92;

	.page {
		height: 100vh;
		display: flex;
		flex-direction: column;
		background: $page;
	}

	.scroll {
		flex: 1;
		height: 0;
		padding: 20rpx 28rpx 0;
		box-sizing: border-box;
	}

	.empty {
		padding: 120rpx 32rpx;
		text-align: center;
	}

	.empty--loading {
		display: flex;
		flex-direction: column;
		gap: 22rpx;
	}

	.loading-line {
		height: 26rpx;
		border-radius: 999rpx;
		background: linear-gradient(
			90deg,
			rgba(255, 255, 255, 0.04),
			rgba(148, 163, 184, 0.16),
			rgba(255, 255, 255, 0.04)
		);
	}

	.loading-line:nth-child(2) {
		width: 78%;
		margin: 0 auto;
	}

	.loading-line:nth-child(3) {
		width: 56%;
		margin: 0 auto;
	}

	.empty-txt {
		font-size: 28rpx;
		color: $muted;
		line-height: 1.5;
	}

	.empty-retry {
		margin-top: 28rpx;
		font-size: 28rpx;
		color: #247494;
		font-weight: 600;
	}

	.card {
		background: $card;
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 24rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.9);
		box-shadow: 0 18rpx 40rpx rgba(67, 112, 142, 0.11);
		overflow: hidden;
	}

	.card-cover-wrap {
		position: relative;
		width: 100%;
		height: 280rpx;
		border-radius: 16rpx;
		margin-bottom: 20rpx;
		overflow: hidden;
		background: #dceefa;
		box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.56);
	}

	.card-cover {
		width: 100%;
		height: 100%;
		background: #dceefa;
		display: block;
		transition: transform 0.18s ease, opacity 0.18s ease;
	}

	.card-cover-wrap:active .card-cover {
		transform: scale(0.99);
		opacity: 0.94;
	}

	.cover-preview-icon {
		position: absolute;
		right: 14rpx;
		bottom: 14rpx;
		width: 52rpx;
		height: 52rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		border-radius: 18rpx;
		background: rgba(29, 71, 91, 0.62);
		box-shadow: 0 8rpx 18rpx rgba(29, 71, 91, 0.18), inset 0 1rpx 0 rgba(255, 255, 255, 0.28);
		color: #ffffff;
		font-size: 25rpx;
		pointer-events: none;
	}

	.card-hd {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}

	.tag {
		font-size: 20rpx;
		font-weight: 700;
		color: #247494;
		background: rgba(220, 238, 250, 0.95);
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
	}

	.time {
		font-size: 22rpx;
		color: $muted;
	}

	.card-title {
		display: block;
		font-size: 32rpx;
		font-weight: 800;
		color: $text;
		line-height: 1.4;
	}

	.card-body {
		display: block;
		margin-top: 14rpx;
		font-size: 26rpx;
		color: $muted;
		line-height: 1.55;
		white-space: pre-wrap;
	}

	.card-link {
		width: fit-content;
		min-height: 58rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		margin-top: 18rpx;
		margin-left: auto;
		padding: 0 18rpx 0 20rpx;
		box-sizing: border-box;
		border: 1rpx solid rgba(36, 116, 148, 0.16);
		border-radius: 18rpx;
		background: rgba(229, 244, 249, 0.82);
		box-shadow: 0 7rpx 16rpx rgba(67, 112, 142, 0.08), inset 0 1rpx 0 rgba(255, 255, 255, 0.86);
		font-size: 24rpx;
		color: #247494;
		font-weight: 700;
		transition: transform 0.16s ease, opacity 0.16s ease;
	}

	.card-link--pressed {
		transform: translateY(1rpx);
		opacity: 0.82;
	}

	.card-link-icon {
		font-size: 22rpx;
	}

	.pad {
		height: calc(40rpx + env(safe-area-inset-bottom));
	}
</style>
