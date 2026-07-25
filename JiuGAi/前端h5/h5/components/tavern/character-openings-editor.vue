<template>
	<view class="studio-section">
		<view class="section-head">
			<view>
				<text class="section-title">开场场景</text>
				<text class="section-sub">每个场景可以拥有不同情境和发言顺序</text>
			</view>
			<view v-if="openings.length < 20" class="icon-action" @tap="addOpening"><u-icon name="plus" color="#245b73" size="30"></u-icon></view>
		</view>

		<view v-for="(opening, openingIndex) in openings" :key="opening.clientKey || openingIndex" class="opening-block">
			<view class="opening-head">
				<view class="default-toggle" @tap="makeDefault(openingIndex)">
					<u-icon :name="opening.defaultOpening ? 'checkmark-circle-fill' : 'radio-button-off'" color="#2d7994" size="30"></u-icon>
				</view>
				<input v-model="opening.title" maxlength="80" class="opening-title" :placeholder="'开场 ' + (openingIndex + 1)" />
				<view class="opening-actions">
					<u-icon v-if="openingIndex > 0" name="arrow-up" color="#607b89" size="24" @tap.stop="moveOpening(openingIndex, -1)"></u-icon>
					<u-icon v-if="openingIndex < openings.length - 1" name="arrow-down" color="#607b89" size="24" @tap.stop="moveOpening(openingIndex, 1)"></u-icon>
					<u-icon v-if="openings.length > 1" name="trash" color="#9b5c67" size="26" @tap.stop="removeOpening(openingIndex)"></u-icon>
				</view>
			</view>
			<input v-model="opening.summary" maxlength="255" class="summary-input" placeholder="简短描述这个开场，方便选择" />
			<textarea v-model="opening.scenarioOverride" maxlength="12000" auto-height class="scenario-input" placeholder="可选：仅这个开场使用的情境补充"></textarea>

			<view v-for="(segment, segmentIndex) in opening.segments" :key="segment.clientKey || segmentIndex" class="segment-row">
				<view class="speaker-select" @tap="chooseSpeaker(openingIndex, segmentIndex)">
					<text>{{ speakerLabel(segment) }}</text><u-icon name="arrow-down" color="#607b89" size="20"></u-icon>
				</view>
				<textarea v-model="segment.content" maxlength="12000" auto-height class="segment-content" placeholder="输入这段开场内容"></textarea>
				<view v-if="opening.segments.length > 1" class="segment-remove" @tap="removeSegment(openingIndex, segmentIndex)"><u-icon name="close" color="#7d6670" size="24"></u-icon></view>
			</view>
			<view v-if="opening.segments.length < 20" class="text-action" @tap="addSegment(openingIndex)">+ 添加一段发言</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'CharacterOpeningsEditor',
	props: {
		value: { type: Array, default: () => [] },
		members: { type: Array, default: () => [] }
	},
	computed: { openings() { return this.value; } },
	methods: {
		newSegment() { return { clientKey: 'segment_' + Date.now() + Math.random(), speakerClientKey: (this.members[0] || {}).clientKey || '', speakerType: 'CHARACTER', content: '' }; },
		newOpening() { return { clientKey: 'opening_' + Date.now() + Math.random(), title: '', summary: '', scenarioOverride: '', defaultOpening: !this.openings.length, segments: [this.newSegment()] }; },
		addOpening() { this.openings.push(this.newOpening()); this.$emit('input', this.openings); },
		removeOpening(index) { const wasDefault = !!this.openings[index].defaultOpening; this.openings.splice(index, 1); if (wasDefault && this.openings.length) this.makeDefault(0); this.$emit('input', this.openings); },
		moveOpening(index, offset) { const target = index + offset; if (target < 0 || target >= this.openings.length) return; const moved = this.openings.splice(index, 1)[0]; this.openings.splice(target, 0, moved); this.$emit('input', this.openings); },
		makeDefault(index) { this.openings.forEach((item, i) => { item.defaultOpening = i === index; }); this.$emit('input', this.openings); },
		addSegment(index) { this.openings[index].segments.push(this.newSegment()); this.$emit('input', this.openings); },
		removeSegment(openingIndex, segmentIndex) { this.openings[openingIndex].segments.splice(segmentIndex, 1); this.$emit('input', this.openings); },
		speakerLabel(segment) {
			if (segment.speakerType === 'NARRATOR') return '旁白';
			const member = this.members.find(item => item.clientKey === segment.speakerClientKey);
			return (member && member.name) || '选择角色';
		},
		chooseSpeaker(openingIndex, segmentIndex) {
			const labels = this.members.map(item => item.name || '未命名角色').concat(['旁白']);
			uni.showActionSheet({ itemList: labels, success: ({ tapIndex }) => {
				const segment = this.openings[openingIndex].segments[segmentIndex];
				if (tapIndex === this.members.length) { segment.speakerType = 'NARRATOR'; segment.speakerClientKey = ''; }
				else { segment.speakerType = 'CHARACTER'; segment.speakerClientKey = this.members[tapIndex].clientKey; }
				this.$emit('input', this.openings);
			}});
		}
	}
};
</script>

<style scoped lang="scss">
.studio-section { color: #17394b; }
.section-head, .opening-head, .speaker-select { display: flex; align-items: center; }
.section-head { justify-content: space-between; margin-bottom: 12rpx; }
.section-title { display: block; font-size: 30rpx; font-weight: 750; }
.section-sub { display: block; margin-top: 6rpx; font-size: 22rpx; color: #6a8290; }
.icon-action, .remove-action, .default-toggle, .segment-remove { display: flex; align-items: center; justify-content: center; width: 60rpx; height: 60rpx; }
.opening-actions { flex: 0 0 auto; min-width: 54rpx; min-height: 58rpx; display: flex; align-items: center; justify-content: flex-end; gap: 14rpx; }
.opening-block { padding: 22rpx 0; border-top: 1rpx solid rgba(72,111,132,.16); }
.opening-block:first-of-type { border-top: 0; }
.opening-head { gap: 8rpx; }
.opening-title { flex: 1; height: 58rpx; font-size: 28rpx; font-weight: 700; color: #17394b; border-bottom: 1rpx solid rgba(72,111,132,.15); }
.summary-input { width: 100%; height: 70rpx; margin-top: 8rpx; font-size: 23rpx; color: #496b7c; border-bottom: 1rpx solid rgba(72,111,132,.12); }
.scenario-input, .segment-content { box-sizing: border-box; background: rgba(255,255,255,.56); border: 1rpx solid rgba(72,111,132,.16); border-radius: 8rpx; color: #17394b; font-size: 24rpx; line-height: 1.55; }
.scenario-input { width: 100%; min-height: 100rpx; margin-top: 12rpx; padding: 14rpx 16rpx; }
.segment-row { position: relative; margin-top: 14rpx; padding-left: 150rpx; }
.speaker-select { position: absolute; left: 0; top: 0; width: 136rpx; height: 64rpx; justify-content: space-between; font-size: 22rpx; color: #365f72; border-bottom: 1rpx solid rgba(72,111,132,.18); }
.segment-content { width: 100%; min-height: 112rpx; padding: 14rpx 46rpx 14rpx 16rpx; }
.segment-remove { position: absolute; right: 0; top: 0; }
.text-action { padding: 18rpx 0 4rpx; font-size: 23rpx; color: #246681; }
</style>
