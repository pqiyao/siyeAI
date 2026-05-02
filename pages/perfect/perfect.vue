<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="copy.title" mode="dark" @back="goBack" />
		<image class="page-glow page-glow--top" src="/static/user/vipbgs.png" mode="widthFix"></image>
		<image class="page-glow page-glow--bottom" src="/static/user/moneybg.png" mode="widthFix"></image>

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="intro-card">
				<view class="intro-copy">
					<text class="intro-title">{{ copy.heading }}</text>
					<text class="intro-subtitle">{{ copy.subtitle }}</text>
				</view>
				<image class="intro-art" src="/static/logo.png" mode="aspectFill"></image>
			</view>

			<view class="avatar-card">
				<text class="section-title">{{ copy.avatarTitle }}</text>
				<text class="section-tip">{{ copy.avatarTip }}</text>
				<view class="avatar-preview-wrap">
					<image class="avatar-preview" :src="displayAvatar" mode="aspectFill"></image>
				</view>
				<view class="upload-shell">
					<u-upload
						:action="action"
						:file-list="fileList"
						:show-upload-list="false"
						max-count="1"
						name="file"
						:form-data="formData"
						@on-uploaded="onUploaded"
					></u-upload>
				</view>
			</view>

			<view class="field-card">
				<text class="section-title">{{ copy.nicknameTitle }}</text>
				<input
					class="nickname-input"
					v-model="nickname"
					:placeholder="copy.nicknamePlaceholder"
					maxlength="32"
				/>
			</view>

			<view class="tips-card">
				<text class="tips-title">{{ copy.noteTitle }}</text>
				<text class="tips-text">{{ copy.noteBody }}</text>
			</view>

			<view class="btn-wrap">
				<button class="btn-primary" type="default" @tap="submit">{{ copy.submit }}</button>
			</view>
			<u-gap height="48"></u-gap>
		</scroll-view>
	</view>
</template>

<script>
import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';
const { getLanguageCode } = require('@/common/tavernUiI18n.js');

const COPY = {
	'zh-cn': {
		title: '完善资料',
		heading: '给你的角色身份补上一点辨识度',
		subtitle: '上传头像、设置昵称后，你的账号会更容易在订单和客服处理中被识别。',
		avatarTitle: '头像',
		avatarTip: '建议上传清晰头像，后续可在个人资料里继续修改。',
		nicknameTitle: '旅人昵称',
		nicknamePlaceholder: '请输入昵称',
		noteTitle: '提示',
		noteBody: '这里保存的是 H5 账号资料，不会影响你当前的聊天角色设定。',
		submit: '进入角色广场',
		nicknameRequired: '请输入昵称',
		avatarRequired: '请先上传头像'
	},
	'zh-hk': {
		title: '完善資料',
		heading: '替你的角色身份補上一點辨識度',
		subtitle: '上傳頭像、設定暱稱後，之後的訂單與客服處理會更容易辨認。',
		avatarTitle: '頭像',
		avatarTip: '建議上傳清晰頭像，之後仍可在個人資料內修改。',
		nicknameTitle: '旅人暱稱',
		nicknamePlaceholder: '請輸入暱稱',
		noteTitle: '提示',
		noteBody: '這裡保存的是 H5 帳號資料，不會改動你目前的聊天角色設定。',
		submit: '進入酒館',
		nicknameRequired: '請輸入暱稱',
		avatarRequired: '請先上傳頭像'
	},
	en: {
		title: 'Complete Profile',
		heading: 'Make your tavern identity feel more like yours',
		subtitle: 'Add an avatar and nickname so orders and support requests can recognize your account more easily.',
		avatarTitle: 'Avatar',
		avatarTip: 'A clear avatar helps. You can change it again later in profile settings.',
		nicknameTitle: 'Display Name',
		nicknamePlaceholder: 'Enter your nickname',
		noteTitle: 'Note',
		noteBody: 'This only updates your H5 account profile and does not change your current roleplay character settings.',
		submit: 'Enter Tavern',
		nicknameRequired: 'Please enter a nickname',
		avatarRequired: 'Please upload an avatar first'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			nickname: '',
			imgbox: '',
			fileList: [],
			action: this.util.api.path + 'common/upload',
			formData: {
				token: (uni.getStorageSync('user') || {}).token || ''
			},
			label: [],
			occupation: [],
			relation: []
		};
	},
	computed: {
		copy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		displayAvatar() {
			if (!this.imgbox) {
				return '/static/logo.png';
			}
			if (this.imgbox.indexOf('http') === 0) {
				return this.imgbox;
			}
			return this.$getimgsrc(this.imgbox);
		}
	},
	onShow() {
		this.loadSelectDefaults();
		const u = uni.getStorageSync('user');
		if (u && u.nickname && !this.nickname) this.nickname = u.nickname;
		if (u && u.avatar) this.imgbox = u.avatar;
		this.formData = {
			token: (u || {}).token || ''
		};
	},
	methods: {
		goBack() {
			this.util.safeNavigateBack('/pages/login/login');
		},
		loadSelectDefaults() {
			this.util.request('index/get_select', {}).then((res) => {
				this.label = res.label || [];
				this.occupation = res.occupation || [];
				this.relation = res.relation || [];
			});
		},
		onUploaded(res) {
			const first = (res || [])[0];
			if (!first) return;
			const response = first.response || {};
			let url = '';
			if (typeof response === 'string') {
				try {
					const parsed = JSON.parse(response);
					url = parsed && parsed.data ? parsed.data.url : '';
				} catch (e) {}
			} else if (response && response.data && response.data.url) {
				url = response.data.url;
			}
			if (!url && first.url && first.progress === 100) {
				url = first.url;
			}
			if (url) {
				this.imgbox = url;
				this.fileList = [{ url: this.$getimgsrc(url) }];
			}
		},
		submit() {
			const name = (this.nickname || '').trim();
			if (!name) {
				this.util.showToast(this.copy.nicknameRequired);
				return;
			}
			if (!this.imgbox) {
				this.util.showToast(this.copy.avatarRequired);
				return;
			}
			const charactersId = this.label[0] ? this.label[0].id : 0;
			const occupationId = this.occupation[0] ? this.occupation[0].id : 0;
			const relationIds = this.relation[0] ? [this.relation[0].id] : [];
			this.util
				.request(
					'index/profile',
					{
						need_edit: 1,
						nickname: name,
						avatar: this.imgbox,
						bio: '',
						gender: 1,
						birthday: '',
						height: '',
						weight: '',
						country: '',
						characters: charactersId,
						relation: relationIds,
						occupation: occupationId,
						label: [],
						token: uni.getStorageSync('user').token
					},
					'POST'
				)
				.then((res) => {
					if (res.code == 4003) {
						uni.hideLoading();
						uni.clearStorageSync();
						this.util.showToast(res.msg);
						this.$store.commit('userout');
						return;
					}
					const u = uni.getStorageSync('user') || {};
					u.nickname = name;
					u.avatar = this.imgbox;
					uni.setStorageSync('user', u);
					setTimeout(() => {
						uni.reLaunch({ url: '/pages/index/index' });
					}, 400);
				});
		}
	}
};
</script>

