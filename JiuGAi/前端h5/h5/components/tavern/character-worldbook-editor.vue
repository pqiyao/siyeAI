<template>
	<view class="studio-section">
		<view class="section-head">
			<view>
				<text class="section-title">世界书</text>
				<text class="section-sub">按关键词在需要时注入设定</text>
			</view>
			<view v-if="entries.length < 300" class="icon-action" @tap="addEntry"><u-icon name="plus" color="#245b73" size="30"></u-icon></view>
		</view>
		<view v-if="!entries.length" class="empty-row"><u-icon name="bookmark" color="#77909d" size="32"></u-icon><text>还没有世界书条目</text></view>
		<view v-for="(entry, index) in entries" :key="entry.clientKey || index" class="entry-row">
			<view class="entry-head">
				<input v-model="entry.title" maxlength="120" class="entry-title" :placeholder="'条目 ' + (index + 1)" />
				<switch :checked="entry.enabled !== false" color="#2d7994" @change="entry.enabled = $event.detail.value" />
				<view class="remove-action" @tap="removeEntry(index)"><u-icon name="trash" color="#9b5c67" size="26"></u-icon></view>
			</view>
			<input :value="keywordsText(entry)" class="keyword-input" placeholder="关键词，用逗号分隔" @input="updateKeywords(entry, $event.detail.value)" />
			<textarea v-model="entry.content" maxlength="30000" auto-height class="entry-content" placeholder="输入地点、人物关系、规则或背景设定"></textarea>
			<view class="entry-options">
				<view class="option-toggle" @tap="entry.constantInjection = !entry.constantInjection"><u-icon :name="entry.constantInjection ? 'checkmark-circle-fill' : 'radio-button-off'" color="#2d7994" size="24"></u-icon><text>常驻</text></view>
				<view class="scope-select" @tap="chooseScope(entry)">{{ scopeLabel(entry) }} <u-icon name="arrow-down" color="#607b89" size="18"></u-icon></view>
				<view class="advanced-toggle" @tap="toggleAdvanced(entry)"><text>高级</text><u-icon :name="entry.advancedOpen ? 'arrow-up' : 'arrow-down'" color="#607b89" size="18"></u-icon></view>
			</view>
			<view v-if="entry.advancedOpen" class="entry-advanced">
				<view class="advanced-field">
					<text class="advanced-label">次关键词</text>
					<input :value="secondaryKeywordsText(entry)" class="advanced-input" placeholder="可选；主关键词命中后再检查" @input="updateSecondaryKeywords(entry, $event.detail.value)" />
				</view>
				<view class="advanced-field">
					<text class="advanced-label">主关键词规则</text>
					<view class="segmented-control">
						<view class="segment-option" :class="{ 'segment-option--on': entry.matchMode !== 'ALL' }" @tap="setMatchMode(entry, 'ANY')">任一命中</view>
						<view class="segment-option" :class="{ 'segment-option--on': entry.matchMode === 'ALL' }" @tap="setMatchMode(entry, 'ALL')">全部命中</view>
					</view>
				</view>
				<view class="advanced-grid">
					<view class="advanced-cell">
						<text class="advanced-label">扫描消息数</text>
						<input :value="entry.scanDepth == null ? 8 : entry.scanDepth" type="number" class="number-input" @input="updateNumber(entry, 'scanDepth', $event.detail.value, 1, 100, 8)" />
					</view>
					<view class="advanced-cell">
						<text class="advanced-label">优先级</text>
						<input :value="entry.priority == null ? 100 : entry.priority" type="number" class="number-input" @input="updateNumber(entry, 'priority', $event.detail.value, 0, 1000, 100)" />
					</view>
				</view>
				<view class="advanced-field advanced-field--last">
					<text class="advanced-label">注入位置</text>
					<view class="position-select" @tap="chooseInjectionPosition(entry)">{{ injectionPositionLabel(entry) }}<u-icon name="arrow-down" color="#607b89" size="18"></u-icon></view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'CharacterWorldbookEditor',
	props: { value: { type: Array, default: () => [] }, members: { type: Array, default: () => [] } },
	computed: { entries() { return this.value; } },
	methods: {
		addEntry() { this.entries.push({ clientKey: 'lore_' + Date.now() + Math.random(), title: '', memberClientKey: '', keywords: [], secondaryKeywords: [], matchMode: 'ANY', content: '', priority: 100, constantInjection: false, scanDepth: 8, injectionPosition: 'BEFORE_CHARACTER', enabled: true, advancedOpen: false }); this.$emit('input', this.entries); },
		removeEntry(index) { this.entries.splice(index, 1); this.$emit('input', this.entries); },
		keywordsText(entry) { return Array.isArray(entry.keywords) ? entry.keywords.join(', ') : ''; },
		secondaryKeywordsText(entry) { return Array.isArray(entry.secondaryKeywords) ? entry.secondaryKeywords.join(', ') : ''; },
		parseKeywords(value) { return String(value || '').split(/[,，]/).map(item => item.trim()).filter(Boolean).slice(0, 100); },
		updateKeywords(entry, value) { this.$set(entry, 'keywords', this.parseKeywords(value)); this.$emit('input', this.entries); },
		updateSecondaryKeywords(entry, value) { this.$set(entry, 'secondaryKeywords', this.parseKeywords(value)); this.$emit('input', this.entries); },
		toggleAdvanced(entry) { this.$set(entry, 'advancedOpen', !entry.advancedOpen); },
		setMatchMode(entry, mode) { this.$set(entry, 'matchMode', mode === 'ALL' ? 'ALL' : 'ANY'); this.$emit('input', this.entries); },
		updateNumber(entry, key, value, min, max, fallback) {
			const parsed = Math.floor(Number(value));
			this.$set(entry, key, Number.isFinite(parsed) ? Math.max(min, Math.min(max, parsed)) : fallback);
			this.$emit('input', this.entries);
		},
		scopeLabel(entry) { const member = this.members.find(item => item.clientKey === entry.memberClientKey); return member ? member.name || '指定成员' : '全体共享'; },
		chooseScope(entry) {
			const labels = ['全体共享'].concat(this.members.map(item => item.name || '未命名角色'));
			uni.showActionSheet({ itemList: labels, success: ({ tapIndex }) => { entry.memberClientKey = tapIndex === 0 ? '' : this.members[tapIndex - 1].clientKey; this.$emit('input', this.entries); } });
		},
		injectionPositionLabel(entry) {
			const value = String(entry && entry.injectionPosition || 'BEFORE_CHARACTER');
			if (value === 'AFTER_CHARACTER') return '角色设定之后';
			if (value === 'BEFORE_HISTORY') return '聊天历史之前';
			return '角色设定之前';
		},
		chooseInjectionPosition(entry) {
			const values = ['BEFORE_CHARACTER', 'AFTER_CHARACTER', 'BEFORE_HISTORY'];
			uni.showActionSheet({ itemList: ['角色设定之前', '角色设定之后', '聊天历史之前'], success: ({ tapIndex }) => {
				this.$set(entry, 'injectionPosition', values[tapIndex] || values[0]);
				this.$emit('input', this.entries);
			}});
		}
	}
};
</script>

