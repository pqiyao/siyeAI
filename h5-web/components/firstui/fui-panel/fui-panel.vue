<template>
	<!--本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 25 4 4，营业执照号：91 4     40 6  0   5M AC 0 Y3J  70Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。-->
	<view class="fui-panel__wrap"
		:style="{background:background,'border-top-color':borderColor,'border-bottom-color':borderColor,marginTop:marginTop+'rpx',marginBottom:marginBottom+'rpx'}"
		:class="{'fui-panel__unlined':!isBorder,'fui-panel__background':!background}">
		<view class="fui-panel__hd"
			:style="{background:hdBackground,paddingLeft:padding+'rpx',paddingRight:padding+'rpx'}"
			v-if="panelData[head]">
			<text :class="{'fui-panel__title-color':!headColor}"
				:style="{fontSize:headSize+'rpx',color:headColor+'rpx'}">{{panelData[head] || ''}}</text>
			<view v-if="hdBorder" :style="{background:borderColor,left:hdLeft+'rpx',right:hdRight+'rpx'}"
				class="fui-panel__border" :class="{'fui-panel__border-color':!borderColor}"></view>
		</view>
		<view class="fui-panel__mediabox" :style="{paddingLeft:padding+'rpx',paddingRight:padding+'rpx'}"
			:class="{'fui-mediabox__center':!flexStart,'fui-panel__hover-weex':highlight}" v-for="(item,index) in listData"
			:key="index" :hover-class="highlight?'fui-panel__hover':''" :hover-stay-time="150"
			@tap="handleClick(index)">
			<view class="fui-panel__mb-hd" v-if="item[src]" :style="{width:width+'rpx',height:height+'rpx'}">
				<image class="fui-panel__mb-thumb" :src="item[src]" mode="widthFix"
					:style="{width:width+'rpx',height:height+'rpx',borderRadius:radius+'rpx'}" />
			</view>
			<view class="fui-panel__mb-bd">
				<text class="fui-panel__mb-title" v-if="item[title]" :class="{'fui-panel__title-color':!color}"
					:style="{color:color,fontSize:size+'rpx',fontWeight:fontWeight}">{{item[title] || ''}}</text>
				<text class="fui-panel__mb-desc" v-if="item[desc]" :class="{'fui-panel__sub-color':!descColor}"
					:style="{color:descColor,fontSize:descSize+'rpx'}">{{item[desc] || ''}}</text>
				<view class="fui-panel__mb-info" v-if="item[source] || item[time] || item[extra]">
					<text class="fui-panel__info-meta" :class="{'fui-panel__info-color':!infoColor}"
						:style="{color:infoColor,fontSize:infoSize+'rpx'}"
						v-if="item[source]">{{item[source] || ''}}</text>
					<text class="fui-panel__info-meta" :class="{'fui-panel__info-color':!infoColor}"
						:style="{color:infoColor,fontSize:infoSize+'rpx'}" v-if="item[time]">{{item[time] || ''}}</text>
					<text class="fui-panel__info-extra"
						:class="{'fui-panel__info-color':!infoColor,'fui-panel__extra-bcolor':!infoColor}"
						:style="{color:infoColor,fontSize:infoSize+'rpx','border-left-color':infoColor}"
						v-if="item[extra]">{{item[extra] || ''}}</text>
				</view>
			</view>
			<view v-if="bdBorder && index!==listData.length-1"
				:style="{background:borderColor,left:bdLeft+'rpx',right:bdRight+'rpx'}" class="fui-panel__border"
				:class="{'fui-panel__border-color':!borderColor}"></view>
		</view>
		<slot></slot>
	</view>
</template>

