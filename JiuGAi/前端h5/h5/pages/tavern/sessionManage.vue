<template>
	<view class="page" :class="localeFontClass">
		<image class="app-page-bg" src="/static/login.png" mode="aspectFill"></image>
		<tavern-nav-bar mode="dark" :title="t.管理会话 || '管理会话'" @back="goBack">
			<template #right>
				<view class="nav-action" @tap="toggleSelectAll">
					<text class="nav-action-txt">{{ allSelected ? (t.取消全选 || '取消全选') : (t.全选 || '全选') }}</text>
				</view>
			</template>
		</tavern-nav-bar>

		<view v-if="loading" class="banner banner--load">
			<text class="banner-txt">{{ t.详情加载中 || '加载中…' }}</text>
		</view>
		<view v-else-if="error" class="banner banner--err">
			<text class="banner-txt">{{ error }}</text>
			<text class="banner-retry" @tap="loadSessions">{{ t.发现点击重试 || '点击重试' }}</text>
		</view>
		<view v-else-if="!sessions.length" class="empty">
			<text class="empty-txt">{{ t.暂无会话可管理 || '暂无会话' }}</text>
		</view>

		<scroll-view v-else scroll-y class="scroll" :show-scrollbar="false">
			<view
				class="row"
				v-for="s in sessions"
				:key="s.id"
				@tap="toggleOne(s)"
			>
				<view class="check" :class="{ 'check--on': isSelected(s) }">
					<text v-if="isSelected(s)" class="check-mark">✓</text>
				</view>
				<image class="avatar" :src="sessionCover(s)" mode="aspectFill" lazy-load></image>
				<view class="row-main">
					<text class="row-title">{{ displayTitle(s) }}</text>
					<text class="row-desc">{{ sessionSnippet(s) }}</text>
				</view>
			</view>
			<view class="scroll-pad"></view>
		</scroll-view>

		<view class="footer" v-if="sessions.length">
			<text class="footer-count">{{ selectedCountText }}</text>
			<button
				class="footer-btn"
				:class="{ 'footer-btn--disabled': !selectedIds.length || deleting }"
				:disabled="!selectedIds.length || deleting"
				@tap="confirmDeleteSelected"
			>
				{{ deleting ? (t.处理中 || '处理中…') : (t.删除所选 || '删除所选') }}
			</button>
		</view>
	</view>
</template>

