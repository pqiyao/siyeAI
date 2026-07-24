const assert = require('assert');
const {
	buildViewerIdentitySignature,
	shouldReloadViewerIdentity
} = require('../common/viewerIdentity.js');

function guest(clientUid) {
	return buildViewerIdentitySignature({ clientUid, authenticated: false });
}

function user(userId) {
	return buildViewerIdentitySignature({
		userId,
		clientUid: `h5u_${userId}`,
		authenticated: true
	});
}

const guestIdentity = guest('tc_device_1');
const userAIdentity = user('101');
const userBIdentity = user('202');

assert.strictEqual(guestIdentity, 'guest:tc_device_1');
assert.strictEqual(userAIdentity, 'user:101|client:h5u_101');

assert.strictEqual(shouldReloadViewerIdentity(guestIdentity, userAIdentity), true, 'guest -> login must reload');
assert.strictEqual(shouldReloadViewerIdentity(userAIdentity, userBIdentity), true, 'account A -> B must reload');
assert.strictEqual(shouldReloadViewerIdentity(userAIdentity, guestIdentity), true, 'logout must reload');

const userAAfterTokenRefresh = buildViewerIdentitySignature({
	userId: '101',
	clientUid: 'h5u_101',
	authenticated: true
});
assert.strictEqual(userAAfterTokenRefresh, userAIdentity, 'token refresh must not change stable identity');
assert.strictEqual(
	shouldReloadViewerIdentity(userAIdentity, userAAfterTokenRefresh),
	false,
	'same-user token refresh must not reload'
);
assert.strictEqual(shouldReloadViewerIdentity(guestIdentity, guestIdentity), false, 'unchanged guest must not reload');
assert.strictEqual(shouldReloadViewerIdentity(userAIdentity, userAIdentity), false, 'unchanged user must not reload');
assert.strictEqual(shouldReloadViewerIdentity('', userAIdentity), false, 'initial identity capture must not reload');

console.log('viewer identity unit tests passed');
