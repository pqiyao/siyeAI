<template>
<canvas ref="canvas" class="liquid-glass-canvas" :class="{ 'detail-glass-canvas': variant === 'detail' }" aria-hidden="true"></canvas>
</template>

<script>
const vertexSource = `#version 300 es
in vec2 a_position;
out vec2 v_uv;
void main() {
  v_uv = a_position * 0.5 + 0.5;
  gl_Position = vec4(a_position, 0.0, 1.0);
}`;

const fragmentSource = `#version 300 es
precision highp float;
uniform sampler2D u_background;
uniform vec2 u_resolution;
uniform float u_time;
uniform int u_mode;
in vec2 v_uv;
out vec4 outColor;

float roundedBox(vec2 p, vec2 b, float r) {
  vec2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec3 refractSurface(vec2 uv, float edge, vec2 n, float amount) {
  vec2 wobble = n * amount + vec2(sin(u_time * 0.7 + uv.y * 18.0), cos(u_time * 0.55 + uv.x * 21.0)) * 0.0018;
  vec2 shift = wobble * smoothstep(0.0, 0.08, edge);
  vec3 r = texture(u_background, clamp(uv + shift * 1.08, 0.001, 0.999)).rgb;
  vec3 g = texture(u_background, clamp(uv + shift, 0.001, 0.999)).rgb;
  vec3 b = texture(u_background, clamp(uv + shift * 0.92, 0.001, 0.999)).rgb;
  return vec3(r.r, g.g, b.b);
}

void glassRect(inout vec4 color, inout float alpha, vec2 center, vec2 halfSize, float radius, float strength) {
  vec2 p = v_uv * u_resolution - center;
  float d = roundedBox(p, halfSize, radius);
  float mask = 1.0 - smoothstep(0.0, 2.5, d);
  if (mask <= 0.0) return;
  float edge = abs(d);
  vec2 n = normalize(vec2(dFdx(d), dFdy(d)) + 0.0001);
  vec2 uv = v_uv;
  vec3 refracted = refractSurface(uv, edge, n, strength);
  float rim = 1.0 - smoothstep(0.0, 18.0, edge);
  float bevel = 1.0 - smoothstep(0.0, 34.0, edge);
  vec3 tint = mix(refracted, vec3(0.95, 1.0, 0.99), 0.24);
  tint += vec3(0.16, 0.48, 0.52) * rim * 0.18;
  tint += vec3(1.0) * bevel * 0.16;
  color.rgb = mix(color.rgb, tint, 0.82 * mask);
  alpha = max(alpha, mask);
}

void main() {
  vec2 uv = v_uv;
  vec2 cover = vec2(0.5) + (uv - 0.5) * vec2(1.0, 1.0);
  vec4 color = vec4(texture(u_background, clamp(cover, 0.001, 0.999)).rgb, 1.0);
  float alpha = u_mode == 1 ? 0.0 : 1.0;
  float w = u_resolution.x;
  // Design space is 1080×1920; scale the three major surfaces to the actual
  // viewport so the glass remains aligned on desktop emulation and phones.
  float sx = u_resolution.x / 394.0;
  float sy = u_resolution.y / 917.0;
  if (u_mode == 0) {
    glassRect(color, alpha, vec2(w * 0.5, 285.0 * sy), vec2(w * 0.5 - 24.0 * sx, 177.0 * sy), 28.0 * sx, 0.055);
    glassRect(color, alpha, vec2(w * 0.5, 555.0 * sy), vec2(w * 0.5 - 24.0 * sx, 122.0 * sy), 28.0 * sx, 0.045);
    glassRect(color, alpha, vec2(w * 0.5, 760.0 * sy), vec2(w * 0.5 - 24.0 * sx, 34.0 * sy), 22.0 * sx, 0.03);
  } else {
    // Detail pages use the canvas inside the scrolling sheet. Keep the pass
    // translucent so the hero image remains visible while the surface gains
    // a moving liquid sheen and chromatic edge.
    vec2 p = v_uv * u_resolution;
    float band = smoothstep(0.02, 0.0, abs(fract(v_uv.y * 3.0 + sin(u_time * 0.08)) - 0.5) - 0.46);
    float edge = smoothstep(0.18, 0.0, min(min(v_uv.x, 1.0 - v_uv.x), min(v_uv.y, 1.0 - v_uv.y)));
    // Detail mode is an optical sheen only. Sampling the full background here
    // would wash the entire chat page; bubble surfaces own transparency and
    // this pass adds a moving cyan/rose reflection above them.
    float wave = 0.5 + 0.5 * sin(u_time * 0.45 + v_uv.x * 8.0 + v_uv.y * 5.0);
    vec3 liquid = mix(vec3(0.70, 0.93, 0.96), vec3(1.0, 0.78, 0.90), wave);
    color.rgb = liquid + vec3(0.08, 0.16, 0.18) * edge + vec3(1.0) * band * 0.12;
    alpha = 0.025 + edge * 0.07 + band * 0.035;
  }
  outColor = vec4(color.rgb, alpha);
}`;