<script>
	import TavernNavBar from '@/components/tavern/tavern-nav-bar.vue';

	const tavernApi = require('@/common/tavernApi.js');
	const tavernErrors = require('@/common/tavernErrors.js');

	function clearSessionLocalArtifacts(session) {
		const clientUid = tavernApi.getClientUid();
		const candidates = [];
		if (session && session.id != null && session.id !== '') {
			candidates.push(session.id);
		}
		if (session && session.characterId != null && session.characterId !== '') {
			candidates.push('char_' + session.characterId);
		}
		candidates.forEach((candidate) => {
			tavernApi.cleanupLocalConversationArtifacts({
				clientUid,
				conversationId: candidate
			});
		});
	}

	export default {
		components: { TavernNavBar },
		data() {
			return {
				sessions: [],
				selectedIds: [],
				loading: false,
				error: '',
				deleting: false
			};
		},
		computed: {
			t() {
				return this.allText.酒馆页 || {};
			},
			allSelected() {
				return this.sessions.length > 0 && this.selectedIds.length === this.sessions.length;
			},
			selectedCountText() {
				const n = this.selectedIds.length;
				const tpl = this.t.已选会话数 || '已选 {n} 项';
				return String(tpl).replace('{n}', String(n));
			}
		},
		onShow() {
			this.loadSessions();
		},
		methods: {
			goBack() {
				uni.navigateBack({
					fail: () => {
						uni.switchTab({ url: '/pages/tavern/tavernInbox' });
					}
				});
			},
			loadSessions() {
				if (!tavernApi.jgEnabled()) {
					this.loading = false;
					this.sessions = [];
					this.selectedIds = [];
					this.error = this.t.发现后端未配置 || '后端接口未开启';
					return;
				}
				this.loading = true;
				this.error = '';
				tavernApi
					.fetchTavernSessions(tavernApi.getClientUid())
					.then((list) => {
						this.sessions = Array.isArray(list) ? list : [];
						const alive = {};
						this.sessions.forEach((s) => {
							if (s && s.id != null) alive[String(s.id)] = true;
						});
						this.selectedIds = this.selectedIds.filter((id) => alive[String(id)]);
						this.error = '';
					})
					.catch((e) => {
						this.sessions = [];
						this.selectedIds = [];
						this.error = tavernErrors.getTavernErrorMessage(
							e,
							(this.allText.酒馆页 && this.allText.酒馆页.收件箱加载失败) || '加载失败'
						);
					})
					.finally(() => {
						this.loading = false;
					});
			},
			displayTitle(s) {
				return s.displayTitle || s.nickname || this.t.收件箱默认标题 || '会话';
			},
			sessionSnippet(s) {
				return s.snippet || s.lastMessage || '';
			},
			sessionCover(s) {
				const u = s.coverThumbUrl || s.avatarThumbUrl || s.coverUrl || s.avatarUrl;
				if (!u || String(u).trim() === '') return '/static/logo.png';
				return tavernApi.resolveJgAssetUrl(u) || '/static/logo.png';
			},
			isSelected(s) {
				if (!s || s.id == null) return false;
				return this.selectedIds.indexOf(String(s.id)) >= 0;
			},
			toggleOne(s) {
				if (!s || s.id == null || this.deleting) return;
				const id = String(s.id);
				const idx = this.selectedIds.indexOf(id);
				if (idx >= 0) {
					this.selectedIds = this.selectedIds.filter((x) => x !== id);
				} else {
					this.selectedIds = this.selectedIds.concat([id]);
				}
			},
			toggleSelectAll() {
				if (this.deleting || !this.sessions.length) return;
				if (this.allSelected) {
					this.selectedIds = [];
					return;
				}
				this.selectedIds = this.sessions
					.filter((s) => s && s.id != null)
					.map((s) => String(s.id));
			},
			confirmDeleteSelected() {
				if (this.deleting || !this.selectedIds.length) return;
				const selected = this.sessions.filter(
					(s) => s && s.id != null && this.selectedIds.indexOf(String(s.id)) >= 0
				);
				if (!selected.length) {
					uni.showToast({ title: this.t.请先选择会话 || '请先选择会话', icon: 'none' });
					return;
				}
				const invalid = selected.some(
					(s) => s.characterId == null || s.characterId === '' || !Number(s.characterId)
				);
				if (invalid) {
					uni.showToast({ title: this.t.删除失败无角色 || '无法删除', icon: 'none' });
					return;
				}
				uni.showModal({
					title: this.t.删除会话标题 || '删除会话',
					content: (this.t.批量删除会话确认 || '确认删除已选 {n} 个会话？记录将不可恢复。').replace(
						'{n}',
						String(selected.length)
					),
					confirmText: this.t.删除会话确定 || '删除',
					cancelText: this.t.关闭 || '取消',
					success: (res) => {
						if (!res.confirm) return;
						this.deleteSelected(selected);
					}
				});
			},
			deleteSelected(selected) {
				if (this.deleting) return;
				this.deleting = true;
				uni.showLoading({
					title: this.t.处理中 || '处理中…',
					mask: true
				});
				const clientUid = tavernApi.getClientUid();
				let ok = 0;
				let fail = 0;
				const runNext = (index) => {
					if (index >= selected.length) {
						try {
							uni.hideLoading();
						} catch (e) {}
						this.deleting = false;
						if (fail === 0) {
							uni.showToast({
								title: (this.t.批量删除成功 || '已删除 {n} 个会话').replace('{n}', String(ok)),
								icon: 'success'
							});
						} else {
							uni.showToast({
								title: (this.t.批量删除部分失败 || '成功 {ok}，失败 {fail}')
									.replace('{ok}', String(ok))
									.replace('{fail}', String(fail)),
								icon: 'none'
							});
						}
						this.loadSessions();
						return;
					}
					const s = selected[index];
					tavernApi
						.postTavernSessionDelete({
							characterId: Number(s.characterId),
							clientUid
						})
						.then(() => {
							clearSessionLocalArtifacts(s);
							ok += 1;
						})
						.catch(() => {
							fail += 1;
						})
						.finally(() => {
							runNext(index + 1);
						});
				};
				runNext(0);
			}
		}
	};
