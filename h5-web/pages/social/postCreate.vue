<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="发布动态" mode="dark" @back="goBack">
			<view slot="right" class="nav-action" :class="{ disabled: submitting || !canCreatePost }" @tap="submitPost">
				<text class="cuIcon-send nav-ico"></text>
				<text>{{ submitting ? '发布中' : '发布' }}</text>
			</view>
		</tavern-nav-bar>

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="editor-card">
				<view class="editor-head">
					<view class="editor-icon">
						<text class="cuIcon-edit"></text>
					</view>
					<view class="editor-copy">
						<text class="editor-title">写一条动态</text>
						<text class="editor-subtitle">文字和图片会展示在社区广场</text>
					</view>
				</view>
				<textarea
					v-model="content"
					class="content-input"
					maxlength="3000"
					placeholder="分享今天的想法、角色灵感或聊天片段..."
					placeholder-class="placeholder"
				></textarea>
				<view class="counter">{{ content.length }}/3000</view>
			</view>

			<view v-if="socialFeatureConfig.postImageEnabled" class="image-card">
				<view class="section-head">
					<text class="section-title">图片</text>
					<text class="section-sub">{{ images.length }}/9</text>
				</view>
				<view class="image-grid">
					<view v-for="(item, index) in images" :key="item.id" class="image-cell">
						<image class="picked-image" :src="item.localUrl" mode="aspectFill" @tap="preview(index)"></image>
						<view class="remove-image" @tap.stop="removeImage(index)">×</view>
						<view v-if="item.uploading" class="upload-mask">{{ item.progress }}%</view>
					</view>
					<view v-if="images.length < 9" class="add-cell" @tap="chooseImages">
						<text class="cuIcon-cameraadd add-plus"></text>
						<text class="add-text">添加图片</text>
					</view>
				</view>
			</view>

			<view class="tips-card">
				<text>{{ socialFeatureConfig.postImageEnabled ? '图片会先上传到四叶 AI，再随动态一起发布。第一版只支持文字和图片。' : '当前后台已关闭图片动态，只能发布文字内容。' }}</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const tavernApi = require('@/common/tavernApi.js');

export default {
	components: { TavernNavBar },
	data() {
		return {
			content: '',
			images: [],
			submitting: false,
			socialFeatureConfig: tavernApi.getSocialFeatureConfig()
		};
	},
	computed: {
		canCreatePost() {
			const config = this.socialFeatureConfig || {};
			return config.communityEnabled !== false && config.postCreateEnabled !== false && config.postPublishMode !== 'closed';
		}
	},
	onLoad() {
		if (!tavernApi.hasLoggedInUser()) {
			uni.redirectTo({ url: tavernApi.buildLoginUrl('/pages/social/postCreate') });
			return;
		}
		this.loadSocialFeatureConfig(true).then((config) => {
			if (config.communityEnabled === false || config.postCreateEnabled === false || config.postPublishMode === 'closed') {
				uni.showToast({ title: '当前已关闭动态发布', icon: 'none' });
				setTimeout(() => this.goBack(), 350);
			}
		});
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/social/feed' }) });
		},
		chooseImages() {
			if (this.socialFeatureConfig.postImageEnabled === false) {
				uni.showToast({ title: '当前已关闭图片动态', icon: 'none' });
				return;
			}
			if (this.images.length >= 9) return;
			const remain = 9 - this.images.length;
			uni.chooseImage({
				count: remain,
				sizeType: ['compressed'],
				sourceType: ['album', 'camera'],
				success: (res) => {
					const paths = res.tempFilePaths || [];
					paths.forEach((path) => {
						this.images.push({
							id: Date.now() + '_' + Math.random().toString(36).slice(2),
							localUrl: path,
							mediaKey: '',
							uploading: false,
							progress: 0
						});
					});
				}
			});
		},
		removeImage(index) {
			if (this.submitting) return;
			this.images.splice(index, 1);
		},
		preview(index) {
			const urls = this.images.map((item) => item.localUrl).filter(Boolean);
			if (!urls.length) return;
			uni.previewImage({ urls, current: urls[index] || urls[0] });
		},
		uploadOne(item) {
			if (item.mediaKey) return Promise.resolve(item.mediaKey);
			item.uploading = true;
			item.progress = 1;
			return tavernApi.uploadCommunityImage(item.localUrl, (progress) => {
				item.progress = progress;
			}).then((mediaKey) => {
				item.mediaKey = mediaKey;
				item.progress = 100;
				return mediaKey;
			}).finally(() => {
				item.uploading = false;
			});
		},
		submitPost() {
			if (this.submitting) return;
			if (!this.canCreatePost) {
				uni.showToast({ title: '当前已关闭动态发布', icon: 'none' });
				return;
			}
			if (this.socialFeatureConfig.postImageEnabled === false && this.images.length) {
				uni.showToast({ title: '当前已关闭图片动态', icon: 'none' });
				return;
			}
			const text = String(this.content || '').trim();
			if (!text && !this.images.length) {
				uni.showToast({ title: '内容或图片至少填写一项', icon: 'none' });
				return;
			}
			if (!tavernApi.hasLoggedInUser()) {
				uni.navigateTo({ url: tavernApi.buildLoginUrl('/pages/social/postCreate') });
				return;
			}
			this.submitting = true;
			Promise.all(this.images.map(this.uploadOne))
				.then((mediaList) => tavernApi.createCommunityPost({
					content: text,
					mediaList: mediaList.filter(Boolean)
				}))
				.then((post) => {
					try {
						uni.setStorageSync('social_feed_refresh_needed', '1');
					} catch (e) {}
					uni.showToast({ title: '发布成功', icon: 'none' });
					setTimeout(() => {
						const postId = post && post.postId;
						if (postId) {
							uni.redirectTo({ url: '/pages/social/detail?id=' + encodeURIComponent(postId) });
						} else {
							uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/social/feed' }) });
						}
					}, 350);
				})
				.catch((error) => {
					uni.showToast({ title: (error && error.message) || '发布失败', icon: 'none' });
				})
				.finally(() => {
					this.submitting = false;
				});
		},
		loadSocialFeatureConfig(force) {
			return tavernApi.fetchSocialFeatureConfig(!!force)
				.then((config) => {
					this.socialFeatureConfig = config || tavernApi.getSocialFeatureConfig();
					if (this.socialFeatureConfig.postImageEnabled === false) {
						this.images = [];
					}
					return this.socialFeatureConfig;
				})
				.catch(() => {
					this.socialFeatureConfig = tavernApi.getSocialFeatureConfig();
					return this.socialFeatureConfig;
				});
		}
	}
};
</script>

