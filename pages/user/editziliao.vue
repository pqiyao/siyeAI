<template>
	<view class="page" :class="localeFontClass">
		<tavern-nav-bar :title="allText.我的页.编辑" mode="dark" @back="goBack">
			<template slot="right">
				<text class="save-txt" @tap="save">{{ allText.我的页.保存 }}</text>
			</template>
		</tavern-nav-bar>
		<image class="page-glow page-glow--top" src="/static/user/vipbgs.png" mode="widthFix"></image>
		<image class="page-glow page-glow--bottom" src="/static/user/moneybg.png" mode="widthFix"></image>

		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="intro-card">
				<view class="intro-copy">
					<text class="intro-title">{{ pageCopy.title }}</text>
					<text class="intro-subtitle">{{ pageCopy.subtitle }}</text>
				</view>
				<image class="intro-art" :src="displayAvatar" mode="aspectFill"></image>
			</view>

			<view class="avatar-card" @tap="upload">
				<text class="section-title">{{ pageCopy.avatarTitle }}</text>
				<text class="section-tip">{{ pageCopy.avatarTip }}</text>
				<view class="avatar-shell">
					<image class="avatar" :src="displayAvatar" mode="aspectFill"></image>
				</view>
				<text class="avatar-action">{{ pageCopy.avatarAction }}</text>
			</view>

			<view class="field-card">
				<text class="section-title">{{ allText.我的页.昵称 }}</text>
				<input class="field-input" v-model="nickname" :placeholder="t.旅人昵称" maxlength="32" />
			</view>

			<view class="field-card">
				<text class="section-title">{{ t.一句话介绍 }}</text>
				<textarea
					class="bio"
					v-model="bio"
					:placeholder="t.一句话介绍占位"
					maxlength="200"
				></textarea>
			</view>

			<view class="tips-card">
				<text class="tips-title">{{ pageCopy.noteTitle }}</text>
				<text class="foot-note">{{ t.Telegram身份说明 || pageCopy.noteBody }}</text>
			</view>

			<view class="submit-wrap">
				<button class="submit-btn" type="default" @tap="save">{{ allText.我的页.保存 }}</button>
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
		title: '管理你的角色身份',
		subtitle: '昵称、头像和简介会出现在订单、客服与个人资料中，不会改动当前聊天角色设定。',
		avatarTitle: '头像',
		avatarTip: '建议使用清晰头像，便于在订单和客服处理中快速识别你的账户。',
		avatarAction: '点击上传头像',
		noteTitle: '温馨提示',
		noteBody: '当前资料会在 H5 账号和已绑定身份中共用，用于识别你的账户，不影响聊天角色设定。'
	},
	'zh-hk': {
		title: '管理你的酒館身份',
		subtitle: '暱稱、頭像與簡介會出現在訂單、客服與個人資料中，不會改動目前聊天角色設定。',
		avatarTitle: '頭像',
		avatarTip: '建議使用清晰頭像，方便在訂單與客服處理時快速識別你的帳戶。',
		avatarAction: '點擊上傳頭像',
		noteTitle: '溫馨提示',
		noteBody: '目前資料會在 H5 帳號與已綁定身份中共用，用於識別你的帳戶，不影響聊天角色設定。'
	},
	en: {
		title: 'Manage Your Tavern Identity',
		subtitle: 'Your nickname, avatar, and bio appear in orders, support, and profile pages without changing your current roleplay settings.',
		avatarTitle: 'Avatar',
		avatarTip: 'A clear avatar makes your account easier to identify during support and order handling.',
		avatarAction: 'Tap to upload avatar',
		noteTitle: 'Note',
		noteBody: 'This profile is shared across your H5 account and linked identities for account recognition only.'
	}
};