</script>

<style scoped lang="scss">
	$text: #244b66;
	$muted: $tavern-muted-on-dark;
	$desc: #687f92;
	$link: #247494;
	$card-bg: $tavern-card-dark;

	.page {
		position: relative;
		height: 100vh;
		display: flex;
		flex-direction: column;
		background: $tavern-surface-dark;
		padding-bottom: env(safe-area-inset-bottom);
		box-sizing: border-box;
		overflow: hidden;
	}

	.nav-action {
		min-width: 120rpx;
		min-height: 72rpx;
		padding: 0 16rpx;
		display: flex;
		align-items: center;
		justify-content: flex-end;
	}

	.nav-action-txt {
		font-size: 24rpx;
		color: $link;
		font-weight: 600;
	}

	.banner {
		margin: 16rpx 28rpx;
		padding: 20rpx 22rpx;
		border-radius: 16rpx;
		display: flex;
		flex-direction: row;
		align-items: center;
		flex-wrap: wrap;
		gap: 16rpx;
	}

	.banner--load {
		background: rgba(124, 58, 237, 0.12);
	}

	.banner--err {
		background: rgba(185, 28, 28, 0.14);
	}

	.banner-txt {
		flex: 1;
		font-size: 24rpx;
		color: $desc;
	}

	.banner-retry {
		font-size: 24rpx;
		color: $link;
		font-weight: 600;
	}

	.empty {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 80rpx 40rpx;
	}

	.empty-txt {
		font-size: 26rpx;
		color: $muted;
	}

	.scroll {
		flex: 1;
		height: 0;
		width: 100%;
	}

	.row {
		margin: 0 28rpx 18rpx;
		padding: 18rpx 20rpx;
		border-radius: 18rpx;
		background: $card-bg;
		border: 1rpx solid rgba(255, 255, 255, 0.9);
		box-shadow: 0 10rpx 24rpx rgba(67, 112, 142, 0.08);
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 16rpx;
	}

	.check {
		width: 40rpx;
		height: 40rpx;
		border-radius: 10rpx;
		border: 2rpx solid rgba(36, 116, 148, 0.35);
		box-sizing: border-box;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.check--on {
		background: #247494;
		border-color: #247494;
	}

	.check-mark {
		color: #fff;
		font-size: 22rpx;
		font-weight: 700;
		line-height: 1;
	}

	.avatar {
		width: 88rpx;
		height: 88rpx;
		border-radius: 14rpx;
		flex-shrink: 0;
		background: #dceefa;
	}

	.row-main {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.row-title {
		font-size: 28rpx;
		font-weight: 700;
		color: $text;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row-desc {
		font-size: 22rpx;
		color: $desc;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.scroll-pad {
		height: 160rpx;
	}

	.footer {
		flex-shrink: 0;
		padding: 16rpx 28rpx calc(16rpx + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 20rpx;
		background: rgba(255, 255, 255, 0.82);
		border-top: 1rpx solid rgba(148, 183, 210, 0.28);
		backdrop-filter: blur(12px);
	}

	.footer-count {
		flex: 1;
		font-size: 24rpx;
		color: $muted;
	}

	.footer-btn {
		margin: 0;
		min-width: 220rpx;
		height: 76rpx;
		line-height: 76rpx;
		padding: 0 28rpx;
		border-radius: 38rpx;
		background: #ef4444;
		color: #fff;
		font-size: 26rpx;
		font-weight: 700;
		border: none;
	}

	.footer-btn--disabled {
		opacity: 0.45;
	}

	.footer-btn::after {
		border: none;
	}
</style>

<style>
	page {
		background:
			radial-gradient(circle at 12% 0%, rgba(200, 229, 250, 0.98) 0%, rgba(200, 229, 250, 0) 38%),
			radial-gradient(circle at 92% 3%, rgba(248, 226, 244, 0.9) 0%, rgba(248, 226, 244, 0) 34%),
			linear-gradient(155deg, #dceefa 0%, #ecf8fb 48%, #fff4f8 100%);
		height: 100%;
	}
</style>
