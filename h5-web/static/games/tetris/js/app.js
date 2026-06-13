(function(document, window) {
	'use strict';

	var gameInst;
	var hudTimer;
	var currentDifficulty = 'normal';
	var difficulties = {
		easy: 1200,
		normal: 1000,
		hard: 650
	};

	function query(selector) {
		return document.querySelector(selector);
	}

	function queryAll(selector) {
		return Array.prototype.slice.call(document.querySelectorAll(selector));
	}

	function setDisplay(selector, value) {
		var el = query(selector);
		if (el) el.style.display = value;
	}

	function setText(selector, value) {
		var el = query(selector);
		if (el) el.textContent = String(value);
	}

	function formatTime(seconds) {
		seconds = Math.max(0, Number(seconds) || 0);
		var hours = Math.floor(seconds / 3600);
		var minutes = Math.floor((seconds - hours * 3600) / 60);
		var remain = seconds - hours * 3600 - minutes * 60;
		return [hours, minutes, remain].map(function(value) {
			return value < 10 ? '0' + value : String(value);
		}).join(':');
	}

	function getStats() {
		if (!gameInst) {
			return { score: 0, best: 0, time: '00:00:00', level: 0 };
		}
		var score = gameInst.score ? gameInst.score.score : 0;
		var best = gameInst.highscore ? gameInst.highscore.highScore : 0;
		var time = gameInst.timer ? formatTime(gameInst.timer.time) : '00:00:00';
		var level = gameInst.leval ? gameInst.leval.leval : 0;
		return { score: score, best: best, time: time, level: level };
	}

	function syncHud() {
		var stats = getStats();
		setText('#tetris-hud-score', stats.score);
		setText('#tetris-hud-best', stats.best);
		setText('#tetris-hud-time', stats.time);
		setText('#tetris-hud-level', stats.level);
		setText('#tetris-record-score', stats.score);
		setText('#tetris-record-best', stats.best);
		setText('#tetris-record-time', stats.time);
		setText('#tetris-record-level', stats.level);
	}

	function startHudSync() {
		if (hudTimer) window.clearInterval(hudTimer);
		hudTimer = window.setInterval(syncHud, 300);
		syncHud();
	}

	function syncPauseButtons() {
		var text = gameInst && gameInst._state === 'playing' ? '\u6682\u505c' : '\u7ee7\u7eed';
		queryAll('.btn-game-pause').forEach(function(button) {
			button.textContent = text;
		});
	}

	function syncSoundToggles(checked) {
		queryAll('#ck-sound, #tetris-sound-toggle').forEach(function(input) {
			input.checked = checked;
		});
	}

	function syncDifficultyButtons() {
		queryAll('[data-difficulty]').forEach(function(button) {
			button.classList.toggle('is-active', button.getAttribute('data-difficulty') === currentDifficulty);
		});
	}

	function applyDifficulty(value) {
		if (!difficulties[value]) value = 'normal';
		currentDifficulty = value;
		window.localStorage.setItem('tetris-difficulty', value);
		window.TetrisConfig.constSpeed = difficulties[value];
		window.TetrisConfig.speed = difficulties[value];
		syncDifficultyButtons();
		if (gameInst && gameInst._state === 'playing') {
			gameInst._stopTick();
			gameInst._startTick();
		}
	}

	function startGame() {
		if (gameInst) return;
		ResourceManager.onResourceLoaded = function() {
			gameInst = new Tetris();
			window.TetrisGame = gameInst;
			gameInst.startGame();
			syncPauseButtons();
			startHudSync();
		};
		ResourceManager.init();
	}

	function togglePause() {
		if (!gameInst) return;
		if (gameInst._state === 'playing') {
			gameInst.pause();
		} else {
			gameInst.resume();
		}
		syncPauseButtons();
		syncHud();
	}

	function setSoundEnabled(checked) {
		window.TetrisConfig.config.enableSound = checked;
		syncSoundToggles(checked);
		if (!gameInst || !gameInst._sound) return;
		if (checked && gameInst._state === 'playing') {
			gameInst._playSound();
		} else {
			gameInst._sound.pause();
		}
	}

	function openDialog(selector) {
		var dialog = query(selector);
		if (!dialog) return;
		syncHud();
		dialog.hidden = false;
	}

	function closeDialogs() {
		queryAll('.tetris-dialog-panel').forEach(function(dialog) {
			dialog.hidden = true;
		});
	}

	function bindDialogControls() {
		var settingsOpen = query('#tetris-settings-open');
		var recordOpen = query('#tetris-record-open');
		var restartButton = query('#tetris-restart-button');
		var clearRecord = query('#tetris-clear-record');

		if (settingsOpen) settingsOpen.addEventListener('click', function() {
			openDialog('#tetris-settings-panel');
		});
		if (recordOpen) recordOpen.addEventListener('click', function() {
			openDialog('#tetris-record-panel');
		});
		if (restartButton) restartButton.addEventListener('click', function() {
			window.location.reload();
		});
		if (clearRecord) clearRecord.addEventListener('click', function() {
			window.localStorage.setItem('high-score', 0);
			if (gameInst && gameInst.highscore) {
				gameInst.highscore.highScore = 0;
				gameInst.highscore._render();
			}
			syncHud();
		});

		queryAll('[data-difficulty]').forEach(function(button) {
			button.addEventListener('click', function(event) {
				applyDifficulty(event.currentTarget.getAttribute('data-difficulty'));
			});
		});

		queryAll('[data-close-dialog]').forEach(function(button) {
			button.addEventListener('click', closeDialogs);
		});
		queryAll('.tetris-dialog-panel').forEach(function(dialog) {
			dialog.addEventListener('click', function(event) {
				if (event.target === dialog) closeDialogs();
			});
		});
	}

	function init() {
		setDisplay('.start-container', 'none');
		setDisplay('.game-container', 'block');
		setDisplay('.modal-dialog', 'none');
		window.TetrisConfig.config.enableSound = false;
		syncSoundToggles(false);
		applyDifficulty(window.localStorage.getItem('tetris-difficulty') || 'normal');

		queryAll('.btn-game-pause').forEach(function(button) {
			button.addEventListener('click', togglePause);
		});

		queryAll('#ck-sound, #tetris-sound-toggle').forEach(function(sound) {
			sound.addEventListener('change', function(event) {
				setSoundEnabled(event.currentTarget.checked);
			});
		});

		bindDialogControls();
		startGame();
	}

	document.addEventListener('DOMContentLoaded', init);
})(document, window);