<style lang="scss" scoped>
.page {
	position: relative;
	height: 100vh;
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	background: #ecf8fb;
}

.app-page-bg {
	position: fixed;
	inset: 0;
	width: 100%;
	height: 100%;
	opacity: 0.5;
	z-index: 0;
}

.nav-action {
	padding: 12rpx 18rpx;
	border-radius: 999rpx;
	background: #4f93a3;
	color: #fff;
	font-size: 24rpx;
	font-weight: 700;
}

.nav-action.disabled {
	opacity: 0.55;
}

.scroll {
	position: relative;
	z-index: 1;
	flex: 1;
	min-height: 0;
	padding: 24rpx;
	box-sizing: border-box;
}

.editor-card,
.image-card,
.tips-card {
	border-radius: 8rpx;
	background: rgba(255, 255, 255, 0.78);
	border: 1rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 14rpx 30rpx rgba(38, 57, 77, 0.08);
}

.editor-card {
	padding: 24rpx;
}

.content-input {
	width: 100%;
	min-height: 300rpx;
	color: #2c405a;
	font-size: 30rpx;
	line-height: 1.6;
}

.placeholder {
	color: #95a6b4;
}

.counter {
	margin-top: 12rpx;
	text-align: right;
	color: #95a6b4;
	font-size: 22rpx;
}

.image-card {
	margin-top: 20rpx;
	padding: 24rpx;
}

.section-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 18rpx;
}

.section-title {
	color: #26394d;
	font-size: 28rpx;
	font-weight: 700;
}

.section-sub {
	color: #7891a4;
	font-size: 24rpx;
}

.image-grid {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 12rpx;
}

.image-cell,
.add-cell {
	position: relative;
	height: 210rpx;
	border-radius: 8rpx;
	overflow: hidden;
}

.picked-image {
	width: 100%;
	height: 100%;
	background: rgba(79, 147, 163, 0.1);
}

.remove-image {
	position: absolute;
	top: 8rpx;
	right: 8rpx;
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(38, 57, 77, 0.7);
	color: #fff;
	font-size: 28rpx;
	line-height: 1;
}

.upload-mask {
	position: absolute;
	inset: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(38, 57, 77, 0.46);
	color: #fff;
	font-size: 26rpx;
	font-weight: 700;
}

