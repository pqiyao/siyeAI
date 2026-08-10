// 本文件由FirstUI授权予广东即见即得数字科技有限公司（会员ID： 25 4 4，营业执照号：   91 4  40 6 05  M  AC 0 Y 3J70  Y）专用，请尊重知识产权，勿私下传播，违者追究法律责任。
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