<script>
	export default {
		name: "fui-panel",
		emits: ['click'],
		props: {
			//面板数据，键名可通过 fields属性配置
			panelData: {
				type: Object,
				default () {
					return {}
				}
			},
			//面板数据源键名（key）
			fields: {
				type: Object,
				default () {
					return {}
				}
			},
			background: {
				type: String,
				// #ifdef APP-NVUE
				default: '#fff'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			},
			//是否有点击效果
			highlight: {
				type: Boolean,
				default: true
			},
			flexStart: {
				type: Boolean,
				default: false
			},
			marginTop: {
				type: [Number, String],
				default: 0
			},
			marginBottom: {
				type: [Number, String],
				default: 0
			},
			//左右间距
			padding: {
				type: [Number, String],
				default: 32
			},
			hdBorder: {
				type: Boolean,
				default: true
			},
			hdLeft: {
				type: [Number, String],
				default: 32
			},
			hdRight: {
				type: [Number, String],
				default: 0
			},
			hdBackground: {
				type: String,
				default: '#fff'
			},
			headSize: {
				type: [Number, String],
				default: 32
			},
			headColor: {
				type: String,
				// #ifdef APP-NVUE
				default: '#181818'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			},
			bdBorder: {
				type: Boolean,
				default: true
			},
			bdLeft: {
				type: [Number, String],
				default: 32
			},
			bdRight: {
				type: [Number, String],
				default: 0
			},
			//图片宽度
			width: {
				type: [Number, String],
				default: 120
			},
			//图片高度
			height: {
				type: [Number, String],
				default: 120
			},
			radius: {
				type: [Number, String],
				default: 0
			},
			size: {
				type: [Number, String],
				default: 32
			},
			color: {
				type: String,
				// #ifdef APP-NVUE
				default: '#181818'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			},
			fontWeight: {
				type: [Number, String],
				default: 'normal'
			},
			descSize: {
				type: [Number, String],
				default: 28
			},
			descColor: {
				type: String,
				// #ifdef APP-NVUE
				default: '#7F7F7F'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			},
			infoSize: {
				type: [Number, String],
				default: 24
			},
			infoColor: {
				type: String,
				// #ifdef APP-NVUE
				default: '#b2b2b2'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			},
			//是否需要panel外层上下线条
			isBorder: {
				type: Boolean,
				default: true
			},
			borderColor: {
				type: String,
				// #ifdef APP-NVUE
				default: '#eee'
				// #endif
				// #ifndef APP-NVUE
				default: ''
				// #endif
			}

		},
		data() {
			return {
				listData: [],
				head: 'head',
				list: 'list',
				src: 'src',
				title: 'title',
				desc: 'desc',
				source: 'source',
				time: 'time',
				extra: 'extra'
			};
		},
		watch: {
			panelData: {
				handler(val) {
					this.initData(val)
				},
				deep: true
			},
			//未做深度监听
			fields(val){
				this.handleFileds()
			}
		},
		created() {
			this.initData(this.panelData)
		},
		methods: {
			handleFileds() {
				if (this.fields && typeof this.fields === 'object') {
					this.head = this.fields.head || 'head';
					this.list = this.fields.list || 'list';
					this.src = this.fields.src || 'src';
					this.title = this.fields.title || 'title';
					this.desc = this.fields.desc || 'desc';
					this.source = this.fields.source || 'source';
					this.time = this.fields.time || 'time';
					this.extra = this.fields.extra || 'extra';
				}
			},
			initData(val) {
				this.handleFileds()
				val = val || {};
				let list = val[this.list] && [...val[this.list]]
				if (val[this.list] && Array.isArray(list)) {
					this.listData = list;
				} else {
					this.listData = [val];
				}
			},
			handleClick(index) {
				this.$emit('click', {
					index: index,
					...this.listData[index]
				})
			}
		}
	}
</script>

