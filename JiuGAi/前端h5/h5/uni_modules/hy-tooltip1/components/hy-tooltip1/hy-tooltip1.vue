<template>
	<view  id="tab"
		class="tooltip"
		@longpress="showbar"
	>
	<view v-if="show" style="position: fixed;top:0;bottom: 0;left: 0;right: 0;background-color: #0000;z-index: 1000;" @touchstart.stop.prevent="cancel"></view>
		<slot></slot>
		<view class="bar" :style="{top:isTop?top:'',bottom:isTop?'':bottom,justifyContent:positionX,left:left?left:''}">
			<view  v-show="show" class="tab-box"  :style="{color:color,backgroundColor:bgColor,fontSize:fontSize?fontSize:''}">
				<view class="bar-item" v-for="(tab,index) in tabs" :key="'bar'+index" @click="taptab(index,tab,id)">{{tab}}</view>
			</view>
		</view>
	</view>
</template>
<script>
	import{
		mapState
	}from 'vuex'
	export default {
		name:"hy-tooltip1",
		props:{
			id:{
				default:'id为空'
			},
			//tab列表
			tabs:{
				type:Array,
				default:()=>['示例']
			},
			color:{
				default:'#e0e0e0'
			},
			bgColor:{
				default:'#505050'
			},
			top:{
				default:'-34px'
			},
			bottom:{
				default:'-34px'
			},
			//是否位于上方
			isTop:{
				default:true
			},
			//x方向偏离距离
			left:{
				
			},
			//tab的水平位置
			positionX:{
				default:'center'
			},
			fontSize:{
				
			},
		},
		computed:{
			...mapState(['user'])
		},
		mounted() {
			// //计算组件宽高
			// this.$nextTick(function(){
			// 	const query = uni.createSelectorQuery().in(this);
			// 		query.select('#tab').boundingClientRect(data => {
			// 			console.log(data);
			// 		console.log("得到布局位置信息" + JSON.stringify(data));
			// 		this.boxHeight = data.height;
			// 		// this.w=data.width;
			// 		// this.h=data.height;
			// 		// this.fx=-data.left;
			// 		// console.log("节点离页面顶部的距离为" + data.top);								
			// 		}).exec();
			// })
		},
		data() {
			return {
				show:false,//bar		
			};
		},
		methods:{
			 showbar(e){
				 // console.log(e);
				 // this.x = e.touches[0].pageX;
				 // this.y = e.touches[0].pageY; 
				 if(this.id.senderId == this.user.user_id){
					 this.show=true;
				 }
			 },
			 cancel(){
				this.show=false; 
			 },
			 //点击tab
			 taptab(i,e,id){
				 // console.log(e,i);
				this.show=false; 
				this.$emit('taptab',i,e,id)
			 },			 			 			 
		}
	}	
</script>
<style>
.tooltip{
	/* width: 100%; */
	/* height: 100%; */
	position: relative;
	/* border: 1px solid blue; */
}
.bar{
	display: flex;
	/* justify-content: center; */
	z-index: 10036;
	position: absolute;
	width: 100%;
	/* overflow: visible; */
	white-space: nowrap;
}
.tab-box{
	display: flex;
	font-size: 13px;
	padding: 5px 5px;
	border-radius: 5px;
}
.bar-item{
	margin: 0 8px;
}
</style>