export default {
	name: 'LiquidGlassCanvas',
	props: {
		backgroundSrc: { type: String, default: '/static/login.png' },
		variant: { type: String, default: 'user' }
	},
	mounted() {
		// WebGL2 is a progressive enhancement; non-H5 targets keep the CSS fallback.
		// #ifdef H5
		this.$nextTick(() => this.init());
		// #endif
	},
	beforeDestroy() {
		if (this._raf) cancelAnimationFrame(this._raf);
		if (this._resizeObserver) this._resizeObserver.disconnect();
	},
	watch: {
		backgroundSrc() {
			if (this._reloadTexture) this._reloadTexture();
		}
	},
	methods: {
		init() {
			const canvas = this.$refs.canvas;
			if (!canvas) return;
			const gl = canvas.getContext('webgl2', { alpha: true, antialias: true, premultipliedAlpha: false });
			if (!gl) return;
			const compile = (type, source) => {
				const shader = gl.createShader(type);
				gl.shaderSource(shader, source);
				gl.compileShader(shader);
				if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
				return shader;
			};
			const program = gl.createProgram();
			gl.attachShader(program, compile(gl.VERTEX_SHADER, vertexSource));
			gl.attachShader(program, compile(gl.FRAGMENT_SHADER, fragmentSource));
			gl.linkProgram(program);
			if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
			gl.useProgram(program);
			gl.enable(gl.BLEND);
			gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
			gl.clearColor(0, 0, 0, 0);
			const buffer = gl.createBuffer();
			gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
			gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]), gl.STATIC_DRAW);
			const position = gl.getAttribLocation(program, 'a_position');
			gl.enableVertexAttribArray(position);
			gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
			const texture = gl.createTexture();
			gl.bindTexture(gl.TEXTURE_2D, texture);
			gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
			gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
			gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
			gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
			const image = new Image();
			const loadTexture = () => {
				gl.bindTexture(gl.TEXTURE_2D, texture);
				gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
				gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
				this._ready = true;
			};
			image.onload = loadTexture;
			this._reloadTexture = () => {
				this._ready = false;
				const source = this.backgroundSrc || '/static/login.png';
				const joiner = source.indexOf('?') >= 0 ? '&' : '?';
				image.src = source + joiner + 'glass=' + Date.now();
			};
			this._reloadTexture();
			this._uResolution = gl.getUniformLocation(program, 'u_resolution');
			this._uTime = gl.getUniformLocation(program, 'u_time');
			this._uMode = gl.getUniformLocation(program, 'u_mode');
			gl.uniform1i(this._uMode, this.variant === 'detail' ? 1 : 0);
			this._resize = () => {
				const rect = canvas.getBoundingClientRect();
				const ratio = Math.min(window.devicePixelRatio || 1, 2);
				canvas.width = Math.max(1, Math.round(rect.width * ratio));
				canvas.height = Math.max(1, Math.round(rect.height * ratio));
				gl.viewport(0, 0, canvas.width, canvas.height);
				gl.uniform2f(this._uResolution, rect.width, rect.height);
			};
			this._resize();
			this._resizeObserver = new ResizeObserver(this._resize);
			this._resizeObserver.observe(canvas);
			const start = performance.now();
			const frame = (now) => {
				if (this._ready) {
					gl.clear(gl.COLOR_BUFFER_BIT);
					gl.uniform1f(this._uTime, (now - start) / 1000);
					gl.drawArrays(gl.TRIANGLES, 0, 6);
				}
				this._raf = requestAnimationFrame(frame);
			};
			this._raf = requestAnimationFrame(frame);
		}
	}
};
</script>

<style scoped>
.liquid-glass-canvas {
	position: absolute;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	z-index: 0;
	pointer-events: none;
}

.detail-glass-canvas {
	z-index: 2;
	opacity: 0.9;
}
</style>