export default {
	components: { TavernNavBar },
	data() {
		return {
			nickname: '',
			avatar: '',
			bio: '',
			birthday: '',
			height: '',
			weight: '',
			country: '',
			characters_id: '',
			relation_id: '',
			label_id: '',
			occupation_id: '',
			gender: ''
		};
	},
	computed: {
		t() {
			return this.allText.酒馆页 || {};
		},
		pageCopy() {
			const code = getLanguageCode();
			return COPY[code] || COPY['zh-cn'];
		},
		displayAvatar() {
			if (!this.avatar) return '/static/logo.png';
			if (this.avatar.indexOf('http') === 0) return this.avatar;
			return this.$getimgsrc(this.avatar);
		}
	},
	onLoad() {
		this.myuser();
	},
	methods: {
		goBack() {
			uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user/user' }) });
		},
		upload() {
			this.util.addImg().then((data) => {
				this.util.uploadFile(data).then((res) => {
					this.avatar = res.url;
				});
			});
		},
		myuser() {
			this.util
				.request('user/user_info', {
					token: uni.getStorageSync('user').token
				})
				.then((res) => {
					this.avatar = res.avatar || this.avatar;
					this.nickname = res.nickname || this.nickname;
					this.bio = res.bio || this.bio;
					this.birthday = res.birthday || '';
					this.height = res.height || '';
					this.weight = res.weight || '';
					this.country = res.country || '';
					this.characters_id = res.characters != null && res.characters !== '' ? res.characters : '';
					this.relation_id = res.relation != null && res.relation !== '' ? res.relation : '';
					this.label_id = res.label != null && res.label !== '' ? res.label : '';
					this.occupation_id = res.occupation != null && res.occupation !== '' ? res.occupation : '';
					this.gender = res.gender;
				});
		},
		save() {
			const name = (this.nickname || '').trim();
			if (!name) {
				this.util.showToast(this.allText.登录页.请输入用户名);
				return;
			}
			this.util
				.request('index/profile', {
					need_edit: 1,
					nickname: name,
					bio: this.bio || '',
					avatar: this.avatar,
					birthday: this.birthday || '',
					height: this.height || '',
					weight: this.weight || '',
					country: this.country || '',
					characters: this.characters_id,
					relation: this.relation_id,
					occupation: this.occupation_id,
					label: this.label_id,
					token: uni.getStorageSync('user').token,
					gender: this.gender
				})
				.then(() => {
					this.util.showToast(this.t.保存成功);
					const u = uni.getStorageSync('user') || {};
					u.nickname = name;
					if (this.avatar) u.avatar = this.avatar;
					uni.setStorageSync('user', u);
					this.$store.commit('setuser', u);
					setTimeout(() => uni.navigateBack(), 400);
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

.save-txt {
	padding-right: 12rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #c4b5fd;
}

.intro-card,
.avatar-card,
.field-card,
.tips-card {
	position: relative;
	border-radius: 28rpx;
	padding: 30rpx;
	margin-bottom: 24rpx;
	background: rgba(15, 23, 42, 0.72);
	border: 1rpx solid rgba(148, 163, 184, 0.18);
	box-shadow: 0 24rpx 70rpx rgba(15, 23, 42, 0.22);
	backdrop-filter: blur(16rpx);
}

.intro-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
}

.intro-copy {
	flex: 1;
}

.intro-title {
	display: block;
	font-size: 34rpx;
	font-weight: 700;
	color: #f8fafc;
	line-height: 1.35;
}

.intro-subtitle {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.78);
}

.intro-art {
	width: 132rpx;
	height: 132rpx;
	border-radius: 28rpx;
	border: 2rpx solid rgba(255, 255, 255, 0.18);
	box-shadow: 0 18rpx 42rpx rgba(79, 70, 229, 0.22);
}

.section-title {
	display: block;
	font-size: 28rpx;
	font-weight: 700;
	color: #f8fafc;
}

.section-tip {
	display: block;
	margin-top: 12rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: rgba(226, 232, 240, 0.72);
}

.avatar-card {
	text-align: center;
}

.avatar-shell {
	display: flex;
	justify-content: center;
	margin: 28rpx 0 16rpx;
}

.avatar {
	width: 180rpx;
	height: 180rpx;
	border-radius: 50%;
	border: 4rpx solid rgba(255, 255, 255, 0.88);
	box-shadow: 0 18rpx 48rpx rgba(99, 102, 241, 0.26);
}

.avatar-action {
	display: block;
	font-size: 24rpx;
	font-weight: 600;
	color: #c4b5fd;
}

.field-input,
.bio {
	width: 100%;
	margin-top: 18rpx;
	padding: 24rpx 24rpx;
	border-radius: 22rpx;
	background: rgba(15, 23, 42, 0.78);
	border: 1rpx solid rgba(148, 163, 184, 0.16);
	box-sizing: border-box;
	color: #f8fafc;
	font-size: 28rpx;
}

.field-input {
	height: 104rpx;
}

.bio {
	min-height: 220rpx;
	line-height: 1.7;
}

.tips-title {
	display: block;
	font-size: 26rpx;
	font-weight: 700;
	color: #f8fafc;
}

.foot-note {
	display: block;
	margin-top: 14rpx;
	font-size: 23rpx;
	line-height: 1.8;
	color: rgba(226, 232, 240, 0.74);
}

.submit-wrap {
	margin-top: 18rpx;
}

.submit-btn {
	height: 96rpx;
	border: none;
	border-radius: 999rpx;
	font-size: 30rpx;
	font-weight: 700;
	color: #fff;
	background: linear-gradient(135deg, #7c3aed 0%, #ec4899 100%);
	box-shadow: 0 22rpx 44rpx rgba(124, 58, 237, 0.28);
}
</style>
