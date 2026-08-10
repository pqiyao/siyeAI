/**
 * 获取视频第一帧的Base64字符串
 * @param {string} videoUrl 视频网络路径
 * @param {number} [targetSize=7] 目标大小(KB)，默认7KB
 * @param {number} [maxSize=10] 最大大小(KB)，默认10KB
 * @returns {Promise<string>} 返回Base64字符串(不带Data URI前缀)
 */
export async function getVideoFirstFrame(videoUrl, targetSize = 7, maxSize = 10) {
  return new Promise((resolve, reject) => {
    // 创建视频元素
    const video = document.createElement('video');
    video.setAttribute('crossOrigin', 'anonymous'); // 处理跨域
    video.setAttribute('src', videoUrl);
    video.style.display = 'none';
    
    // 创建canvas元素
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    
    // 视频加载完成回调
    video.addEventListener('loadedmetadata', () => {
      // 设置canvas尺寸
      const { videoWidth, videoHeight } = video;
      canvas.width = videoWidth;
      canvas.height = videoHeight;
      
      // 尝试获取第一帧
      video.currentTime = 0.1; // 设置一个很小的非零值
    });
    
    // 视频seek完成时捕获帧
    video.addEventListener('seeked', () => {
      try {
        // 绘制视频帧到canvas
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        
        // 获取Base64并处理大小
        processImageQuality(canvas, targetSize * 1024, maxSize * 1024)
          .then(base64Str => {
            // 移除Data URI前缀
            const pureBase64 = base64Str.replace(/^data:image\/(png|jpeg|jpg);base64,/, '');
            resolve(pureBase64);
          })
          .catch(reject);
      } catch (error) {
        reject(error);
      } finally {
        // 清理
        video.pause();
        video.remove();
      }
    });
    
    // 错误处理
    video.addEventListener('error', (err) => {
      reject(new Error(`视频加载失败: ${err.message}`));
    });
    
    // 开始加载视频
    video.load();
  });
}

/**
 * 处理图片质量，控制输出大小
 * @param {HTMLCanvasElement} canvas 
 * @param {number} targetSize 目标大小(字节)
 * @param {number} maxSize 最大大小(字节)
 * @returns {Promise<string>} Base64字符串
 */
function processImageQuality(canvas, targetSize, maxSize) {
  return new Promise((resolve) => {
    let quality = 0.9; // 初始质量
    let result = '';
    let lastValidResult = '';
    
    const tryCompress = () => {
      // 转换为JPEG格式的Base64
      result = canvas.toDataURL('image/jpeg', quality);
      
      // 检查大小
      const base64Length = result.length - (result.indexOf(',') + 1);
      const size = (base64Length * 3) / 4; // Base64近似大小计算
      
      if (size <= maxSize) {
        lastValidResult = result;
      }
      
      if (size <= targetSize || quality <= 0.1) {
        // 达到目标大小或质量已很低
        resolve(lastValidResult || result);
        return;
      }
      
      // 调整质量继续尝试
      quality = Math.max(0.1, quality - 0.1);
      setTimeout(tryCompress, 0); // 避免阻塞
    };
    
    tryCompress();
  });
}