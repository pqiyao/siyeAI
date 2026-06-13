// dragManager.js
import {BASE, DERIVED, EDITOR, SYSTEM, USER} from '../core/manager.js';

/**
 * @description 拖拽管理器 - 用于管理拖拽操作
 */
export class Drag {
    constructor() {
        // 初始化变换参数
        this.translateX = 0;
        this.translateY = 0;
        this.scale = 1;
        this.isDragging = false;
        this.accumulatedX = 0;
        this.accumulatedY = 0;
        this.threshold = 1;
        this.zoomValue = 0.8;
        this.zoomRange = [-2, 5];
        this.elements = new Map();

        // 新增阈值变量
        this.dragThreshold = 5; // 移动超过5px视为拖拽
        this.initialPosition = { x: 0, y: 0 };
        this.shouldDrag = false;

        // 创建容器结构
        this.dragContainer = document.createElement('div');
        this.dragContainer.style.position = 'relative';
        this.dragContainer.style.display = 'flex';
        this.dragContainer.style.flexGrow = '1';
        this.dragContainer.style.flexShrink = '0';
        this.dragContainer.style.width = '100%';
        this.dragContainer.style.height = '100%';
        this.dragContainer.style.minHeight = '500px';
        this.dragContainer.style.overflow = 'hidden';
        this.dragContainer.style.userSelect = 'none';
        this.dragContainer.style.cursor = 'grab';

        this.dragSpace = document.createElement('div');
        this.dragSpace.style.transformOrigin = '0 0';
        this.dragSpace.style.position = 'absolute';
        this.dragSpace.style.top = '0';
        this.dragSpace.style.left = '0';
        this.dragSpace.style.bottom = '0';
        // this.dragSpace.style.outline = '3px solid #41b681'
        this.dragSpace.style.willChange = 'transform';  // 优化：提示浏览器 transform 可能会变化
        this.dragSpace.style.pointerEvents = 'auto';
        this.dragContainer.appendChild(this.dragSpace);


        // 分离手机和电脑事件
        if (/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)) {
            this.dragThreshold = 1;
            this.dragSpace.style.transition = 'transform 0.05s cubic-bezier(0, 0, 0.58, 1)';
            // this.dragSpace.style.transition = 'none';
            this.dragContainer.addEventListener('touchstart', this.handleMouseDown);
        } else {
            this.dragThreshold = 5;
            this.dragSpace.style.transition = 'transform 0.12s cubic-bezier(0.22, 1, 0.36, 1)';
            this.dragContainer.addEventListener('mousedown', this.handleMouseDown);
            this.dragContainer.addEventListener('wheel', this.handleWheel, { passive: false });
        }
    }

    /**
     * 获取渲染元素，用于挂载到页面上
     * @returns {HTMLDivElement}
     */
    get render() {
        return this.dragContainer;
    }

    /**
     * 添加元素，支持设置初始位置，默认为[0, 0]
     * @example add('name', element, [100, 100])
     * @param name
     * @param element
     * @param position
     */
    add(name, element, position = [0, 0]) {
        element.style.position = 'absolute';
        element.style.left = `${position[0]}px`;
        element.style.top = `${position[1]}px`;
        this.dragSpace.appendChild(element);
        this.elements.set(name, element);
    }

    /**
     * 移动元素到指定位置，默认为[0, 0]
     * @example move('name', [100, 100])
     * @param name
     * @param position
     */
    move(name, position = [0, 0]) {
        if (this.elements.has(name)) {
            const element = this.elements.get(name);
            element.style.left = `${position[0]}px`;
            element.style.top = `${position[1]}px`;
        }
    }

    /**
     * 删除元素，同时会从页面上移除
     * @example delete('name')
     * @param name
     */
    delete(name) {
        if (this.elements.has(name)) {
            const element = this.elements.get(name);
            this.dragSpace.removeChild(element);
            this.elements.delete(name);
        }
    }


    /** ------------------ 以下为拖拽功能实现，为事件处理函数，不需要手动调用 ------------------ */
        // 鼠标按下事件
    handleMouseDown = (e) => {
        // 获取点击位置的所有元素
        // const elements = document.elementsFromPoint(e.clientX, e.clientY);
        // console.log(elements);

        if (e.button === 0 || e.type === 'touchstart') {
            let clientX, clientY, touches;
            if (e.type === 'touchstart') {
                touches = e.touches;
                if (touches.length > 0) { // 确保 touches 数组不为空
                    clientX = touches[0].clientX;
                    clientY = touches[0].clientY;
                } else {
                    return; // 如果 touches 为空，则直接返回，不处理
                }
                document.addEventListener('touchmove', this.handleFirstMove, { passive: false });
                document.addEventListener('touchend', this.handleMouseUp, { passive: true });
            } else {
                clientX = e.clientX;
                clientY = e.clientY;
                document.addEventListener('mousemove', this.handleFirstMove, { passive: false });
                document.addEventListener('mouseup', this.handleMouseUp, { passive: true });
            }

            this.initialPosition.x = clientX;
            this.initialPosition.y = clientY;

            this.isDragging = false;
            this.shouldDrag = false;
        }
    };

    handleFirstMove = (e) => {
        let clientX, clientY, touches;
        if (e.type === 'touchmove') {
            touches = e.touches;
            if (touches.length > 0) {
                clientX = touches[0].clientX;
                clientY = touches[0].clientY;
            } else {
                return;
            }
        } else {
            clientX = e.clientX;
            clientY = e.clientY;
        }

        const dx = clientX - this.initialPosition.x;
        const dy = clientY - this.initialPosition.y;

        if (Math.sqrt(dx * dx + dy * dy) > this.dragThreshold) {
            this.isDragging = true;
            this.shouldDrag = true;
            this.dragSpace.style.pointerEvents = 'none';
            this.dragContainer.style.cursor = 'grabbing';

            this.canvasStartX = (clientX - this.translateX) / this.scale;
            this.canvasStartY = (clientY - this.translateY) / this.scale;

            if (e.type === 'touchmove') {
                document.removeEventListener('touchmove', this.handleFirstMove);
                document.addEventListener('touchmove', this.handleMouseMove, { passive: false });
            } else {
                document.removeEventListener('mousemove', this.handleFirstMove);
                document.addEventListener('mousemove', this.handleMouseMove, { passive: false });
            }

            this.handleMouseMove(e);
        }
    };

    handleMouseMove = (e) => {
        if (!this.isDragging) return;

        if (e.type === 'touchmove') {
            if (e.touches.length === 0) return;
            this.translateX = e.touches[0].clientX - this.canvasStartX
            this.translateY = e.touches[0].clientY - this.canvasStartY
            this.updateTransform(); // 更新位移
            e.preventDefault();     // 阻止默认行为减少卡顿
        } else {
            const deltaX = (e.clientX - this.translateX) / this.scale - this.canvasStartX;
            const deltaY = (e.clientY - this.translateY) / this.scale - this.canvasStartY;

            this.mergeOffset(deltaX * this.scale, deltaY * this.scale);
        }
    };


    // 鼠标释放事件
    handleMouseUp = (e) => {
        // 分离手机和电脑事件
        if (e.type === 'touchend') {
            document.removeEventListener('touchmove', this.handleFirstMove);
            document.removeEventListener('touchmove', this.handleMouseMove);
            document.removeEventListener('touchend', this.handleMouseUp);
        } else {
            document.removeEventListener('mousemove', this.handleFirstMove);
            document.removeEventListener('mousemove', this.handleMouseMove);
            document.removeEventListener('mouseup', this.handleMouseUp);
        }

        // 重置状态
        this.isDragging = false;
        this.shouldDrag = false;
        this.dragSpace.style.pointerEvents = 'auto';
        this.dragContainer.style.cursor = 'grab';
    };

    // 滚轮缩放事件
    handleWheel = (e) => {
        e.preventDefault();
        const originalScale = this.scale;
        const zoomFactor = this.zoomValue ** (e.deltaY > 0 ? 1 : -1);

        // 计算新缩放比例
        let newScale = originalScale * zoomFactor;
        newScale = Math.min(
            Math.max(newScale, Math.pow(this.zoomValue, this.zoomRange[1])),
            Math.pow(this.zoomValue, this.zoomRange[0])
        );
        newScale = Math.round(newScale * 100) / 100;
        this.scale = newScale;

        // 计算缩放中心
        const rect = this.dragContainer.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const mouseY = e.clientY - rect.top;

        // 计算新的位移值
        const worldX = (mouseX - this.translateX) / originalScale;
        const worldY = (mouseY - this.translateY) / originalScale;

        const targetTranslateX = mouseX - worldX * this.scale;
        const targetTranslateY = mouseY - worldY * this.scale;

        this.mergeOffset(targetTranslateX - this.translateX, targetTranslateY - this.translateY);
        this.updateTransform();
    };

    // 应用位移量
    mergeOffset(x, y) {
        this.accumulatedX += x;
        this.accumulatedY += y;

        if (Math.abs(this.accumulatedX) > this.threshold || Math.abs(this.accumulatedY) > this.threshold) {
            const offsetX = Math.floor(this.accumulatedX / this.threshold) * this.threshold;
            const offsetY = Math.floor(this.accumulatedY / this.threshold) * this.threshold;

            this.translateX += offsetX;
            this.translateY += offsetY;
            this.accumulatedX -= offsetX;
            this.accumulatedY -= offsetY;

            this.updateTransform();
        }
    }

    updateTransform() {
        requestAnimationFrame(() => {
            // 使用transform3d触发GPU加速
            this.dragSpace.style.transform = `translate3d(${this.translateX}px, ${this.translateY}px, 0) scale(${this.scale})`;
        });
    }
}
