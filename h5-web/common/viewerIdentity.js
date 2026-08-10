function normalizeIdentityPart(value) {
	return value == null ? '' : String(value).trim();
}

function buildViewerIdentitySignature(state) {
	var source = state && typeof state === 'object' ? state : {};
	var userId = normalizeIdentityPart(source.userId);
	var clientUid = normalizeIdentityPart(source.clientUid);
	if (source.authenticated === true && userId) {
		return 'user:' + userId + '|client:' + clientUid;
	}
	return 'guest:' + clientUid;
}

function shouldReloadViewerIdentity(previousSignature, currentSignature) {
	var previous = normalizeIdentityPart(previousSignature);
	var current = normalizeIdentityPart(currentSignature);
	return !!previous && !!current && previous !== current;
}

module.exports = {
	buildViewerIdentitySignature: buildViewerIdentitySignature,
	shouldReloadViewerIdentity: shouldReloadViewerIdentity
};
