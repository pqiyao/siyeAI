<template>
	<view v-if="controller.branchPanel.visible" class="branch-mask" @tap="controller.closeBranchPanel()">
		<view class="branch-sheet" @tap.stop>
			<view class="branch-head">
				<view class="branch-head-copy">
					<text class="branch-title">故事管理</text>
					<text class="branch-sub">{{ controller.branchPanel.loading ? controller.tx('loading', '加载中...') : (controller.branchPanel.mode === 'sessions' ? '管理这个角色的独立故事' : (controller.branchPanel.mode === 'openings' ? '选择故事的开场' : '切换当前剧情路线')) }}</text>
				</view>
				<view
					class="branch-close"
					:title="controller.tx('close', '关闭')"
					:aria-label="controller.tx('close', '关闭')"
					@tap="controller.closeBranchPanel()"
				>
					<u-icon name="close" color="#64748b" size="30"></u-icon>
				</view>
			</view>
			<view class="branch-tabs">
				<view class="branch-tab" :class="{ 'branch-tab--active': controller.branchPanel.mode === 'sessions' }" @tap="controller.branchPanel.mode = 'sessions'">会话</view>
				<view class="branch-tab" :class="{ 'branch-tab--active': controller.branchPanel.mode === 'openings' }" @tap="controller.branchPanel.mode = 'openings'">开场</view>
				<view class="branch-tab" :class="{ 'branch-tab--active': controller.branchPanel.mode === 'branches' }" @tap="controller.branchPanel.mode = 'branches'">剧情分支</view>
			</view>
			<view v-if="controller.branchPanel.mode === 'sessions'" class="branch-create" @tap="controller.createStorySession()">
				<u-icon name="plus" color="#245b73" size="28"></u-icon><text>新建故事</text>
			</view>
			<scroll-view class="branch-scroll" scroll-y :show-scrollbar="false">
				<view v-if="controller.branchPanel.loading" class="branch-empty">
					<text class="branch-empty-title">{{ controller.tx('loading', '加载中...') }}</text>
				</view>
				<view v-else-if="controller.branchPanel.error" class="branch-empty">
					<text class="branch-empty-title">{{ controller.tx('branch_load_failed', '分支加载失败') }}</text>
					<text class="branch-empty-sub" @tap.stop="controller.loadBranchPanel()">{{ controller.branchPanel.error }}</text>
				</view>
				<view v-else-if="!controller.branchPanelRows.length" class="branch-empty">
					<text class="branch-empty-title">{{ controller.branchPanel.mode === 'sessions' ? '暂无独立会话' : (controller.branchPanel.mode === 'openings' ? '暂无开场场景' : '暂无剧情分支') }}</text>
					<text class="branch-empty-sub">{{ controller.branchPanel.mode === 'sessions' ? '点击上方新建故事' : (controller.branchPanel.mode === 'openings' ? '可以在角色卡编辑器中添加开场' : '长按任意消息即可从那里创建新路线') }}</text>
				</view>
				<view
					v-for="row in controller.branchPanelRows"
					:key="'branch_row_' + row.id"
					class="branch-row"
					:class="{
						'branch-row--active': row.active,
						'branch-row--switching': controller.branchPanel.switchingBranchId === row.id || controller.branchPanel.deletingBranchId === row.id
					}"
					:style="row.kind === 'branch' && row.depth ? { paddingLeft: (24 + row.depth * 24) + 'rpx' } : null"
					@tap.stop="controller.switchBranch(row)"
				>
					<view class="branch-row-head">
						<view class="branch-row-title-wrap">
							<view class="branch-row-title-line">
								<u-icon v-if="row.kind === 'branch' && row.depth" name="arrow-right" color="#94a3b8" size="22"></u-icon>
								<text class="branch-row-title">{{ row.title }}</text>
							</view>
							<text class="branch-row-preview">{{ row.preview || controller.tx('branch_no_preview', '暂无消息') }}</text>
						</view>
						<view class="branch-row-side">
							<text class="branch-row-current">{{ row.active ? controller.tx('branch_current', '当前') : (row.created ? controller.tx('branch_continue', '继续') : controller.tx('branch_start', '开始')) }}</text>
							<view v-if="row.kind === 'branch' || row.kind === 'session'" class="branch-row-actions">
								<u-icon name="edit-pen" color="#64748b" size="28" @tap.stop="controller.openBranchRename(row)"></u-icon>
								<u-icon v-if="row.kind === 'branch' && !row.defaultBranch" name="trash" color="#b45353" size="28" @tap.stop="controller.deleteManagedBranch(row)"></u-icon>
								<u-icon v-if="row.kind === 'session' && !row.active" name="trash" color="#b45353" size="28" @tap.stop="controller.deleteStorySession(row)"></u-icon>
							</view>
						</view>
					</view>
					<view class="branch-row-meta">
						<text v-if="row.created" class="branch-row-meta-text">{{ controller.tx('branch_message_count', '{n} 条消息').replace('{n}', String(row.messageCount || 0)) }}</text>
						<text v-else class="branch-row-meta-text">{{ controller.tx('branch_new_story', '新剧情') }}</text>
					</view>
				</view>
			</scroll-view>
		</view>
		<view v-if="controller.branchPanel.editorVisible" class="branch-editor-mask" @tap.stop="controller.closeBranchRename()">
			<view class="branch-editor" @tap.stop>
				<text class="branch-editor-title">重命名</text>
				<input
					class="branch-editor-input"
					v-model="controller.branchPanel.editorTitle"
					maxlength="80"
					:disabled="controller.branchPanel.editing"
					placeholder="输入名称"
					confirm-type="done"
					@confirm="controller.saveBranchRename()"
				/>
				<view class="branch-editor-actions">
					<text class="branch-editor-button" @tap="controller.closeBranchRename()">取消</text>
					<text class="branch-editor-button branch-editor-button--primary" @tap="controller.saveBranchRename()">保存</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'ChatBranchPanel',
	props: {
		controller: {
			type: Object,
			required: true
		}
	}
};
</script>

