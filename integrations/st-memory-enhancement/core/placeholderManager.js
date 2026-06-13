// core/placeholderManager.js
import { BASE } from './manager.js';

class PlaceholderManager {
    constructor() {
        this.placeholderRegex = /\$([A-Z]+\d+)|S\[(.+?)]\[(.+?)]/g;
    }

    /**
     * 渲染模板字符串，替换所有占位符
     * @param {string} template - 包含占位符的模板字符串
     * @param {object} currentSheet - 当前的表格对象
     * @returns {string} - 渲染后的字符串
     */
    render(template, currentSheet) {
        if (!template) return '';

        return template.replace(this.placeholderRegex, (match, singleAddress, sheetIdentifier, cellAddress) => {
            if (singleAddress) {
                // 处理当前工作表的单元格引用，例如 $A1
                const { row, col } = this.parseCellAddress(singleAddress);
                const cell = currentSheet.findCellByPosition(row, col);
                return cell ? cell.data.value : '';
            } else if (sheetIdentifier && cellAddress) {
                // 处理对其他工作表的引用，例如 S[工作表名称][A1] 或 S[0][A1]
                const targetSheet = this.findSheet(sheetIdentifier);
                if (!targetSheet) {
                    return `[Sheet "${sheetIdentifier}" not found]`;
                }

                const { row, col } = this.parseCellAddress(cellAddress);
                const cell = targetSheet.findCellByPosition(row, col);
                return cell ? cell.data.value : `[Cell "${cellAddress}" not found in sheet "${sheetIdentifier}"]`;
            }
            return match; // Fallback for no match
        });
    }

    /**
     * 根据标识符（名称或索引）查找工作表
     * @param {string} identifier - 工作表名称或索引
     * @returns {object|null} - 找到的工作表对象，如果未找到则返回 null
     */
    findSheet(identifier) {
        // 检查标识符是否为数字索引
        const sheetIndex = parseInt(identifier, 10);
        if (!isNaN(sheetIndex)) {
            const sheets = BASE.getChatSheets();
            if (sheetIndex >= 0 && sheetIndex < sheets.length) {
                return sheets[sheetIndex];
            }
        }
        
        // 否则，按名称查找工作表
        return BASE.getSheetByName(identifier);
    }

    /**
     * 将单元格地址（例如 "A1"）解析为行和列索引
     * @param {string} address - 单元格地址
     * @returns {{row: number, col: number}} - 行和列索引
     */
    parseCellAddress(address) {
        const colStrMatch = address.match(/[A-Z]+/);
        const rowStrMatch = address.match(/\d+/);
        if (!colStrMatch || !rowStrMatch) return { row: -1, col: -1 };

        const colStr = colStrMatch[0];
        const rowStr = rowStrMatch[0];

        // "A" corresponds to index 1 in hashSheet (index 0 is row header)
        const col = colStr.toUpperCase().charCodeAt(0) - 'A'.charCodeAt(0) + 1;
        // Row "1" corresponds to index 1 in hashSheet (index 0 is column header)
        const row = parseInt(rowStr, 10);
        
        return { row, col };
    }
}

export const placeholderManager = new PlaceholderManager();