<style scoped>
	.fui-panel__wrap {
		/* #ifndef APP-NVUE */
		width: 100%;
		border-top-width: 0;
		border-bottom-width: 0;
		/* #endif */
		flex: 1;
		position: relative;
		overflow: hidden;

		/* #ifdef APP-NVUE */
		border-top-width: 0.5px;
		border-top-style: solid;
		border-bottom-width: 0.5px;
		border-bottom-style: solid;
		/* #endif */
	}

	/* #ifndef APP-NVUE */
	.fui-panel__background {
		background: var(--fui-bg-color, #FFFFFF);
	}

	/* #endif */
	.fui-panel__unlined {
		border-top-width: 0 !important;
		border-bottom-width: 0 !important;
	}

	/* #ifndef APP-NVUE */
	.fui-panel__wrap::before {
		top: 0;
		border-top: 1px solid var(--fui-color-border, #EEEEEE);
		-webkit-transform-origin: 0 0;
		transform-origin: 0 0;
		-webkit-transform: scaleY(.5);
		transform: scaleY(.5)
	}

	.fui-panel__wrap:after{
		content: " ";
		position: absolute;
		left: 0;
		right: 0;
		height: 1px;
	}
	.fui-panel__wrap::before {
		content: " ";
		position: absolute;
		left: 0;
		right: 0;
		height: 1px;
	}

	.fui-panel__wrap::after {
		bottom: 0;
		border-bottom: 1px solid var(--fui-color-border, #EEEEEE);
		-webkit-transform-origin: 0 100%;
		transform-origin: 0 100%;
		-webkit-transform: scaleY(.5);
		transform: scaleY(.5)
	}

	.fui-panel__unlined::before{
		border-top-width: 0 !important;
		border-bottom-width: 0 !important;
	}
	.fui-panel__unlined::after {
		border-top-width: 0 !important;
		border-bottom-width: 0 !important;
	}

	/* #endif */

	.fui-panel__hd {
		/* #ifndef APP-NVUE */
		width: 100%;
		display: flex;
		word-break: break-all;
		box-sizing: border-box;
		/* #endif */
		flex: 1;
		padding-top: 26rpx;
		padding-bottom: 26rpx;
		font-weight: 700;
		position: relative;
		flex-direction: row;
		align-items: center;
	}

	.fui-panel__border {
		position: absolute;
		bottom: 0;
		/* #ifdef APP-NVUE */
		height: 0.5px;
		z-index: -1;
		/* #endif */
		/* #ifndef APP-NVUE */
		height: 1px;
		-webkit-transform: scaleY(0.5);
		transform: scaleY(0.5);
		transform-origin: 0 100%;
		z-index: 1;
		/* #endif */
	}

	/* #ifndef APP-NVUE */
	.fui-panel__border-color {
		background: var(--fui-color-border, #EEEEEE) !important;
	}

	/* #endif */

	.fui-panel__mediabox {
		padding-top: 32rpx;
		padding-bottom: 32rpx;
		position: relative;
		/* #ifndef APP-NVUE */
		display: flex;
		/* #endif */
		flex-direction: row;
	}

	.fui-mediabox__center {
		align-items: center;
	}

	.fui-panel__mb-hd {
		margin-right: 32rpx;
		overflow: hidden;
		/* #ifndef APP-NVUE */
		display: flex;
		/* #endif */
		align-items: center;
		justify-content: center;
	}

	.fui-panel__mb-thumb {
		/* #ifndef APP-NVUE */
		display: block;
		/* #endif */
	}

	.fui-panel__mb-bd {
		flex: 1;
		overflow: hidden;
	}

	/* 暂不使用 */
	.fui-panel__mb-between {
		/* #ifndef APP-NVUE */
		display: flex;
		/* #endif */
		flex-direction: column;
		justify-content: space-between;
	}

	/* 暂不使用 */
	/* #ifndef APP-NVUE */
	.fui-panel__bd-height {
		min-height: var(--fui-img-base, 60px);
	}

	/* #endif */

	.fui-panel__mb-title {
		font-weight: normal;
		/* #ifdef APP-NVUE */
		lines: 1;
		/* #endif */

		/* #ifndef APP-NVUE */
		white-space: nowrap;
		line-height: 1.2;
		display: block;
		/* #endif */
	}

	/* #ifndef APP-NVUE */
	.fui-panel__title-color {
		color: var(--fui-color-title, #181818) !important;
	}

	/* #endif */

	.fui-panel__mb-desc,
	.fui-panel__mb-title {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.fui-panel__mb-desc {
		padding-top: 12rpx;
		font-weight: 400;
		font-size: 28rpx;
		/* #ifndef APP-NVUE */
		line-height: 1.4;
		word-break: break-all;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		/* #endif */

		/* #ifdef APP-NVUE */
		lines: 2;
		/* #endif */
	}

	/* #ifndef APP-NVUE */
	.fui-panel__sub-color {
		color: var(--fui-color-subtitle, #7F7F7F) !important;
	}

	/* #endif */

	.fui-panel__mb-info {
		/* #ifndef APP-NVUE */
		display: flex;
		/* #endif */
		align-items: center;
		flex-direction: row;
		margin-top: 32rpx;
		padding-bottom: 8rpx;
		font-weight: 400;
		line-height: 1;
		overflow: hidden;
	}

	/* #ifndef APP-NVUE */
	.fui-panel__info-color {
		color: var(--fui-color-label, #B2B2B2) !important;
	}

	/* #endif */

	.fui-panel__info-meta {
		padding-right: 32rpx;
	}

	.fui-panel__info-extra {
		padding-left: 32rpx;
		border-left-width: 1rpx;
		border-left-style: solid;
	}

	/* #ifndef APP-NVUE */
	.fui-panel__extra-bcolor {
		border-left-color: var(--fui-color-label, #B2B2B2) !important;
	}

	/* #endif */
	/* #ifndef APP-NVUE */
	.fui-panel__hover {
		background-color: var(--fui-bg-color-hover, rgba(0, 0, 0, 0.2)) !important;
	}

	/* #endif */

	/* #ifdef APP-NVUE */
	.fui-panel__hover-weex:active {
		background: rgba(0, 0, 0, 0.2) !important;
	}

	/* #endif */
</style>