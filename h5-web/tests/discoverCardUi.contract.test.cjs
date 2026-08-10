const assert = require('assert');
const fs = require('fs');
const path = require('path');

const discover = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.vue'), 'utf8');
const template = discover.slice(discover.indexOf('<template>'), discover.indexOf('<script>'));

assert(
	/class="g3-img"[\s\S]{0,180}:src="charCoverUrl\(c\)"/.test(template),
	'top pick cards must use the full-quality cover source'
);
assert(
	!/class="g3-img"[\s\S]{0,180}:src="charAvatarUrl\(c\)"/.test(template),
	'top pick cards must not upscale the avatar thumbnail'
);
assert(
	/charCoverUrl\(c\)[\s\S]{0,260}c\.cover_detail[\s\S]{0,180}c\.cover_thumb/.test(discover),
	'cover resolution must prefer detail/original fields before thumbnails'
);
assert.strictEqual(
	(template.match(/cardTierClass\(c\)/g) || []).length,
	3,
	'top picks and both main lists must use the same tier styling'
);
assert(
	/cardVisualTier\(c\)[\s\S]{0,420}requiredLevel >= 2[\s\S]{0,100}return 'svip'[\s\S]{0,180}c\.vip_only[\s\S]{0,100}return 'vip'[\s\S]{0,100}return 'standard'/.test(discover),
	'card tier mapping must preserve standard, VIP, and SVIP logic'
);
assert(discover.includes("return 'SVIP'"), 'SVIP cards must expose a visible grade');
assert(discover.includes("return 'VIP'"), 'VIP cards must expose a visible grade');
assert(discover.includes("return 'R'"), 'standard cards must expose a visible grade');
assert(
	/Keep full-resolution covers crisp[\s\S]{0,600}transform: scale\(1\.04\)/.test(discover),
	'hover treatment must use only a light 1.04 cover scale'
);
assert(
	/\.card-meta\s*\{\s*display:\s*none(?:\s*!important)?;/.test(discover),
	'legacy detached white metadata panel must be hidden'
);

console.log('discover card UI contract passed');
