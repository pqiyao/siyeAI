<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar :title="allText.我的页.编辑" mode="dark" @back="goBack">
			<template slot="right">
				<text class="save-txt" @tap="save">{{ allText.我的页.保存 }}</text>
			</template>
		</tavern-nav-bar>
		<scroll-view scroll-y class="scroll" :show-scrollbar="false">
			<view class="avatar-card" @tap="upload">
				<view class="avatar-editor">
					<view class="avatar-shell">
						<image class="avatar" :src="displayAvatar" mode="aspectFill"></image>
						<view class="avatar-camera"><u-icon name="camera-fill" color="#ffffff" size="22"></u-icon></view>
					</view>
				</view>
				<view class="avatar-card-copy">
					<text class="section-title">{{ pageCopy.avatarTitle }}</text>
					<text class="section-tip">{{ pageCopy.avatarTip }}</text>
					<view class="avatar-action">
						<u-icon name="photo-fill" color="#4f93a3" size="22"></u-icon>
						<text>{{ pageCopy.avatarAction }}</text>
					</view>
				</view>
			</view>

			<view class="profile-form">
				<view class="form-section">
					<view class="field-heading">
						<view class="field-heading-icon"><u-icon name="account-fill" color="#4f93a3" size="24"></u-icon></view>
						<text class="section-title">{{ allText.我的页.昵称 }}</text>
					</view>
					<input class="field-input" v-model="nickname" :placeholder="t.旅人昵称" maxlength="32" />
				</view>
				<view class="form-divider"></view>
				<view class="form-section">
					<view class="field-heading">
						<view class="field-heading-icon field-heading-icon--pink"><u-icon name="edit-pen-fill" color="#b65f83" size="24"></u-icon></view>
						<text class="section-title">{{ t.一句话介绍 }}</text>
					</view>
					<textarea
						class="bio"
						v-model="bio"
						:placeholder="t.一句话介绍占位"
						maxlength="200"
					></textarea>
				</view>
			</view>

			<view class="tips-strip">
				<view class="tips-heading">
					<u-icon name="info-circle-fill" color="#4f93a3" size="24"></u-icon>
					<text class="tips-title">{{ pageCopy.noteTitle }}</text>
				</view>
				<text class="foot-note">{{ t.Telegram身份说明 || pageCopy.noteBody }}</text>
			</view>

			<view class="submit-wrap">
				<button class="submit-btn" type="default" @tap="save"><u-icon name="checkmark-circle-fill" color="#ffffff" size="25"></u-icon><text>{{ allText.我的页.保存 }}</text></button>
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
		title: '管理你的酒馆身份',
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
		},
		ko: {
			title: '내 프로필 관리',
			subtitle: '닉네임, 아바타와 소개는 주문, 고객지원 및 프로필에 표시되며 현재 롤플레이 설정은 변경하지 않습니다.',
			avatarTitle: '아바타',
			avatarTip: '선명한 아바타를 사용하면 주문과 고객지원에서 계정을 쉽게 확인할 수 있습니다.',
			avatarAction: '눌러서 아바타 업로드',
			noteTitle: '안내',
			noteBody: '이 프로필은 계정 확인을 위해 H5 계정과 연결된 로그인에서 함께 사용됩니다.'
		},
		ja: {
			title: 'プロフィール管理',
			subtitle: 'ニックネーム、アバター、紹介文は注文・サポート・プロフィールに表示され、現在のロールプレイ設定は変更しません。',
			avatarTitle: 'アバター',
			avatarTip: '鮮明なアバターを使用すると、注文やサポートでアカウントを確認しやすくなります。',
			avatarAction: 'タップしてアバターをアップロード',
			noteTitle: 'ご案内',
			noteBody: 'このプロフィールはアカウント確認のため、H5アカウントと連携済みログインで共有されます。'
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
				}).catch(() => {});
			}).catch(() => {});
		},
		myuser() {
			this.util
				.request('user/user_info', {
					token: this.util.getStoredToken()
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
					token: this.util.getStoredToken(),
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
	background: transparent;
	overflow: hidden;
	color: #203846;
}

.scroll {
	position: relative;
	z-index: 1;
	height: calc(100vh - 88rpx);
	padding: 28rpx 24rpx calc(44rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.save-txt {
	padding-right: 12rpx;
	font-size: 26rpx;
	font-weight: 700;
	color: #236f82;
}

.avatar-card,
.profile-form {
	position: relative;
	border: 1rpx solid rgba(255, 255, 255, 0.84);
	background: linear-gradient(145deg, rgba(255, 255, 255, 0.72) 0%, rgba(243, 251, 253, 0.52) 100%);
	box-shadow: 0 22rpx 52rpx rgba(44, 83, 103, 0.12), inset 0 1rpx 0 rgba(255, 255, 255, 0.88);
	backdrop-filter: blur(22rpx);
	-webkit-backdrop-filter: blur(22rpx);
	box-sizing: border-box;
}

.avatar-card {
	display: flex;
	align-items: center;
	gap: 30rpx;
	margin-bottom: 22rpx;
	padding: 32rpx;
	border-radius: 36rpx;
	background: linear-gradient(135deg, rgba(255, 255, 255, 0.78) 0%, rgba(226, 245, 249, 0.58) 62%, rgba(255, 237, 246, 0.5) 100%);
}

.section-title,
.tips-title {
	display: block;
	color: #203846;
	font-weight: 800;
}

.section-tip,
.foot-note {
	display: block;
	color: #5f7280;
	line-height: 1.7;
}

.section-title {
	font-size: 29rpx;
}

.section-tip {
	margin-top: 10rpx;
	font-size: 24rpx;
}

.avatar-card-copy {
	flex: 1;
	min-width: 0;
}

.avatar-editor {
	flex-shrink: 0;
}

.avatar-shell {
	position: relative;
	display: flex;
	justify-content: center;
}

.avatar {
	width: 164rpx;
	height: 164rpx;
	border-radius: 50%;
	border: 5rpx solid rgba(255, 255, 255, 0.92);
	box-shadow: 0 20rpx 46rpx rgba(36, 70, 88, 0.16);
}

.avatar-camera {
	position: absolute;
	right: 2rpx;
	bottom: 6rpx;
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #4f93a3;
	border: 4rpx solid rgba(255, 255, 255, 0.94);
	box-shadow: 0 10rpx 22rpx rgba(79, 147, 163, 0.24);
}

.avatar-action {
	display: inline-flex;
	align-items: center;
	gap: 7rpx;
	margin-top: 18rpx;
	padding: 10rpx 16rpx;
	border-radius: 999rpx;
	font-size: 22rpx;
	font-weight: 700;
	color: #4f7f8e;
	background: rgba(255, 255, 255, 0.54);
	border: 1rpx solid rgba(79, 147, 163, 0.15);
}

.profile-form {
	overflow: hidden;
	margin-bottom: 20rpx;
	padding: 6rpx 30rpx;
	border-radius: 32rpx;
}

.form-section {
	padding: 26rpx 0 30rpx;
}

.form-divider {
	height: 1rpx;
	background: linear-gradient(90deg, rgba(79, 147, 163, 0) 0%, rgba(79, 147, 163, 0.2) 18%, rgba(79, 147, 163, 0.2) 82%, rgba(79, 147, 163, 0) 100%);
}

.field-heading,
.tips-heading {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.field-heading-icon {
	width: 48rpx;
	height: 48rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(228, 246, 249, 0.9);
}

.field-heading-icon--pink {
	background: rgba(255, 237, 246, 0.9);
}

.field-input,
.bio {
	width: 100%;
	margin-top: 16rpx;
	padding: 0 24rpx;
	border-radius: 22rpx;
	background: rgba(255, 255, 255, 0.6);
	border: 1rpx solid rgba(79, 147, 163, 0.16);
	box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.88);
	box-sizing: border-box;
	color: #203846;
	font-size: 28rpx;
}

.field-input {
	height: 92rpx;
}

.bio {
	min-height: 200rpx;
	padding-top: 22rpx;
	padding-bottom: 22rpx;
	line-height: 1.7;
}

.tips-title {
	font-size: 24rpx;
}

.tips-strip {
	margin-bottom: 20rpx;
	padding: 20rpx 22rpx;
	border-left: 6rpx solid rgba(79, 147, 163, 0.62);
	border-radius: 0 22rpx 22rpx 0;
	background: rgba(238, 249, 251, 0.52);
}

.foot-note {
	margin-top: 12rpx;
	font-size: 23rpx;
}

.submit-wrap {
	margin-top: 24rpx;
}

.submit-btn {
	height: 92rpx;
	border: none;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	background: linear-gradient(135deg, #4f93a3 0%, #72bdc8 100%);
	box-shadow: 0 16rpx 32rpx rgba(48, 103, 117, 0.2);
	color: #fff;
	font-size: 30rpx;
	font-weight: 800;
}

.submit-btn::after {
	border: none;
}

@media (max-width: 420px) {
	.avatar-card {
		gap: 22rpx;
		padding: 28rpx;
	}

	.avatar {
		width: 138rpx;
		height: 138rpx;
	}

	.section-tip {
		font-size: 22rpx;
	}
}

@media (hover: hover) and (pointer: fine) {
	.avatar-card,
	.profile-form,
	.submit-btn {
		transition: transform 180ms ease, box-shadow 180ms ease;
	}

	.avatar-card:hover,
	.profile-form:hover,
	.submit-btn:hover {
		transform: translateY(-2rpx);
	}
}
</style>
