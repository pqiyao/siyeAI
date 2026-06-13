<template>
	<view class="page">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar title="聊天设定" mode="dark" @back="goBack" />
		<view class="body">
			<view class="card card--hint">
				<text class="hint-title">名字现在分三层管理</text>
				<text class="hint-text">
					资料昵称用于你在业务侧的展示；默认聊天称呼会作为你在聊天时的默认名字；当前会话覆盖名只影响这个角色的这一条聊天。
				</text>
			</view>

			<view class="card">
				<text class="section-title">资料与聊天身份</text>
				<view class="field">
					<text class="lab">资料昵称</text>
					<input
						class="inp"
						v-model="displayName"
						placeholder="例如：小夏、旅人、店长"
						placeholder-class="ph"
					/>
					<text class="field-tip">用于业务资料页和部分展示场景，也会作为默认聊天名字的兜底。</text>
				</view>

				<view class="field">
					<text class="lab">默认聊天称呼</text>
					<input
						class="inp"
						v-model="stDisplayName"
						placeholder="例如：yao、洛奇希、旅行者"
						placeholder-class="ph"
					/>
					<text class="field-tip">这是聊天时默认使用的你的称呼，会影响角色怎么称呼你。</text>
				</view>

				<view class="field" v-if="cid">
					<text class="lab">当前会话覆盖名</text>
					<input
						class="inp"
						v-model="stDisplayNameOverride"
						placeholder="只覆盖当前角色会话，可留空"
						placeholder-class="ph"
					/>
					<text class="field-tip">当前生效名称：{{ effectiveStDisplayName || '未设置' }}</text>
				</view>

				<view class="field">
					<text class="lab">我的人设</text>
					<textarea
						class="area"
						v-model="persona"
						placeholder="可以写你的身份、性格、习惯、与你和角色的关系、说话方式等"
						placeholder-class="ph"
						auto-height
					/>
				</view>
				<text class="field-tip">建议写稳定设定，不要写一次性剧情，否则会影响后续多轮对话。</text>
			</view>

			<view class="card">
				<text class="section-title">当前角色背景</text>
				<text class="field-tip" v-if="characterName">当前对话角色：{{ characterName }}</text>
				<view v-if="backgroundPreview" class="bg-preview-wrap" @tap="previewBackground">
					<image
						class="bg-preview"
						:src="backgroundPreview"
						mode="aspectFill"
					/>
					<view class="bg-preview-action">
						<text class="bg-preview-action-text">点击查看背景图</text>
					</view>
				</view>
				<view v-else class="bg-empty">
					<text class="bg-empty-text">当前角色未配置官方聊天背景，将使用默认深色背景。</text>
				</view>
				<text class="field-tip">
					聊天背景由后台角色卡统一配置；如果后台配置了官方背景，聊天页会自动使用。
				</text>
			</view>

			<view class="actions">
				<view class="btn btn--ghost" :class="{ dis: saving }" @tap="resetPersona">清空设定</view>
				<view class="btn" :class="{ dis: saving }" @tap="save">{{ saving ? '保存中...' : '保存' }}</view>
			</view>
		</view>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	export default {
		components: { TavernNavBar },
		data() {
			return {
				cid: '',
				conversationId: null,
				displayName: '',
				stDisplayName: '',
				stDisplayNameOverride: '',
				effectiveStDisplayName: '',
				persona: '',
				saving: false,
				characterName: '',
				backgroundPreview: ''
			};
		},
		onLoad(query) {
			this.cid = query && query.id ? String(query.id) : '';
			this.loadProfile();
			this.loadCharacterPreview();
		},
		methods: {
			goBack() {
				uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/tavern/tavern' }) });
			},
			loadProfile() {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) {
					uni.showToast({ title: '请先配置酒馆后端', icon: 'none' });
					return;
				}
				const tavernErrors = require('@/common/tavernErrors.js');
				tavernApi
					.getTavernProfile(tavernApi.getClientUid(), this.cid || undefined)
					.then((data) => {
						if (!data) return;
						this.displayName = data.display_name || '';
						this.stDisplayName = data.st_display_name || '';
						this.stDisplayNameOverride = data.st_display_name_override || '';
						this.effectiveStDisplayName = data.effective_st_display_name || '';
						this.persona = data.persona || '';
						this.conversationId = data.conversation_id || null;
					})
					.catch((error) => {
						uni.showToast({
							title: tavernErrors.getTavernErrorMessage(error, '加载聊天设定失败'),
							icon: 'none',
							duration: 2800
						});
					});
			},
			loadCharacterPreview() {
				if (!this.cid) return;
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled()) return;
				tavernApi
					.fetchCharacter(this.cid)
					.then((card) => {
						if (!card) return;
						this.characterName = card.nickname || card.name || '';
						const raw = card.chat_background_url || card.chatBackgroundUrl || '';
						this.backgroundPreview = raw ? tavernApi.resolveJgAssetUrl(raw) : '';
					})
					.catch(() => {});
			},
			previewBackground() {
				if (!this.backgroundPreview) return;
				uni.previewImage({
					current: this.backgroundPreview,
					urls: [this.backgroundPreview]
				});
			},
			resetPersona() {
				if (this.saving) return;
				this.displayName = '';
				this.stDisplayName = '';
				this.stDisplayNameOverride = '';
				this.effectiveStDisplayName = '';
				this.persona = '';
			},
			save() {
				const tavernApi = require('@/common/tavernApi.js');
				if (!tavernApi.jgEnabled() || this.saving) return;
				this.saving = true;
				const payload = {
					display_name: (this.displayName || '').trim(),
					st_display_name: (this.stDisplayName || '').trim(),
					persona: (this.persona || '').trim()
				};
				if (this.cid) {
					payload.st_display_name_override = (this.stDisplayNameOverride || '').trim();
				}
				tavernApi
					.putTavernProfile(tavernApi.getClientUid(), payload, this.cid || undefined)
					.then(() => {
						this.effectiveStDisplayName =
							(this.stDisplayNameOverride || '').trim() ||
							(this.stDisplayName || '').trim() ||
							(this.displayName || '').trim();
						uni.showToast({ title: '聊天设定已保存', icon: 'none' });
					})
					.catch((error) => {
						const tavernErrors = require('@/common/tavernErrors.js');
						uni.showToast({
							title: tavernErrors.getTavernErrorMessage(error, '保存聊天设定失败'),
							icon: 'none'
						});
					})
					.finally(() => {
						this.saving = false;
					});
			}
		}
	};