<style scoped lang="scss">
.page {
	position: relative;
	min-height: 100vh;
	background:
		radial-gradient(circle at top left, rgba(99, 102, 241, 0.18), transparent 34%),
		radial-gradient(circle at bottom right, rgba(236, 72, 153, 0.14), transparent 30%),
		$tavern-page-bg;
	overflow: hidden;
}

.page-glow {
	position: absolute;
	pointer-events: none;
	opacity: 0.42;
}

.page-glow--top {
	top: 40rpx;
	left: -120rpx;
	width: 760rpx;
}

.page-glow--bottom {
	right: -150rpx;
	bottom: 60rpx;
	width: 620rpx;
	opacity: 0.26;
}

.scroll {
	position: relative;
	z-index: 1;
	height: calc(100vh - 88rpx);
	padding: 28rpx 28rpx 48rpx;
	box-sizing: border-box;
}

.intro-card,
.avatar-card,
.field-card,
.tips-card {
	border-radius: 28rpx;
	background: rgba(15, 23, 42, 0.74);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	box-shadow: 0 24rpx 64rpx rgba(15, 23, 42, 0.28);
	backdrop-filter: blur(18rpx);
	margin-bottom: 20rpx;
}

.intro-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 24rpx;
	padding: 30rpx;
}

.intro-copy {
	flex: 1;
}

.intro-title {
	display: block;
	font-size: 34rpx;
	font-weight: 700;
	line-height: 1.45;
	color: #f8fafc;
}

.intro-subtitle {
	display: block;
	margin-top: 14rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.78);
}

.intro-art {
	width: 124rpx;
	height: 124rpx;
	border-radius: 30rpx;
	flex-shrink: 0;
}

.avatar-card,
.field-card,
.tips-card {
	padding: 28rpx;
}

.section-title,
.tips-title {
	display: block;
	font-size: 28rpx;
	font-weight: 700;
	color: #f8fafc;
}

.section-tip,
.tips-text {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.74);
}

.avatar-preview-wrap {
	display: flex;
	justify-content: center;
	margin: 28rpx 0 22rpx;
}

.avatar-preview {
	width: 188rpx;
	height: 188rpx;
	border-radius: 50%;
	border: 6rpx solid rgba(255, 255, 255, 0.82);
	box-shadow: 0 22rpx 54rpx rgba(15, 23, 42, 0.4);
	background: rgba(255, 255, 255, 0.08);
}

.upload-shell {
	padding: 18rpx;
	border-radius: 24rpx;
	background: rgba(255, 255, 255, 0.04);
	border: 1rpx dashed rgba(196, 181, 253, 0.36);
}

.nickname-input {
	width: 100%;
	height: 82rpx;
	margin-top: 18rpx;
	padding: 0 20rpx;
	box-sizing: border-box;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.04);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	font-size: 30rpx;
	color: #f8fafc;
}

.btn-wrap {
	margin-top: 28rpx;
}

.btn-primary {
	width: 100%;
	height: 96rpx;
	line-height: 96rpx;
	border: none;
	border-radius: 48rpx;
	font-size: 30rpx;
	font-weight: 600;
	color: #fff;
	background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 52%, #ec4899 100%);
	box-shadow: 0 20rpx 40rpx rgba(79, 70, 229, 0.28);
}

.btn-primary::after {
	border: none;
}
</style>