<style lang="scss">
.branch-mask {
	position: fixed;
	left: 0;
	right: 0;
	top: 0;
	bottom: 0;
	z-index: 2330;
	display: flex;
	align-items: flex-end;
	justify-content: center;
	padding: 32rpx 20rpx calc(env(safe-area-inset-bottom) + 24rpx);
	background: rgba(10, 18, 24, 0.32);
	box-sizing: border-box;
}

.branch-sheet {
	width: 100%;
	max-width: 760rpx;
	max-height: calc(82vh - env(safe-area-inset-bottom));
	min-height: 420rpx;
	height: calc(82vh - env(safe-area-inset-bottom));
	padding: 26rpx;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	border-radius: 32rpx;
	background: rgba(255, 255, 255, 0.95);
	border: 1rpx solid rgba(255, 255, 255, 0.72);
	box-shadow: 0 20rpx 52rpx rgba(15, 23, 42, 0.16);
	box-sizing: border-box;
}

.branch-head {
	flex-shrink: 0;
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 20rpx;
}

.branch-head-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.branch-title {
	font-size: 32rpx;
	line-height: 1.25;
	font-weight: 800;
	color: #17344b;
}

.branch-sub {
	font-size: 23rpx;
	line-height: 1.45;
	color: #64748b;
}

.branch-close {
	flex-shrink: 0;
	width: 58rpx;
	height: 58rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 50%;
	color: #64748b;
	background: rgba(241, 245, 249, 0.86);
}

.branch-tabs {
	flex-shrink: 0;
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	margin-top: 20rpx;
	padding: 5rpx;
	border-radius: 10rpx;
	background: #edf3f6;
	border: 1rpx solid rgba(87, 119, 136, 0.14);
}

.branch-tab {
	height: 62rpx;
	line-height: 62rpx;
	text-align: center;
	border-radius: 7rpx;
	font-size: 24rpx;
	font-weight: 650;
	color: #647b88;
}

.branch-tab--active {
	color: #173f52;
	background: #fff;
	box-shadow: 0 4rpx 12rpx rgba(42, 84, 104, 0.1);
}

