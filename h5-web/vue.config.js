const path = require('path');

module.exports = {
  chainWebpack(config) {
    config.module
      .rule('uni-template-export-compat')
      .enforce('post')
      .resourceQuery(/vue&type=template/)
      .use('uni-template-export-compat')
      .loader(path.resolve(__dirname, 'build/uni-template-export-compat-loader.js'));
  }
};
