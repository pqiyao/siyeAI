import { socket } from '@/common/api.js';

// 配置常量
const HEARTBEAT_INTERVAL = 5000; // 心跳间隔 20 秒
const RECONNECT_INTERVAL = 3000;  // 重连间隔 3 秒
const HEARTBEAT_TIMEOUT = 5000;   // 心跳超时 5 秒
const MESSAGE_TIMEOUT = 10000;    // 消息超时丢弃 10 秒

class WebSocketManager {
	constructor() {
		this.socketTask = null;        
		this.socketIsOpen = false;     
		this.isSocketClose = false;    
		this.reconnectTimer = null;    
		this.heartbeatTimer = null;    
		this.heartbeatTimeoutTimer = null; 
		this.msgs = { message: {} };

		this.sendQueue = []; // 消息发送队列

		this.init();
	}

	async init() {
		const systemInfo = await this.getSystemInfo();
		this.platform = systemInfo.platform;
	}

	getSystemInfo() {
		return new Promise((resolve) => {
			uni.getSystemInfo({
				success: (res) => resolve(res),
				fail: () => resolve({ platform: '' })
			});
		});
	}

	connectSocket() {
		if (this.socketIsOpen) return; 
		this.isSocketClose = false;
		this.clearTimers();

		this.socketTask = uni.connectSocket({
			url: socket,
			fail: (err) => {
				console.error('WebSocket连接失败:', err);
				this.scheduleReconnect();
			}
		});

		this.setupEventHandlers();
	}

	setupEventHandlers() {
		this.socketTask.onOpen(() => {
			console.log('WebSocket连接已打开');
			this.socketIsOpen = true;
			this.startHeartbeat();

			// 连接成功后发送队列中消息
			while (this.sendQueue.length > 0) {
				const { data, time } = this.sendQueue.shift();
				if (Date.now() - time < MESSAGE_TIMEOUT) {
					this._doSend(data);
				} else {
					console.warn('丢弃过期消息:', data);
				}
			}
		});

		this.socketTask.onError((err) => {
			console.error('WebSocket连接错误:', err);
			this.socketIsOpen = false;
			this.scheduleReconnect();
		});

		this.socketTask.onClose(() => {
			console.log('WebSocket连接已关闭');
			this.socketIsOpen = false;
			this.scheduleReconnect();
		});

		this.socketTask.onMessage((res) => {
			this.handleMessage(res);
		});
	}

	handleMessage(res) {
		try {
			const msg = JSON.parse(res.data);
			console.log("收到消息:", msg);
			if(msg.type == "getOnline" && msg.data.length){
				uni.$emit('refMessage', msg.data);
			}
			this.resetHeartbeat();
		} catch (error) {
			console.error('消息解析错误:', error);
		}
	}

	startHeartbeat() {
		this.clearHeartbeat();
		this.heartbeatTimer = setInterval(() => {
			this.sendHeartbeat();
		}, HEARTBEAT_INTERVAL);
	}

	sendHeartbeat() {
		if (!this.socketIsOpen) return;

		try {
			const conversations = uni.getStorageSync('conversationsList') || [];
			const userIds = conversations.map(item => item.userId).filter(Boolean);
			const user = uni.getStorageSync('user') || {};
			const message = {
				uid: user.user_id || '',
				user_ids: userIds.join()
			};
			console.log('发送心跳:', JSON.stringify(message));

			this.socketTask.send({ data: JSON.stringify(message) });

			this.clearHeartbeatTimeout();
			this.heartbeatTimeoutTimer = setTimeout(() => {
				console.warn('心跳超时，尝试重连');
				this.scheduleReconnect();
			}, HEARTBEAT_TIMEOUT);

		} catch (error) {
			console.error('发送心跳失败:', error);
			this.scheduleReconnect();
		}
	}

	resetHeartbeat() {
		this.clearHeartbeatTimeout();
	}

	clearHeartbeat() {
		if (this.heartbeatTimer) {
			clearInterval(this.heartbeatTimer);
			this.heartbeatTimer = null;
		}
		this.clearHeartbeatTimeout();
	}

	clearHeartbeatTimeout() {
		if (this.heartbeatTimeoutTimer) {
			clearTimeout(this.heartbeatTimeoutTimer);
			this.heartbeatTimeoutTimer = null;
		}
	}

	scheduleReconnect() {
		this.clearTimers();
		if (!this.isSocketClose && !this.reconnectTimer) {
			console.log('开始断线重连...');
			this.reconnectTimer = setInterval(() => {
				if (uni.getStorageSync('token')) {
					this.connectSocket();
				}
			}, RECONNECT_INTERVAL);
		}
	}

	closeSocket() {
		this.isSocketClose = true;
		this.clearTimers();
		if (this.socketTask) {
			try {
				this.socketTask.close({
					success: () => console.log('WebSocket已关闭'),
					fail: (err) => console.error('关闭WebSocket失败:', err)
				});
			} catch (error) {
				console.error('关闭WebSocket异常:', error);
			} finally {
				this.socketTask = null;
				this.socketIsOpen = false;
			}
		}
	}

	clearTimers() {
		this.clearHeartbeat();
		if (this.reconnectTimer) {
			clearInterval(this.reconnectTimer);
			this.reconnectTimer = null;
		}
	}

	setMsg(message) {
		this.msgs.message = message;
	}

	// 统一的发送方法
	send(message) {
		const messageStr = JSON.stringify(message);
		if (!this.socketTask || !this.socketIsOpen) {
			console.warn('WebSocket未连接，消息加入队列等待发送:', messageStr);
			this.sendQueue.push({
				data: messageStr,
				time: Date.now()
			});
			return false;
		}
		return this._doSend(messageStr);
	}

	_doSend(messageStr) {
		try {
			this.socketTask.send({ data: messageStr });
			console.log('发送消息成功:', messageStr);
			return true;
		} catch (error) {
			console.error('发送消息失败:', error);
			return false;
		}
	}
	
	safeClose() {
	    console.log('执行安全退出 WebSocket');
	    this.isSocketClose = true; // 标记为手动关闭，阻止重连
	    this.clearTimers();        // 清掉所有心跳、重连定时器
	    this.sendQueue = [];       // 清空待发送队列
	
	    if (this.socketTask) {
	        try {
	            this.socketTask.close({
	                success: () => console.log('WebSocket安全退出成功'),
	                fail: (err) => console.error('安全退出时关闭WebSocket失败:', err)
	            });
	        } catch (error) {
	            console.error('安全退出时关闭WebSocket异常:', error);
	        } finally {
	            this.socketTask = null;
	            this.socketIsOpen = false;
	        }
	    }
	}
	
}

// 单例
const wsManager = new WebSocketManager();

export default {
	connectSocket: () => wsManager.connectSocket(),
	closeSocket: () => wsManager.closeSocket(),
	send: (message) => wsManager.send(message),
	setMsg: (message) => wsManager.setMsg(message),
	safeClose: () => wsManager.safeClose(),
	msgs: wsManager.msgs
};
