// _fotTest.js
import { BASE, DERIVED, EDITOR, SYSTEM, USER } from '../core/manager.js';

const TESTING = true;

let codeQueue = [];
/**
 * 将代码添加到测试队列。
 * @param {Function} code 测试函数
 * @param {string} [functionName] 函数的名称，可选
 */
export function pushCodeToQueue(code, functionName) {
    codeQueue.push({ func: code, name: functionName });
    if (testTestSidebarEnabled && testSidebarContainer) { // 修复变量名
        appendTestFunctionButton(testSidebarContainer, codeQueue[codeQueue.length - 1], codeQueue.length - 1);
    }
}

export function initTest() {
    if (!TESTING || !USER.tableBaseSetting.tableDebugModeAble) return;
    if (!testTestSidebarEnabled) openTestSidebar();
}

let testTestSidebarEnabled = false; // 保留原始变量名
let testSidebarContainer = null;
let isDragging = false;
let offsetX, offsetY;

function openTestSidebar() {
    testTestSidebarEnabled = true;
    testSidebarContainer = createSidebarContainer();
    testSidebarContainer.appendChild(createToolBar());
    loadAndAppendTestContent(testSidebarContainer);

    addDragListeners();
    window.addEventListener('resize', handleWindowResize);
    document.body.appendChild(testSidebarContainer);

    adjustSidebarPositionWithinBounds();
}

async function testingProcess() {
    if (codeQueue.length === 0) {
        console.log(`[${new Date().toLocaleTimeString()}] 没有注册任何 code，无法执行，请使用 SYSTEM.f(()=>{需要测试的代码}, '函数名') 注册测试代码。`);
        return;
    }

    const startTime = performance.now();

    console.log(`%c[${new Date().toLocaleTimeString()}] START [SYSTEM.f()...`, 'color: blue; font-weight: bold');
    for (const codeObject of codeQueue) {
        const func = codeObject.func;
        const functionName = codeObject.name; // 获取函数名
        const startTimeI = performance.now();
        const index = codeQueue.indexOf(codeObject);
        try {
            await func();
            console.log(`%c[${new Date().toLocaleTimeString()}] ${functionName || `f[${index}]`} END (用时: ${(performance.now() - startTimeI).toFixed(2)}ms)`, 'color: green'); // 保留函数名输出和时间
        } catch (error) {
            console.error(`%c[${new Date().toLocaleTimeString()}] ${functionName || `f[${index}]`} ERROR:`, 'color: red; font-weight: bold', error); // 保留函数名输出
        }
    }

    const endTime = performance.now();
    const elapsedTime = endTime - startTime;

    console.log(`%c[${new Date().toLocaleTimeString()}] SYSTEM.f()] END (总用时: ${elapsedTime.toFixed(2)}ms)`, 'color: green; font-weight: bold');
}

function createSidebarContainer() {
    const container = document.createElement('div');
    container.id = 'test-floating-sidebar';
    Object.assign(container.style, {
        position: 'fixed',
        top: '200px',
        right: '20px',
        backgroundColor: '#c11',
        maxWidth: '100px',
        padding: '2px',
        zIndex: '999',
        borderRadius: '5px',
        cursor: 'move',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#ccc',
        userSelect: 'none',
        border: '1px solid #555',
        boxShadow: '2px 2px 5px rgba(0, 0, 0, 0.3)',
    });
    container.classList.add('popup');
    return container;
}

function createToolBar() {
    const toolBar = document.createElement('div');
    toolBar.id = 'test_tool_bar';
    Object.assign(toolBar.style, {
        display: 'flex',
        justifyContent: 'space-around',
        padding: '2px 0',
        marginBottom: '2px',
        borderBottom: '1px solid #555'
    });

    const retryButton = createToolButton('<i class="fa-solid fa-repeat"></i>', async (event) => { // 使用 Font Awesome 图标, 并添加隐藏的文字
        event.stopPropagation();
        if (confirm('将依次执行测试队列中注册的的代码，是否继续？')) {
            await reloadTestContent();
        } else {

        }
    });
    toolBar.appendChild(retryButton);

    const logButton = createToolButton('<i class="fa-solid fa-database"></i>', (event) => { // 使用 Font Awesome 图标, 并添加隐藏的文字
        event.stopPropagation();
        EDITOR.logAll();
    });
    toolBar.appendChild(logButton);

    return toolBar;
}

function createToolButton(innerHTML, onClickHandler) { // 修改 text 参数为 innerHTML
    const button = document.createElement('button');
    button.innerHTML = innerHTML; // 使用 innerHTML
    Object.assign(button.style, {
        background: 'none',
        border: '2px solid #a00',
        cursor: 'pointer',
        color: '#eee',
        margin: '1px',
        padding: '2px 5px',
        fontSize: '10px',
        borderRadius: '3px',
        display: 'flex', // 使按钮内容可以 flex 布局，方便图标和文字对齐
        alignItems: 'center', // 垂直居中
        justifyContent: 'center' // 水平居中
    });
    // 添加hover效果
    button.addEventListener('mouseover', () => {
        button.style.backgroundColor = '#a00';
    });
    button.addEventListener('mouseout', () => {
        button.style.backgroundColor = 'transparent'; //或者'none'
    });

    const icon = button.querySelector('i'); // 选中按钮内的图标，设置图标样式
    if (icon) {
        Object.assign(icon.style, {
            marginRight: '0px', // 图标和文字间距
            fontSize: '12px' // 图标大小
        });
    }

    button.onclick = onClickHandler;
    return button;
}

