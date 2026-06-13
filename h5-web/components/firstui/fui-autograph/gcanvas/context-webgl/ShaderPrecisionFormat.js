// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID：2   544，营业执照号：91 4  4 0605 M   A C0 Y    3   J70Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
export default class WebGLShaderPrecisionFormat {
    className = 'WebGLShaderPrecisionFormat';

    constructor({
        rangeMin, rangeMax, precision
    }) {
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.precision = precision;
    }
}