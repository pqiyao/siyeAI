// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 2 54 4，营业执照号：  9 14406    05 M   AC 0Y   3J7  0Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
import {getTransferedObjectUUID} from './classUtils';

const name = 'WebGLUniformLocation';

function uuid(id) {
    return getTransferedObjectUUID(name, id);
}

export default class WebGLUniformLocation {
    className = name;

    constructor(id, type) {
        this.id = id;
        this.type = type;
    }

    static uuid = uuid;

    uuid() {
        return uuid(this.id);
    }
}