</script>

<style scoped lang="scss">
	.page {
		min-height: 100vh;
		background: transparent;
		display: flex;
		flex-direction: column;
		position: relative;
		overflow: hidden;
		color: #203846;
	}

	.page::before,
	.page::after {
		display: none;
	}

	.body {
		position: relative;
		z-index: 1;
		padding: 22rpx 24rpx calc(40rpx + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}

	.card {
		padding: 28rpx 26rpx;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.78);
		border: 1rpx solid rgba(255, 255, 255, 0.66);
		box-shadow: 0 16rpx 36rpx rgba(36, 70, 88, 0.1);
		backdrop-filter: blur(18rpx);
		-webkit-backdrop-filter: blur(18rpx);
	}

	.card--hint {
		padding: 26rpx;
		background: rgba(255, 255, 255, 0.82);
		border-left: 6rpx solid rgba(63, 143, 159, 0.72);
	}

	.hint-title,
	.section-title,
	.lab,
	.field-tip,
	.bg-empty-text {
		display: block;
	}

	.hint-title,
	.section-title {
		margin-bottom: 12rpx;
		font-size: 30rpx;
		font-weight: 800;
		color: #203846;
	}

	.hint-text,
	.field-tip,
	.bg-empty-text {
		font-size: 24rpx;
		color: #5f7280;
		line-height: 1.68;
	}

	.field {
		margin-bottom: 22rpx;
	}

	.field:last-child {
		margin-bottom: 0;
	}

	.lab {
		margin-bottom: 10rpx;
		font-size: 26rpx;
		font-weight: 700;
		color: #236f82;
	}

	.inp,
	.area {
		width: 100%;
		box-sizing: border-box;
		background: rgba(255, 255, 255, 0.72);
		border: 1rpx solid rgba(79, 147, 163, 0.16);
		border-radius: 18rpx;
		color: #203846;
		font-size: 28rpx;
	}

	.inp {
		height: 82rpx;
		padding: 0 24rpx;
	}

	.area {
		min-height: 236rpx;
		padding: 20rpx 24rpx;
		line-height: 1.64;
	}

	.inp:focus,
	.area:focus {
		border-color: rgba(79, 147, 163, 0.74);
		box-shadow: 0 0 0 2rpx rgba(79, 147, 163, 0.12);
	}

	.bg-preview-wrap {
		position: relative;
		width: 100%;
		height: 248rpx;
		margin: 14rpx 0;
		border-radius: 20rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		box-shadow: 0 12rpx 28rpx rgba(36, 70, 88, 0.1);
		overflow: hidden;
		background: rgba(255, 255, 255, 0.46);
	}

	.bg-preview {
		display: block;
		width: 100%;
		height: 100%;
	}

	.bg-preview-action {
		position: absolute;
		right: 18rpx;
		bottom: 18rpx;
		z-index: 2;
		padding: 10rpx 18rpx;
		border-radius: 999rpx;
		background: rgba(18, 35, 45, 0.62);
		border: 1rpx solid rgba(255, 255, 255, 0.24);
		box-shadow: 0 8rpx 18rpx rgba(18, 35, 45, 0.16);
	}

	.bg-preview-action-text {
		color: #fff;
		font-size: 22rpx;
		font-weight: 700;
		line-height: 1.2;
	}

	.bg-empty {
		margin: 14rpx 0;
		padding: 28rpx 24rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.62);
		border: 1rpx dashed rgba(79, 147, 163, 0.3);
	}

	.actions {
		display: flex;
		gap: 16rpx;
		padding: 10rpx;
		border-radius: 999rpx;
		background: rgba(255, 255, 255, 0.76);
		border: 1rpx solid rgba(255, 255, 255, 0.66);
		box-shadow: 0 14rpx 32rpx rgba(36, 70, 88, 0.1);
	}

	.btn {
		flex: 1;
		height: 78rpx;
		line-height: 78rpx;
		text-align: center;
		border-radius: 999rpx;
		background: #3f8f9f;
		color: #fff;
		font-size: 29rpx;
		font-weight: 700;
		box-shadow: 0 12rpx 26rpx rgba(48, 103, 117, 0.2);
	}

	.btn--ghost {
		background: rgba(255, 255, 255, 0.72);
		color: #236f82;
		border: 1rpx solid rgba(79, 147, 163, 0.12);
		box-shadow: none;
	}

	.btn.dis {
		opacity: 0.5;
		pointer-events: none;
	}
</style>

<style>
	.ph {
		color: #758590;
	}
</style>