.add-cell {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	border: 2rpx dashed rgba(79, 147, 163, 0.34);
	background: rgba(255, 255, 255, 0.48);
	color: #4f93a3;
}

.add-plus {
	font-size: 52rpx;
	line-height: 1;
}

.add-text {
	font-size: 22rpx;
}

.tips-card {
	margin-top: 20rpx;
	padding: 22rpx 24rpx;
	color: #6f7b88;
	font-size: 24rpx;
	line-height: 1.6;
}

.bottom-space {
	height: 140rpx;
}

.page {
	background:
		linear-gradient(180deg, #eef7f1 0%, #f7f7f2 42%, #f4f1ea 100%);
}

.app-page-bg {
	opacity: 0.06;
	filter: grayscale(1);
}

.nav-action {
	min-width: 124rpx;
	height: 54rpx;
	padding: 0 16rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	background: #2e6b4d;
	color: #ffffff;
	border: 1rpx solid rgba(255, 255, 255, 0.6);
	font-weight: 900;
	box-shadow: 0 10rpx 20rpx rgba(46, 107, 77, 0.16);
	box-sizing: border-box;
}

.nav-ico {
	font-size: 24rpx;
	line-height: 1;
}

.editor-card,
.image-card,
.tips-card {
	background: rgba(255, 255, 255, 0.94);
	border-color: rgba(46, 107, 77, 0.08);
	box-shadow: 0 14rpx 30rpx rgba(34, 52, 42, 0.08);
}

.editor-card {
	overflow: hidden;
}

.editor-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 20rpx;
	padding-bottom: 20rpx;
	border-bottom: 1rpx solid rgba(46, 107, 77, 0.08);
}

.editor-icon {
	width: 58rpx;
	height: 58rpx;
	border-radius: 8rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(46, 107, 77, 0.1);
	color: #2e6b4d;
	font-size: 32rpx;
	flex-shrink: 0;
}

.editor-copy {
	flex: 1;
	min-width: 0;
}

.editor-title,
.editor-subtitle {
	display: block;
}

.editor-title {
	color: #1f2933;
	font-size: 30rpx;
	font-weight: 900;
	line-height: 1.25;
}

.editor-subtitle {
	margin-top: 6rpx;
	color: #65736a;
	font-size: 23rpx;
	line-height: 1.35;
}

.content-input {
	color: #263238;
}

.placeholder {
	color: #8a958e;
}

.counter,
.section-sub,
.tips-card {
	color: #7b877f;
}

.section-title {
	color: #1f2933;
	font-weight: 900;
}

.image-grid {
	gap: 10rpx;
}

.image-cell,
.add-cell {
	background: #f1f5f0;
}

.picked-image {
	background: rgba(46, 107, 77, 0.08);
}

.remove-image {
	background: rgba(31, 41, 51, 0.74);
	font-weight: 900;
}

.upload-mask {
	background: rgba(31, 41, 51, 0.5);
}

.add-cell {
	border-color: rgba(46, 107, 77, 0.24);
	background:
		linear-gradient(135deg, rgba(238, 247, 241, 0.9) 0%, rgba(255, 244, 235, 0.9) 100%);
	color: #2e6b4d;
}

.add-plus {
	font-size: 48rpx;
}

.add-text {
	font-weight: 900;
}

.tips-card {
	background: rgba(46, 107, 77, 0.08);
	border-color: rgba(46, 107, 77, 0.08);
	box-shadow: none;
}

/* Composer page: make it feel like a plain editor, not stacked cards. */
.scroll {
	padding: 20rpx 30rpx 0;
}

.editor-card,
.image-card,
.tips-card {
	border-radius: 0;
	background: transparent;
	border: 0;
	box-shadow: none;
}

.editor-card,
.image-card {
	padding: 22rpx 0;
}

.image-card {
	margin-top: 0;
	border-top: 1rpx solid rgba(46, 107, 77, 0.1);
}

.editor-head {
	display: none;
}

.content-input {
	min-height: 360rpx;
	font-size: 32rpx;
}

.counter {
	margin-top: 8rpx;
}

.image-grid {
	gap: 8rpx;
}

.image-cell,
.add-cell {
	height: 214rpx;
	border-radius: 16rpx;
}

.add-cell {
	background: transparent;
}

.tips-card {
	margin-top: 4rpx;
	padding: 0;
	background: transparent;
	font-size: 23rpx;
}
</style>
