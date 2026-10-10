# hy-tooltip1
# `hy-tooltip1` 是一个长按显示提示消息和更多按钮气泡的组件。
# 
## 1.属性说明
| 属性名     | 类型 |	 说明	 |
|:-----------|	:----:	|:-------|
|	id	 |	String	|	需要传入的id值，根据需要可以填写。|
|   tabs  |  Array	|	传入提示消息或按钮，数组			|
|	fontSize	|	String	|	按钮文字大小，例：'15px'	|
| 	color	|	String|		按钮文字颜色	，默认：'#e0e0e0'				|
|	bgColor	|	String|		按钮背景颜色	，默认：'#505050'				|
|	isTop	|	Bool	|   按钮是否位于上方，默认：true，为false时位于下方					|
|	top		|	String	|	按钮垂直方向顶部偏移距离，在isTop为true时生效，例："-34px"		|
|	bottom		|	String	|	按钮垂直方向底部偏移距离，在isTop为false时生效，例："-34px"			|
|	positionX	|  String	|	按钮水平排列方式，默认："center",/"start",/"end"...|
|	left		|	String	|	按钮水平方向偏移距离，例："5px"	|
|	@taptab		|	EventHandle	|	按钮点击事件,数据（按钮索引,按钮名称,传入id）	|

## 2.使用示例
页面:
```html
<template>
    <view class="content">
         <hy-tooltip1 :id='id' :tabs="tab" @taptab="tap">
			<view>这里写你要长按显示气泡的内容</view>
		 </hy-tooltip1>
    </view>
</template>

```

script:
```javascript
	 export default {
		 data() {
			return {
				id:'123',
				tab:['复制','撤回','删除'],
			}
		 },
		methods: {
			tap(i,e,id){
				console.log(i,e,id);//0 复制 123,1 撤回 123,2 删除 123
			},
		}
	}

```

放在循环中使用
```html
<template>
    <view>
		<view v-for="i in 10">
			<hy-tooltip1 :id='i' :tabs="tab" @taptab="tap">
				<view>这里写你要长按显示气泡的内容</view>
			</hy-tooltip1>
		</view>
        
    </view>
</template>
```
#说明
该组件会根据外层<view></view>的大小铺满，可在该组件标签内部写你的内容，该标签外部标签为长按点击的区域。