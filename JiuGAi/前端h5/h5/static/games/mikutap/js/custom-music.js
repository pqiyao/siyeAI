(function () {
  'use strict';

  var storageKey = 'mikutap.customMusic.v1';
  var localDbName = 'mikutap-local-music';
  var localStoreName = 'files';
  var localRecordId = 'active';
  var maxLocalFileSize = 50 * 1024 * 1024;
  var tracks = {
    default: {
      label: '原版背景节奏',
      src: ''
    },
    liar: {
      label: 'ライアーメイデン',
      src: 'music/liar-maiden.mp3'
    },
    local: {
      label: '本地 MP3',
      src: ''
    }
  };

  var state = {
    mode: 'default',
    tapSound: true,
    volume: 70,
    localName: '',
    localSize: 0
  };

  var audio = new Audio();
  audio.loop = true;
  audio.preload = 'auto';
  var currentAudioSrc = '';
  var localObjectUrl = '';
  var gameActive = false;

  function installWebAudioManagerHook() {
    var hookedCtor = null;
    try {
      Object.defineProperty(window, 'WebAudioManager', {
        configurable: true,
        get: function () {
          return hookedCtor;
        },
        set: function (OriginalCtor) {
          hookedCtor = function () {
            var manager = new OriginalCtor();
            var sourceUrl = '';
            var originalLoad = manager.load;
            var originalPlay = manager.play;

            manager.load = function (url) {
              sourceUrl = String(url || '');
              return originalLoad.apply(manager, arguments);
            };

            manager.play = function () {
              if (
                sourceUrl.indexOf('data/main/main.json') >= 0 &&
                window.MikutapCustomMusic &&
                !window.MikutapCustomMusic.shouldPlayTapSound()
              ) {
                return;
              }
              return originalPlay.apply(manager, arguments);
            };

            return manager;
          };

          Object.defineProperty(window, 'WebAudioManager', {
            configurable: true,
            writable: true,
            value: hookedCtor
          });
        }
      });
    } catch (err) {}
  }

  function loadState() {
    try {
      var saved = JSON.parse(localStorage.getItem(storageKey) || '{}');
      if (tracks[saved.mode]) state.mode = saved.mode;
      if (typeof saved.tapSound === 'boolean') state.tapSound = saved.tapSound;
      if (typeof saved.volume === 'number') state.volume = Math.max(0, Math.min(100, saved.volume));
      if (typeof saved.localName === 'string') state.localName = saved.localName;
      if (typeof saved.localSize === 'number') state.localSize = saved.localSize;
    } catch (err) {}
  }

  function saveState() {
    try {
      localStorage.setItem(storageKey, JSON.stringify(state));
    } catch (err) {}
  }

  function openLocalDb() {
    return new Promise(function (resolve, reject) {
      if (!window.indexedDB) {
        reject(new Error('IndexedDB is not available.'));
        return;
      }

      var request = indexedDB.open(localDbName, 1);
      request.onupgradeneeded = function () {
        var db = request.result;
        if (!db.objectStoreNames.contains(localStoreName)) {
          db.createObjectStore(localStoreName, { keyPath: 'id' });
        }
      };
      request.onsuccess = function () {
        resolve(request.result);
      };
      request.onerror = function () {
        reject(request.error || new Error('Failed to open local music database.'));
      };
    });
  }

  function runLocalStore(mode, callback) {
    return openLocalDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var transaction = db.transaction(localStoreName, mode);
        var store = transaction.objectStore(localStoreName);
        var request = callback(store);

        request.onsuccess = function () {
          resolve(request.result);
        };
        request.onerror = function () {
          reject(request.error || new Error('Local music storage failed.'));
        };
        transaction.oncomplete = function () {
          db.close();
        };
        transaction.onerror = function () {
          db.close();
          reject(transaction.error || new Error('Local music transaction failed.'));
        };
      });
    });
  }

  function getLocalRecord() {
    return runLocalStore('readonly', function (store) {
      return store.get(localRecordId);
    });
  }

  function fileToArrayBuffer(file) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () {
        resolve(reader.result);
      };
      reader.onerror = function () {
        reject(reader.error || new Error('Failed to read local file.'));
      };
      reader.readAsArrayBuffer(file);
    });
  }

  function saveLocalFile(file) {
    return fileToArrayBuffer(file).then(function (buffer) {
      return runLocalStore('readwrite', function (store) {
        return store.put({
          id: localRecordId,
          name: file.name,
          size: file.size,
          type: file.type || 'audio/mpeg',
          updatedAt: Date.now(),
          buffer: buffer
        });
      });
    });
  }

  function deleteLocalFile() {
    return runLocalStore('readwrite', function (store) {
      return store.delete(localRecordId);
    });
  }

  function isCustomMode() {
    return state.mode !== 'default';
  }

  function isLocalMode() {
    return state.mode === 'local';
  }

  function setStatus(text) {
    $('#custom_music_status').text(text);
  }

  function formatSize(size) {
    if (!size) return '';
    if (size < 1024 * 1024) return Math.max(1, Math.round(size / 1024)) + ' KB';
    return (size / 1024 / 1024).toFixed(1) + ' MB';
  }

  function setOriginalBacktrack(enabled) {
    var $button = $('#bt_backtrack a');
    if (!$button.length) return;
    var text = $button.text();
    var isOn = text.indexOf('开启') >= 0 || /\bon\b/i.test(text);
    if (isOn !== enabled) $button.trigger('click');
  }

  function syncBodyClass() {
    $('body').toggleClass('mikutap-custom-music', isCustomMode());
  }

  function syncControls() {
    $('input[name="custom_music_mode"][value="' + state.mode + '"]').prop('checked', true);
    $('#custom_tap_sound').prop('checked', state.tapSound).prop('disabled', !isCustomMode());
    $('#custom_music_volume').val(state.volume).prop('disabled', !isCustomMode());
    $('#custom_music_clear').prop('disabled', !state.localName);
    $('#custom_music_local_name').text(
      state.localName ? '已选择：' + state.localName + (state.localSize ? '（' + formatSize(state.localSize) + '）' : '') : '未选择本地 MP3'
    );
    $('.music-toggle, .music-volume').toggleClass('is-disabled', !isCustomMode());
    syncBodyClass();
  }

  function setAudioSrc(src) {
    if (currentAudioSrc !== src) {
      currentAudioSrc = src;
      audio.src = src;
      audio.load();
    }
    audio.volume = state.volume / 100;
  }

  function clearAudioSrc() {
    currentAudioSrc = '';
    audio.removeAttribute('src');
    audio.load();
  }

  function revokeLocalObjectUrl() {
    if (localObjectUrl) {
      URL.revokeObjectURL(localObjectUrl);
      localObjectUrl = '';
    }
  }

  function useSelectedLocalFile(file, statusText) {
    revokeLocalObjectUrl();
    localObjectUrl = URL.createObjectURL(file);
    state.mode = 'local';
    state.localName = file.name;
    state.localSize = file.size;
    saveState();
    syncControls();
    setOriginalBacktrack(false);
    setStatus(statusText + file.name);
    if (gameActive) playCustomAudio(true);
  }

  function ensureLocalObjectUrl() {
    if (localObjectUrl) return Promise.resolve(localObjectUrl);
    return getLocalRecord().then(function (record) {
      if (!record || (!record.buffer && !record.blob)) return '';
      var blob = record.blob || new Blob([record.buffer], { type: record.type || 'audio/mpeg' });
      localObjectUrl = URL.createObjectURL(blob);
      state.localName = record.name || state.localName;
      state.localSize = record.size || state.localSize;
      saveState();
      syncControls();
      return localObjectUrl;
    });
  }

  function ensureAudioSource() {
    var track = tracks[state.mode];

    if (!isCustomMode()) {
      clearAudioSrc();
      return Promise.resolve(false);
    }

    if (isLocalMode()) {
      return ensureLocalObjectUrl()
        .then(function (url) {
          if (!url) {
            setStatus('请先选择本地 MP3。');
            return false;
          }
          setAudioSrc(url);
          return true;
        })
        .catch(function () {
          setStatus('读取本地 MP3 失败，请重新选择。');
          return false;
        });
    }

    if (!track || !track.src) {
      clearAudioSrc();
      return Promise.resolve(false);
    }

    setAudioSrc(track.src);
    return Promise.resolve(true);
  }

  function stopCustomAudio(reset) {
    audio.pause();
    if (reset) {
      try {
        audio.currentTime = 0;
      } catch (err) {}
    }
  }

  function playCustomAudio(reset) {
    if (!isCustomMode()) return;
    ensureAudioSource().then(function (ready) {
      if (!ready) return;
      if (reset) {
        try {
          audio.currentTime = 0;
        } catch (err) {}
      }
      audio.volume = state.volume / 100;
      var playPromise = audio.play();
      if (playPromise && playPromise.catch) {
        playPromise.catch(function () {
          setStatus('需要先点击开始后才能播放 MP3。');
        });
      }
    });
  }

  function getCurrentTrackLabel() {
    if (isLocalMode()) return state.localName ? '本地 MP3：' + state.localName : '本地 MP3';
    return tracks[state.mode] ? tracks[state.mode].label : '';
  }

  function applyMusicMode() {
    syncControls();
    if (isCustomMode()) {
      setOriginalBacktrack(false);
      if (gameActive) playCustomAudio(false);
      setStatus(isLocalMode() && !state.localName ? '请先选择本地 MP3。' : '当前 MP3：' + getCurrentTrackLabel());
    } else {
      stopCustomAudio(true);
      clearAudioSrc();
      setOriginalBacktrack(true);
      setStatus('当前使用原版背景节奏。');
    }
  }

  function openPanel() {
    $('#music_settings_panel').addClass('is-open').attr('aria-hidden', 'false');
  }

  function closePanel() {
    $('#music_settings_panel').removeClass('is-open').attr('aria-hidden', 'true');
  }

  function validateLocalFile(file) {
    if (!file) return '没有选择文件。';
    var hasMp3Name = /\.mp3$/i.test(file.name || '');
    var hasMp3Type = ['audio/mpeg', 'audio/mp3', 'audio/x-mpeg', 'audio/mpeg3'].indexOf(file.type || '') >= 0;
    if (!hasMp3Name && !hasMp3Type) {
      return '请选择 MP3 文件。';
    }
    if (file.size > maxLocalFileSize) {
      return '文件太大，请选择 50MB 以内的 MP3。';
    }
    return '';
  }

  function handleLocalFile(file) {
    var error = validateLocalFile(file);
    if (error) {
      setStatus(error);
      return;
    }

    setStatus('正在保存到本机...');
    saveLocalFile(file)
      .then(function () {
        useSelectedLocalFile(file, '已保存本地 MP3：');
      })
      .catch(function () {
        try {
          useSelectedLocalFile(file, '本机存储不可用，已临时使用 MP3：');
        } catch (err) {
          setStatus('保存失败，可能是本机存储空间不足。');
        }
      });
  }

  function resetLocalFile(statusText) {
    revokeLocalObjectUrl();
    if (isLocalMode()) state.mode = 'default';
    state.localName = '';
    state.localSize = 0;
    saveState();
    applyMusicMode();
    setStatus(statusText);
  }

  function clearLocalFile() {
    stopCustomAudio(true);
    setStatus('正在移除本地音乐...');
    deleteLocalFile()
      .then(function () {
        resetLocalFile('本地音乐已移除。');
      })
      .catch(function () {
        resetLocalFile('已从当前页面移除，本机记录稍后可再清理。');
      });
  }

  function refreshLocalInfo() {
    if (!window.indexedDB) {
      state.localName = '';
      state.localSize = 0;
      if (isLocalMode()) state.mode = 'default';
      saveState();
      syncControls();
      return;
    }
    getLocalRecord()
      .then(function (record) {
        if (record && (record.buffer || record.blob)) {
          state.localName = record.name || state.localName;
          state.localSize = record.size || state.localSize;
        } else {
          state.localName = '';
          state.localSize = 0;
          if (isLocalMode()) state.mode = 'default';
        }
        saveState();
        syncControls();
        if (isLocalMode()) setStatus('当前 MP3：' + getCurrentTrackLabel());
      })
      .catch(function () {});
  }

  window.MikutapCustomMusic = {
    shouldPlayTapSound: function () {
      return !isCustomMode() || state.tapSound;
    },
    stop: function () {
      gameActive = false;
      stopCustomAudio(true);
    }
  };

  installWebAudioManagerHook();
  loadState();

  $(function () {
    syncControls();
    setStatus('选择后点击开始播放。');
    setTimeout(function () {
      refreshLocalInfo();
      applyMusicMode();
    }, 0);

    $('#music_settings_btn').on('click', function () {
      openPanel();
    });

    $('#music_settings_close').on('click', function () {
      closePanel();
    });

    $('#music_settings_panel').on('click', function (event) {
      if (event.target === this) closePanel();
    });

    $('input[name="custom_music_mode"]').on('change', function () {
      state.mode = this.value;
      saveState();
      applyMusicMode();
    });

    $('#custom_music_file').on('change', function () {
      var file = this.files && this.files[0];
      this.value = '';
      handleLocalFile(file);
    });

    $('#custom_music_clear').on('click', function () {
      if (!state.localName) return;
      clearLocalFile();
    });

    $('#custom_tap_sound').on('change', function () {
      state.tapSound = this.checked;
      saveState();
      syncControls();
      setStatus(state.tapSound ? '点击音效已开启。' : '点击音效已关闭，只保留动画。');
    });

    $('#custom_music_volume').on('input change', function () {
      state.volume = Number(this.value) || 0;
      audio.volume = state.volume / 100;
      saveState();
    });

    $('#bt_start a').on('click', function () {
      gameActive = true;
      if (isCustomMode()) {
        setOriginalBacktrack(false);
        playCustomAudio(true);
      }
    });

    $('#bt_back').on('click', function () {
      gameActive = false;
      stopCustomAudio(false);
    });

    audio.addEventListener('error', function () {
      setStatus(isLocalMode() ? '本地 MP3 播放失败，请重新选择。' : 'MP3 加载失败，请检查 music 文件夹。');
    });

    window.addEventListener('beforeunload', revokeLocalObjectUrl);
  });
})();
