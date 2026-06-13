
const MenuItemType = {
    normal: 'normal',
    warning: 'warning',
}

/**
 * @description 弹出菜单类 - 用于创建和管理弹出菜单
 */
export class PopupMenu {
    ItemType = MenuItemType
    /**
     * 静态属性，用于存储当前活动的 PopupMenu 实例，使其在全局范围内作为单例使用
     * @type {null}
     */
    static instance = null;

    /**
     * 构造函数
     * @param {object} [options] - 可选配置项
     * @param {boolean} [options.lasting=false] - 是否持久化，为 true 时点击外部或菜单项点击后不销毁实例，只隐藏
     */
    constructor(options = {}) {
        if (PopupMenu.instance) {
            PopupMenu.instance.destroy();
        }

        this.menuItems = [];
        this.lasting = false;
        this.popupContainer = null;
        this._closePromise = null;
        this._closeResolver = null;
        this._frameUpdateId = null;

        this.#init(options);
        PopupMenu.instance = this;
    }

    add(html, event, type = MenuItemType.normal) {
        const index = this.menuItems.length;
        this.menuItems.push({ html, event, type });
        this.menuItemIndexMap.set(html, index); // 存储 HTML 内容与索引的映射
    }

    renderMenu() {
        this.menuContainer.innerHTML = '';

        this.menuItems.forEach((item, index, type) => {
            const menuItem = document.createElement('div');
            menuItem.innerHTML = item.html;
            menuItem.style.padding = '5px';
            menuItem.style.cursor = 'pointer';
            menuItem.style.userSelect = 'none';
            menuItem.style.justifyContent = 'flex-start';
            menuItem.style.alignItems = 'center';
            menuItem.classList.add('dynamic-popup-menu-item', 'list-group-item');

            if (item.type === MenuItemType.warning) {
                menuItem.classList.add('redWarningText');
            }

            this.menuContainer.appendChild(menuItem);

            // 存储菜单项元素与索引的映射
            this.menuItemIndexMap.set(menuItem, index);
        });

        return this.popupContainer;
    }

    /**
     * 显示菜单
     * @param {number} x - 菜单显示的横坐标 (相对于父元素)
     * @param {number} y - 菜单显示的纵坐标 (相对于父元素)
     * @returns {Promise} 返回一个 Promise，在菜单关闭时 resolve
     */
    async show(x = 0, y = 0) {
        // 清理之前的关闭 Promise
        if (this._closePromise) {
            this._closeResolver?.();
            this._closePromise = null;
            this._closeResolver = null;
        }

        this.popupContainer.style.left = `${x}px`;
        this.popupContainer.style.top = `${y}px`;
        this.popupContainer.style.display = 'block';
        this.popupContainer.style.zIndex = '9999';

        // 创建新的 Promise 用于跟踪关闭事件
        this._closePromise = new Promise((resolve) => {
            this._closeResolver = resolve;
        });

        setTimeout(() => {
            document.addEventListener('click', this.handleClickOutside.bind(this));
        }, 0);

        return this._closePromise;
    }

    /**
     * 隐藏菜单
     */
    hide() {
        this.cancelFrameUpdate();
        this.popupContainer.style.display = 'none';
        document.removeEventListener('click', this.handleClickOutside.bind(this));

        // 触发关闭 Promise 的 resolve
        this._closeResolver?.();
        this._closePromise = null;
        this._closeResolver = null;
    }

    /**
     * 销毁菜单
     */
    destroy() {
        this.cancelFrameUpdate();
        document.removeEventListener('click', this.handleClickOutside.bind(this));
        if (this.popupContainer.parentNode) {
            this.popupContainer.parentNode.removeChild(this.popupContainer);
        }

        // 触发关闭 Promise 的 resolve
        this._closeResolver?.();
        this._closePromise = null;
        this._closeResolver = null;
    }

    #init(options) {
        this.menuItems = [];
        this.lasting = options.lasting === true;
        this.menuItemIndexMap = new Map();      // 使用 Map 存储菜单项与其索引的映射关系

        this.popupContainer = document.createElement('div');
        this.popupContainer.style.position = 'absolute';
        this.popupContainer.style.display = 'none';
        this.popupContainer.style.zIndex = '1000';
        this.popupContainer.style.width = '180px';
        this.popupContainer.style.height = 'auto';
        this.popupContainer.style.background = 'none';
        this.popupContainer.style.border = 'none';
        this.popupContainer.style.borderRadius = '6px';
        this.popupContainer.style.boxShadow = '0 0 20px rgba(0,0,0,0.2)';
        this.popupContainer.style.backgroundColor = 'var(--SmartThemeBlurTintColor)';

        this.menuContainer = $('<div class="dynamic-popup-menu" id="dynamic_popup_menu"></div>')[0];
        this.menuContainer.style.position = 'relative';
        this.menuContainer.style.padding = '2px 0';
        this.menuContainer.style.backgroundColor = 'var(--SmartThemeUserMesBlurTintColor)';
        this.menuContainer.style.backdropFilter = 'blur(calc(var(--SmartThemeBlurStrength)*2))';
        this.menuContainer.style.webkitBackdropFilter = 'blur(var(--SmartThemeBlurStrength))';
        this.menuContainer.style.border = '1px solid var(--SmartThemeBorderColor)';
        this.menuContainer.style.borderRadius = '6px';

        this.popupContainer.appendChild(this.menuContainer);

        this.handleMenuItemClick = this.handleMenuItemClick.bind(this);
        this.handleClickOutside = this.handleClickOutside.bind(this);
        this.popupContainer.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            this.handleMenuItemClick(event);
        });
    }

    handleMenuItemClick(event) {
        const menuItemElement = event.target.closest('.dynamic-popup-menu-item');
        if (menuItemElement) {
            // 直接从 Map 中获取索引
            const index = this.menuItemIndexMap.get(menuItemElement);
            if (index !== undefined && this.menuItems[index].event) {
                this.menuItems[index].event(event);
                if (this.lasting) {
                    this.hide();
                } else {
                    this.destroy();
                }
            }
        }
    }

    /**
     * 处理点击菜单外部区域，用于关闭菜单
     * @param {MouseEvent} event
     */
    handleClickOutside(event) {
        if (!this.popupContainer.contains(event.target)) {
            if (this.lasting) {
                this.hide();
            } else {
                this.destroy();
            }
        }
    }

    frameUpdate(callback) {
        // 清理现有的动画循环
        this.cancelFrameUpdate();

        // 只在菜单显示时启动动画循环
        if (this.popupContainer.style.display !== 'none') {
            const updateLoop = (timestamp) => {
                // 如果菜单被隐藏，停止循环
                if (this.popupContainer.style.display === 'none') {
                    this.cancelFrameUpdate();
                    return;
                }

                callback(this, timestamp); // 添加 timestamp 参数以便更精确的动画控制
                this._frameUpdateId = requestAnimationFrame(updateLoop);
            };

            this._frameUpdateId = requestAnimationFrame(updateLoop);
        }
    }

    cancelFrameUpdate() {
        if (this._frameUpdateId) {
            cancelAnimationFrame(this._frameUpdateId);
            this._frameUpdateId = null;
        }
    }
}