<style scoped lang="scss">
.studio-section { color: #17394b; }
.section-head, .entry-head, .entry-options, .option-toggle, .scope-select, .empty-row { display: flex; align-items: center; }
.section-head { justify-content: space-between; margin-bottom: 12rpx; }
.section-title { display: block; font-size: 30rpx; font-weight: 750; }
.section-sub { display: block; margin-top: 6rpx; font-size: 22rpx; color: #6a8290; }
.icon-action, .remove-action { width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; }
.empty-row { min-height: 140rpx; justify-content: center; gap: 12rpx; color: #78909c; font-size: 24rpx; border-top: 1rpx solid rgba(72,111,132,.14); }
.entry-row { padding: 20rpx 0; border-top: 1rpx solid rgba(72,111,132,.16); }
.entry-head { gap: 10rpx; }
.entry-title { flex: 1; height: 58rpx; font-size: 27rpx; font-weight: 700; color: #17394b; border-bottom: 1rpx solid rgba(72,111,132,.15); }
.keyword-input { width: 100%; height: 66rpx; margin-top: 6rpx; font-size: 22rpx; color: #496b7c; border-bottom: 1rpx solid rgba(72,111,132,.12); }
.entry-content { width: 100%; min-height: 130rpx; box-sizing: border-box; margin-top: 12rpx; padding: 15rpx 17rpx; border-radius: 8rpx; background: rgba(255,255,255,.56); border: 1rpx solid rgba(72,111,132,.16); color: #17394b; font-size: 24rpx; line-height: 1.58; }
.entry-options { justify-content: space-between; gap: 12rpx; margin-top: 12rpx; font-size: 21rpx; color: #607b89; }
.option-toggle, .scope-select, .advanced-toggle, .position-select { display: flex; align-items: center; gap: 6rpx; }
.scope-select { min-width: 0; }
.advanced-toggle { margin-left: auto; white-space: nowrap; }
.entry-advanced { margin-top: 14rpx; padding: 14rpx 0 2rpx; border-top: 1rpx solid rgba(72,111,132,.14); }
.advanced-field + .advanced-field, .advanced-grid + .advanced-field { margin-top: 14rpx; }
.advanced-label { display: block; margin-bottom: 7rpx; font-size: 20rpx; font-weight: 650; color: #557485; }
.advanced-input, .number-input { width: 100%; box-sizing: border-box; height: 54rpx; border-bottom: 1rpx solid rgba(72,111,132,.14); color: #365b6e; font-size: 22rpx; }
.segmented-control { display: grid; grid-template-columns: 1fr 1fr; min-height: 58rpx; padding: 4rpx; border: 1rpx solid rgba(72,111,132,.16); border-radius: 8rpx; background: rgba(225,238,244,.5); }
.segment-option { display: flex; align-items: center; justify-content: center; font-size: 21rpx; color: #6a8290; }
.segment-option--on { color: #17394b; font-weight: 700; background: rgba(255,255,255,.82); border-radius: 6rpx; }
.advanced-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 14rpx; }
.advanced-cell { min-width: 0; }
.position-select { min-height: 52rpx; justify-content: space-between; color: #365f72; font-size: 22rpx; border-bottom: 1rpx solid rgba(72,111,132,.14); }
.advanced-field--last { padding-bottom: 4rpx; }
</style>