/**
 * 加载并添加测试内容到容器中。
 * @param {HTMLElement} container 容器元素
 */
function loadAndAppendTestContent(container) {
    if (codeQueue.length === 0) {
        appendTestOutput(container, 'SYSTEM.f(()=>{添加测试代码}, "函数名")');
        return;
    }

    codeQueue.forEach((codeObject, index) => {
        appendTestFunctionButton(container, codeObject, index);
    });
}

/**
 * 为单个测试函数创建并添加执行按钮到容器。
 * @param {HTMLElement} container 容器元素
 * @param {object} codeObject 包含测试函数和函数名的对象 { func: Function, name: string }
 * @param {number} index 函数在队列中的索引
 */
function appendTestFunctionButton(container, codeObject, index) {
    const functionContainer = document.createElement('div');
    functionContainer.style.marginBottom = '2px';
    functionContainer.style.display = 'flex';
    functionContainer.style.alignItems = 'center';
    functionContainer.style.borderBottom = '1px dashed #555';
    functionContainer.style.paddingBottom = '2px';
    functionContainer.style.justifyContent = 'space-between';

    const functionLabel = document.createElement('pre');
    functionLabel.innerText = codeObject.name || `f[${index}]`;
    functionLabel.style.margin = '0';
    functionLabel.style.marginRight = '5px';
    functionContainer.appendChild(functionLabel);

    const runButton = createToolButton(`<i class="fas fa-play"></i>`, async (event) => { //  添加 Font Awesome 图标，并隐藏文字
        event.stopPropagation();
        const startTimeI = performance.now();
        const functionName = codeObject.name;
        try {
            console.log(`%c[${new Date().toLocaleTimeString()}] ${functionName || `f[${index}]`} START`, 'color: blue; font-weight: bold');
            await codeObject.func();
            //  "END" 信息也使用粗体显示
            console.log(`%c[${new Date().toLocaleTimeString()}] ${functionName || `f[${index}]`} END (用时: ${(performance.now() - startTimeI).toFixed(2)}ms)`, 'color: green; font-weight: bold');
        } catch (error) {
            console.error(`%c[${new Date().toLocaleTimeString()}] ${functionName || `f[${index}]`} ERROR:`, 'color: red; font-weight: bold', error);
            console.error(error);
        }
    });
    runButton.style.padding = '2px'; // 调整 runButton 的 padding
    runButton.style.minWidth = 'auto'; // 移除最小宽度限制，让按钮更贴合图标
    functionContainer.appendChild(runButton);
    container.appendChild(functionContainer);
}

async function reloadTestContent() {
    if (!testSidebarContainer) return;

    while (testSidebarContainer.children.length > 1) {
        testSidebarContainer.removeChild(testSidebarContainer.lastChild);
    }

    await loadAndAppendTestContent(testSidebarContainer);
    await testingProcess();
}

function appendTestOutput(container, outputText) {
    const outputElement = document.createElement('pre');
    outputElement.innerText = outputText;
    outputElement.style.fontSize = '10px';
    outputElement.style.margin = '0';
    outputElement.style.padding = '2px 5px';
    outputElement.style.backgroundColor = '#222';
    outputElement.style.borderRadius = '3px';
}

function addDragListeners() {
    testSidebarContainer.addEventListener('mousedown', dragStart);
    document.addEventListener('mousemove', dragMove);
    document.addEventListener('mouseup', dragEnd);
}

function removeDragListeners() {
    testSidebarContainer.removeEventListener('mousedown', dragStart);
    document.removeEventListener('mousemove', dragMove);
    document.removeEventListener('mouseup', dragEnd);
    window.removeEventListener('resize', handleWindowResize);
}

function dragStart(e) {
    isDragging = true;
    offsetX = e.clientX - testSidebarContainer.offsetLeft;
    offsetY = e.clientY - testSidebarContainer.offsetTop;
}

function dragMove(e) {
    if (!isDragging) return;
    adjustSidebarPositionWithinBounds(e.clientX - offsetX, e.clientY - offsetY);
}

function dragEnd() {
    isDragging = false;
}

function handleWindowResize() {
    adjustSidebarPositionWithinBounds();
}

function adjustSidebarPositionWithinBounds(inputX, inputY) {
    let newX = inputX !== undefined ? inputX : testSidebarContainer.offsetLeft;
    let newY = inputY !== undefined ? inputY : testSidebarContainer.offsetTop;
    let boundedX = Math.max(0, Math.min(newX, window.innerWidth - testSidebarContainer.offsetWidth));
    let boundedY = Math.max(0, Math.min(newY, window.innerHeight - testSidebarContainer.offsetHeight));

    testSidebarContainer.style.left = boundedX + 'px';
    testSidebarContainer.style.top = boundedY + 'px';
}
