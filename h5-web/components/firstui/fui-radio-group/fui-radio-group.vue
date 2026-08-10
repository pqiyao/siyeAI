<template>
	<!--本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID：2 5  44，营业执照号： 91   44   0 6  05MAC0 Y3  J7   0 Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。-->
	<!-- #ifdef APP-PLUS || H5 || MP-ALIPAY || MP-TOUTIAO|| MP-KUAISHOU || MP-JD || MP-360 || MP-LARK  -->
	<radio-group :name="name">
		<slot></slot>
	</radio-group>
	<!-- #endif -->

	<!-- #ifdef MP-WEIXIN || MP-BAIDU || MP-QQ -->
	<fui-form-field :name="name" v-model="val">
		<slot></slot>
	</fui-form-field>
	<!-- #endif -->
</template>

<script>
	export default {
		name: "fui-radio-group",
		emits: ['change', 'input', 'update:modelValue'],
		// #ifdef MP-WEIXIN
		behaviors: ['wx://form-field-group'],
		// #endif
		// #ifdef MP-BAIDU || MP-QQ || H5
		behaviors: ['uni://form-field'],
		// #endif
		props: {
			name: {
				type: String,
				default: ''
			},
			// #ifdef VUE3
			modelValue: {
				type: String,
				default: ''
			},
			// #endif
			value: {
				type: String,
				default: ''
			}
		},
		data() {
			return {
				val: ''
			}
		},
		watch: {
			// #ifdef VUE3
			modelValue(val) {
				this.modelChange(val)
			},
			// #endif
			value(val) {
				this.modelChange(val)
			}
		},
		created() {
			this.childrens = []
		},
		methods: {
			radioChange(e) {
				this.$emit('change', e)
				// TODO vue2 兼容
				this.$emit('input', e.detail.value)
				// TODO vue3 兼容
				// #ifdef VUE3
				this.$emit("update:modelValue", e.detail.value);
				// #endif
			},
			changeValue(value, target) {
				if (value === this.val) return;
				this.val = value;
				this.childrens.forEach(item => {
					if (item !== target) {
						item.val = false;
					}
				})
				let e = {
					detail: {
						value: value
					}
				}
				this.radioChange(e)
			},
			modelChange(val) {
				this.childrens.forEach(item => {
					if (item.value === val) {
						item.val = true;
					} else {
						item.val = false;
					}
				})
			}
		}
	}
</script>
<style scoped></style>