.branch-create {
	flex-shrink: 0;
	height: 68rpx;
	margin-top: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	border-bottom: 1rpx solid rgba(87, 119, 136, 0.18);
	font-size: 24rpx;
	font-weight: 700;
	color: #245b73;
}

.branch-scroll {
	flex: 1;
	min-height: 0;
	margin-top: 20rpx;
	height: 0;
	width: 100%;
	overflow: hidden;
}

.branch-empty {
	min-height: 220rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	padding: 24rpx;
	box-sizing: border-box;
}

.branch-empty-title {
	font-size: 28rpx;
	font-weight: 700;
	color: #334155;
}

.branch-empty-sub {
	font-size: 23rpx;
	line-height: 1.5;
	color: #7a8796;
	text-align: center;
}

.branch-row {
	padding: 22rpx;
	border-radius: 24rpx;
	background: rgba(248, 250, 252, 0.92);
	border: 1rpx solid rgba(203, 213, 225, 0.52);
	box-sizing: border-box;
}

.branch-row--active {
	background: rgba(235, 252, 255, 0.94);
	border-color: rgba(79, 147, 163, 0.46);
	box-shadow: 0 10rpx 22rpx rgba(48, 103, 117, 0.12);
}

.branch-row--switching {
	opacity: 0.62;
	pointer-events: none;
}

.branch-row + .branch-row {
	margin-top: 18rpx;
}

.branch-row-head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18rpx;
}

.branch-row-title-wrap {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.branch-row-title {
	font-size: 27rpx;
	line-height: 1.35;
	font-weight: 750;
	color: #172033;
}

.branch-row-title-line,
.branch-row-side,
.branch-row-actions {
	display: flex;
	align-items: center;
}

.branch-row-title-line {
	min-width: 0;
	gap: 6rpx;
}

.branch-row-side {
	flex-shrink: 0;
	gap: 12rpx;
}

.branch-row-actions {
	gap: 10rpx;
	padding: 2rpx 0;
}

.branch-row-preview {
	display: block;
	font-size: 24rpx;
	line-height: 1.55;
	color: #475569;
	word-break: break-word;
}

.branch-row-current {
	flex-shrink: 0;
	padding: 7rpx 14rpx;
	border-radius: 999rpx;
	font-size: 21rpx;
	line-height: 1.25;
	font-weight: 700;
	color: #1f6686;
	background: rgba(220, 247, 251, 0.84);
}

.branch-row-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 16rpx;
}

.branch-row-meta-text {
	padding: 6rpx 12rpx;
	border-radius: 999rpx;
	font-size: 21rpx;
	line-height: 1.25;
	font-weight: 650;
	color: #64748b;
	background: rgba(241, 245, 249, 0.9);
}

.branch-editor-mask {
	position: absolute;
	inset: 0;
	z-index: 3;
	display: flex;
	align-items: flex-end;
	background: rgba(15, 23, 42, 0.34);
}

.branch-editor {
	width: 100%;
	padding: 30rpx;
	box-sizing: border-box;
	border-radius: 22rpx 22rpx 0 0;
	background: #ffffff;
}

.branch-editor-title {
	display: block;
	font-size: 29rpx;
	font-weight: 750;
	color: #172033;
}

.branch-editor-input {
	height: 78rpx;
	margin-top: 22rpx;
	padding: 0 20rpx;
	box-sizing: border-box;
	border: 1rpx solid #cbd5e1;
	border-radius: 8rpx;
	font-size: 27rpx;
	color: #172033;
	background: #ffffff;
}

.branch-editor-actions {
	display: flex;
	justify-content: flex-end;
	gap: 18rpx;
	margin-top: 26rpx;
}

.branch-editor-button {
	min-width: 112rpx;
	height: 66rpx;
	line-height: 66rpx;
	text-align: center;
	border-radius: 8rpx;
	font-size: 25rpx;
	font-weight: 700;
	color: #475569;
	background: #f1f5f9;
}

.branch-editor-button--primary {
	color: #ffffff;
	background: #24647d;
}

.wrap--app-plus .branch-mask {
	padding: 18rpx 16rpx calc(env(safe-area-inset-bottom) + 12rpx) !important;
}

.wrap--app-plus .branch-sheet {
	border-radius: 32rpx;
}
</style>
