(function(window, document) {
	'use strict';

	var keys = {
		38: 'top',
		39: 'right',
		40: 'down',
		37: 'left'
	};

	function Keyboard() {
		this.board = null;
	}

	Keyboard.prototype = {
		constructor: Keyboard,
		init: function(board) {
			var self = this;
			self.board = board;
			window.TetrisTouchControl = function(key) {
				self.press(key);
			};
			document.body.classList.add('tetris-touch-ready');
			document.addEventListener('keydown', function(evt) {
				self.processKeyDown(evt);
			});
		},
		processKeyDown: function(evt) {
			if (!this.board || this.board.gameInst._state !== 'playing') return;
			if (!keys[evt.keyCode]) return;
			evt.preventDefault();
			this.press(keys[evt.keyCode]);
		},
		press: function(key) {
			if (!this.board || this.board.gameInst._state !== 'playing') return;

			var refresh = false;
			switch (key) {
				case 'top':
					if (this.board.validMove(0, 0)) {
						this.board.shape.rotate();
						refresh = true;
					}
					break;
				case 'right':
					if (this.board.validMove(1, 0)) {
						this.board.shape.x += 1;
						refresh = true;
					}
					break;
				case 'down':
					if (this.board.validMove(0, 1)) {
						this.board.shape.y += 1;
						refresh = true;
					}
					break;
				case 'left':
					if (this.board.validMove(-1, 0)) {
						this.board.shape.x -= 1;
						refresh = true;
					}
					break;
			}

			if (!refresh) return;
			this.board.refresh();
			this.board.shape.draw(this.board.context);
			if (key === 'down') {
				var self = this;
				window.clearInterval(window.TetrisConfig.intervalId);
				window.TetrisConfig.intervalId = window.setInterval(function() {
					self.board.tick();
				}, TetrisConfig.speed);
			}
		}
	};

	window.Keyboard = Keyboard;

	document.addEventListener('DOMContentLoaded', function() {
		function fitGame() {
			var isTouchLayout = window.matchMedia('(max-width: 760px), (pointer: coarse)').matches;
			var controlsHeight = isTouchLayout ? 78 : 0;
			var topOffset = isTouchLayout ? 106 : 0;
			var baseWidth = isTouchLayout ? 390 : 590;
			var availableHeight = Math.max(240, window.innerHeight - controlsHeight - topOffset);
			var scale = Math.min(window.innerWidth / baseWidth, availableHeight / 600);
			scale = Math.max(scale, 0.1);
			document.documentElement.style.setProperty('--tetris-scale', scale);
			document.documentElement.style.setProperty('--tetris-top', topOffset + 'px');
			document.body.style.height = window.innerHeight + 'px';
		}

		var controls = document.createElement('div');
		controls.className = 'tetris-touch-controls';
		controls.innerHTML = [
			'<button type="button" data-key="top" aria-label="Rotate">&#8634;</button>',
			'<button type="button" data-key="left" aria-label="Left">&#9664;</button>',
			'<button type="button" data-key="down" aria-label="Down">&#9660;</button>',
			'<button type="button" data-key="right" aria-label="Right">&#9654;</button>'
		].join('');
		document.body.appendChild(controls);

		fitGame();
		window.addEventListener('resize', fitGame);
		window.addEventListener('orientationchange', fitGame);

		function press(key) {
			if (typeof window.TetrisTouchControl === 'function') {
				window.TetrisTouchControl(key);
			}
		}

		controls.addEventListener('touchstart', function(evt) {
			var target = evt.target;
			if (!target || !target.getAttribute('data-key')) return;
			evt.preventDefault();
			press(target.getAttribute('data-key'));
		}, { passive: false });

		controls.addEventListener('click', function(evt) {
			var target = evt.target;
			if (!target || !target.getAttribute('data-key')) return;
			press(target.getAttribute('data-key'));
		});

		var canvas = document.getElementById('c_game_main');
		var startX = 0;
		var startY = 0;
		if (!canvas) return;

		canvas.addEventListener('touchstart', function(evt) {
			if (!evt.touches || !evt.touches.length) return;
			startX = evt.touches[0].clientX;
			startY = evt.touches[0].clientY;
		}, { passive: true });

		canvas.addEventListener('touchend', function(evt) {
			if (!evt.changedTouches || !evt.changedTouches.length) return;
			var dx = evt.changedTouches[0].clientX - startX;
			var dy = evt.changedTouches[0].clientY - startY;
			var absX = Math.abs(dx);
			var absY = Math.abs(dy);

			if (Math.max(absX, absY) < 24) {
				press('top');
			} else if (absX > absY) {
				press(dx > 0 ? 'right' : 'left');
			} else {
				press(dy > 0 ? 'down' : 'top');
			}
		}, { passive: true });
	});
})(window, document);
