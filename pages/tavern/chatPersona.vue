<template>
	<view class="page">
		<tavern-nav-bar title="聊天设定" mode="dark" @back="goBack" />
		<view class="body">
			<view class="card card--hint">
				<text class="hint-title">名字现在分三层管理</text>
				<text class="hint-text">
					资料昵称用于你在业务侧的展示；默认聊天称呼会作为你在 聊天运行时里的默认名字；当前会话覆盖名只影响这个角色的这一条聊天。
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
					<text class="field-tip">这是发送给 聊天运行时的默认 user_name，会影响角色怎么称呼你。</text>
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
				<image
					v-if="backgroundPreview"
					class="bg-preview"
					:src="backgroundPreview"
					mode="aspectFill"
				/>
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
					uni.showToast({ title: '请先配置角色聊天后端', icon: 'none' });
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
		background: #12122b;
		display: flex;
		flex-direction: column;
	}

	.body {
		padding: 24rpx;
		display: flex;
		flex-direction: column;
		gap: 20rpx;
	}

	.card {
		padding: 24rpx;
		border-radius: 18rpx;
		background: rgba(26, 26, 56, 0.92);
		border: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.card--hint {
		background: linear-gradient(135deg, rgba(91, 33, 182, 0.32) 0%, rgba(219, 39, 119, 0.16) 100%);
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
		font-size: 30rpx;
		font-weight: 600;
		color: #f8fafc;
		margin-bottom: 12rpx;
	}

	.hint-text,
	.field-tip,
	.bg-empty-text {
		font-size: 24rpx;
		color: #94a3b8;
		line-height: 1.6;
	}

	.field {
		margin-bottom: 24rpx;
	}

	.lab {
		font-size: 26rpx;
		color: #e2e8f0;
		margin-bottom: 12rpx;
	}

	.inp {
		width: 100%;
		height: 80rpx;
		padding: 0 24rpx;
		box-sizing: border-box;
		background: #141430;
		border-radius: 12rpx;
		font-size: 28rpx;
		color: #f1f5f9;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.area {
		width: 100%;
		min-height: 240rpx;
		padding: 20rpx 24rpx;
		box-sizing: border-box;
		background: #141430;
		border-radius: 12rpx;
		font-size: 28rpx;
		color: #f1f5f9;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
	}

	.bg-preview {
		width: 100%;
		height: 220rpx;
		border-radius: 14rpx;
		margin: 12rpx 0;
	}

	.bg-empty {
		margin: 12rpx 0;
		padding: 28rpx 24rpx;
		border-radius: 14rpx;
		background: rgba(15, 23, 42, 0.55);
		border: 1rpx dashed rgba(148, 163, 184, 0.28);
	}

	.actions {
		display: flex;
		gap: 16rpx;
	}

	.btn {
		flex: 1;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		border-radius: 44rpx;
		background: linear-gradient(90deg, #7c3aed 0%, #ec4899 100%);
		color: #fff;
		font-size: 30rpx;
	}

	.btn--ghost {
		background: rgba(148, 163, 184, 0.16);
		color: #cbd5f5;
	}

	.btn.dis {
		opacity: 0.5;
	}
</style>

<style>
	.ph {
		color: #64748b;
	}
</style>
