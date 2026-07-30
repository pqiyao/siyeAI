var INDEX_KEY = 'tavern_local_media_index_v1';
var INDEX_VERSION = 1;
var DB_NAME = 'tavern-local-media';
var DB_VERSION = 1;
var STORE_NAME = 'media';
var H5_MAX_BYTES = 128 * 1024 * 1024;
var APP_MAX_BYTES = 256 * 1024 * 1024;
var mutationQueue = Promise.resolve();
var objectUrls = {};

function revokeObjectUrl(key) {
	var safeKey = text(key);
	var url = safeKey ? objectUrls[safeKey] : '';
	if (url && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
		try { URL.revokeObjectURL(url); } catch (e) {}
	}
	if (safeKey) delete objectUrls[safeKey];
}

function text(value) {
	return value == null ? '' : String(value).trim();
}

function isAppPlus() {
	return typeof plus !== 'undefined' && plus && plus.io;
}

function maxStorageBytes() {
	return isAppPlus() ? APP_MAX_BYTES : H5_MAX_BYTES;
}

function readIndex() {
	try {
		var raw = uni.getStorageSync(INDEX_KEY);
		var entries = raw && typeof raw === 'object' && Array.isArray(raw.entries) ? raw.entries : [];
		return entries.filter(function (item) {
			return item && text(item.key) && text(item.ownerKey) && text(item.conversationId);
		});
	} catch (e) {
		return [];
	}
}

function writeIndex(entries) {
	uni.setStorageSync(INDEX_KEY, {
		version: INDEX_VERSION,
		updatedAt: Date.now(),
		entries: Array.isArray(entries) ? entries : []
	});
}

function serializeMutation(action) {
	mutationQueue = mutationQueue.catch(function () {}).then(action);
	return mutationQueue;
}

function dataUrlParts(dataUrl) {
	var value = text(dataUrl);
	var match = value.match(/^data:([A-Za-z0-9.+-]+\/[A-Za-z0-9.+-]+);base64,([\s\S]+)$/);
	if (!match) throw new Error('invalid_media_data_url');
	var binary = atob(match[2]);
	var bytes = new Uint8Array(binary.length);
	for (var i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
	return { mimeType: match[1].toLowerCase(), bytes: bytes };
}

function extensionForMime(mimeType) {
	var mime = text(mimeType).toLowerCase();
	if (mime === 'audio/mpeg' || mime === 'audio/mp3') return 'mp3';
	if (mime === 'audio/wav' || mime === 'audio/x-wav') return 'wav';
	if (mime === 'audio/ogg') return 'ogg';
	if (mime === 'audio/webm') return 'webm';
	if (mime === 'audio/mp4' || mime === 'audio/m4a' || mime === 'audio/x-m4a') return 'm4a';
	if (mime === 'audio/aac' || mime === 'audio/x-aac') return 'aac';
	if (mime === 'audio/flac' || mime === 'audio/x-flac') return 'flac';
	if (mime === 'audio/amr' || mime === 'audio/3gpp') return 'amr';
	if (mime === 'image/jpeg') return 'jpg';
	if (mime === 'image/webp') return 'webp';
	if (mime === 'image/gif') return 'gif';
	if (mime === 'image/avif') return 'avif';
	return mime.indexOf('image/') === 0 ? 'png' : 'bin';
}

function hashKey(value) {
	var source = text(value);
	var hash = 2166136261;
	for (var i = 0; i < source.length; i += 1) {
		hash ^= source.charCodeAt(i);
		hash = Math.imul(hash, 16777619);
	}
	return (hash >>> 0).toString(16);
}

function openDb() {
	return new Promise(function (resolve, reject) {
		if (typeof indexedDB === 'undefined') {
			reject(new Error('indexeddb_unavailable'));
			return;
		}
		var request = indexedDB.open(DB_NAME, DB_VERSION);
		request.onupgradeneeded = function () {
			var db = request.result;
			if (!db.objectStoreNames.contains(STORE_NAME)) db.createObjectStore(STORE_NAME);
		};
		request.onsuccess = function () { resolve(request.result); };
		request.onerror = function () { reject(request.error || new Error('indexeddb_open_failed')); };
	});
}

function idbPut(key, blob) {
	return openDb().then(function (db) {
		return new Promise(function (resolve, reject) {
			var tx = db.transaction(STORE_NAME, 'readwrite');
			tx.objectStore(STORE_NAME).put(blob, key);
			tx.oncomplete = function () { db.close(); resolve(); };
			tx.onerror = function () { db.close(); reject(tx.error || new Error('indexeddb_write_failed')); };
		});
	});
}

function idbGet(key) {
	return openDb().then(function (db) {
		return new Promise(function (resolve, reject) {
			var tx = db.transaction(STORE_NAME, 'readonly');
			var request = tx.objectStore(STORE_NAME).get(key);
			request.onsuccess = function () { resolve(request.result || null); };
			request.onerror = function () { reject(request.error || new Error('indexeddb_read_failed')); };
			tx.oncomplete = function () { db.close(); };
		});
	});
}

function idbDelete(key) {
	return openDb().then(function (db) {
		return new Promise(function (resolve) {
			var tx = db.transaction(STORE_NAME, 'readwrite');
			tx.objectStore(STORE_NAME).delete(key);
			tx.oncomplete = function () { db.close(); resolve(); };
			tx.onerror = function () { db.close(); resolve(); };
		});
	});
}

function appWrite(key, parts) {
	return new Promise(function (resolve, reject) {
		if (!isAppPlus() || typeof plus.io.resolveLocalFileSystemURL !== 'function') {
			reject(new Error('app_filesystem_unavailable'));
			return;
		}
		var fileName = hashKey(key) + '_' + Date.now() + '.' + extensionForMime(parts.mimeType);
		var relativePath = '_doc/tavern_media/' + fileName;
		var mediaBlob = new Blob([parts.bytes], { type: parts.mimeType });
		var settled = false;
		var timeoutId = setTimeout(function () {
			if (settled) return;
			settled = true;
			reject(new Error('app_media_write_timeout'));
		}, 15000);
		var finish = function (error, value) {
			if (settled) return;
			settled = true;
			clearTimeout(timeoutId);
			if (error) reject(error);
			else resolve(value);
		};
		try {
			plus.io.resolveLocalFileSystemURL('_doc/', function (docEntry) {
				docEntry.getDirectory('tavern_media', { create: true }, function (mediaDir) {
					mediaDir.getFile(fileName, { create: true, exclusive: false }, function (fileEntry) {
						fileEntry.createWriter(function (writer) {
							writer.onwrite = function () {
								if (settled) return;
								fileEntry.file(function (file) {
									var actualSize = Number(file && file.size);
									if (Number.isFinite(actualSize) && actualSize !== parts.bytes.byteLength) {
										finish(new Error('app_media_write_incomplete'));
										return;
									}
									var localUrl = typeof fileEntry.toLocalURL === 'function' ? fileEntry.toLocalURL() : relativePath;
									finish(null, text(localUrl) || relativePath);
								}, finish);
							};
							writer.onerror = function (error) {
								finish(error || new Error('app_media_write_failed'));
							};
							writer.write(mediaBlob);
						}, finish);
					}, finish);
				}, finish);
			}, finish);
		} catch (error) {
			finish(error);
		}
	});
}

function removeLocation(entry) {
	if (!entry) return Promise.resolve();
	revokeObjectUrl(entry.key);
	if (entry.storage === 'idb') return idbDelete(entry.key).catch(function () {});
	if (entry.storage === 'app' && text(entry.location) && isAppPlus()) {
		return new Promise(function (resolve) {
			try {
				plus.io.resolveLocalFileSystemURL(entry.location, function (fileEntry) {
					fileEntry.remove(resolve, resolve);
				}, resolve);
			} catch (e) { resolve(); }
		});
	}
	return Promise.resolve();
}

function dropStaleEntry(entry) {
	if (!entry || !text(entry.key)) return Promise.resolve(false);
	return serializeMutation(function () {
		var entries = readIndex();
		var exists = entries.some(function (item) { return item.key === entry.key; });
		if (!exists) return false;
		writeIndex(entries.filter(function (item) { return item.key !== entry.key; }));
		return removeLocation(entry).then(function () { return true; });
	});
}

function resolveAppEntry(entry) {
	return new Promise(function (resolve) {
		if (!isAppPlus() || !text(entry && entry.location)) {
			resolve(null);
			return;
		}
		try {
			plus.io.resolveLocalFileSystemURL(entry.location, function (fileEntry) {
				var localUrl = typeof fileEntry.toLocalURL === 'function' ? fileEntry.toLocalURL() : entry.location;
				resolve(Object.assign({ url: text(localUrl) || entry.location }, entry));
			}, function () { resolve(null); });
		} catch (e) { resolve(null); }
	});
}

function normalizeMeta(meta) {
	var source = meta && typeof meta === 'object' ? meta : {};
	var key = text(source.key);
	if (!key) throw new Error('media_key_required');
	return {
		key: key,
		ownerKey: text(source.ownerKey),
		conversationId: text(source.conversationId),
		messageId: text(source.messageId),
		kind: text(source.kind),
		taskId: text(source.taskId),
		signature: text(source.signature),
		segmentIndex: Math.max(0, Math.floor(Number(source.segmentIndex) || 0))
	};
}

function upsertIndex(entry) {
	return serializeMutation(function () {
		var entries = readIndex();
		var previous = null;
		entries = entries.filter(function (item) {
			if (item.key === entry.key) { previous = item; return false; }
			return true;
		});
		entries.push(entry);
		writeIndex(entries);
		if (previous && previous.location !== entry.location) return removeLocation(previous).then(prune);
		return prune();
	});
}

function putDataUrl(meta, dataUrl) {
	var normalized = normalizeMeta(meta);
	var parts = dataUrlParts(dataUrl);
	var now = Date.now();
	if (isAppPlus()) {
		return appWrite(normalized.key, parts).then(function (location) {
			var entry = Object.assign({}, normalized, {
				mimeType: parts.mimeType,
				size: parts.bytes.byteLength,
				storage: 'app',
				location: location,
				createdAt: now,
				lastAccessAt: now
			});
			return upsertIndex(entry).then(function () { return Object.assign({ url: location }, entry); });
		});
	}
	var blob = new Blob([parts.bytes], { type: parts.mimeType });
	return idbPut(normalized.key, blob).then(function () {
		var entry = Object.assign({}, normalized, {
			mimeType: parts.mimeType,
			size: parts.bytes.byteLength,
			storage: 'idb',
			location: '',
			createdAt: now,
			lastAccessAt: now
		});
		return upsertIndex(entry).then(function () {
			revokeObjectUrl(normalized.key);
			var url = URL.createObjectURL(blob);
			objectUrls[normalized.key] = url;
			return Object.assign({ url: url }, entry);
		});
	});
}

function registerLocalUrl(meta, url, size, mimeType) {
	var normalized = normalizeMeta(meta);
	var now = Date.now();
	var entry = Object.assign({}, normalized, {
		mimeType: text(mimeType),
		size: Math.max(0, Number(size) || 0),
		storage: isAppPlus() ? 'app' : 'external',
		location: text(url),
		createdAt: now,
		lastAccessAt: now
	});
	return upsertIndex(entry).then(function () { return Object.assign({ url: entry.location }, entry); });
}

function get(key) {
	var safeKey = text(key);
	var entry = readIndex().find(function (item) { return item.key === safeKey; });
	if (!entry) return Promise.resolve(null);
	entry.lastAccessAt = Date.now();
	serializeMutation(function () {
		var entries = readIndex().map(function (item) { return item.key === safeKey ? entry : item; });
		writeIndex(entries);
	});
	if (entry.storage === 'idb') {
		return idbGet(safeKey).then(function (blob) {
			if (!blob) return dropStaleEntry(entry).then(function () { return null; });
			revokeObjectUrl(safeKey);
			objectUrls[safeKey] = URL.createObjectURL(blob);
			return Object.assign({ url: objectUrls[safeKey] }, entry);
		});
	}
	if (entry.storage === 'app') {
		return resolveAppEntry(entry).then(function (resolved) {
			if (resolved) return resolved;
			return dropStaleEntry(entry).then(function () { return null; });
		});
	}
	return Promise.resolve(Object.assign({ url: entry.location }, entry));
}

function list(query) {
	var source = query && typeof query === 'object' ? query : {};
	var ownerKey = text(source.ownerKey);
	var conversationId = text(source.conversationId);
	var messageId = text(source.messageId);
	var kind = text(source.kind);
	var entries = readIndex().filter(function (item) {
		return (!ownerKey || item.ownerKey === ownerKey)
			&& (!conversationId || item.conversationId === conversationId)
			&& (!messageId || item.messageId === messageId)
			&& (!kind || item.kind === kind);
	}).sort(function (a, b) { return Number(a.segmentIndex || 0) - Number(b.segmentIndex || 0); });
	return Promise.all(entries.map(function (item) { return get(item.key); })).then(function (items) {
		return items.filter(Boolean);
	});
}

function matchesQuery(item, query) {
	var source = query && typeof query === 'object' ? query : {};
	var ownerKey = text(source.ownerKey);
	var conversationId = text(source.conversationId);
	var messageId = text(source.messageId);
	var kind = text(source.kind);
	return !!item
		&& (!ownerKey || item.ownerKey === ownerKey)
		&& (!conversationId || item.conversationId === conversationId)
		&& (!messageId || item.messageId === messageId)
		&& (!kind || item.kind === kind);
}

function summary(query) {
	var entries = readIndex().filter(function (item) { return matchesQuery(item, query); });
	var totalBytes = entries.reduce(function (sum, item) {
		return sum + Math.max(0, Number(item.size) || 0);
	}, 0);
	var latestAt = entries.reduce(function (latest, item) {
		return Math.max(latest, Number(item.lastAccessAt || item.createdAt || 0));
	}, 0);
	return Promise.resolve({
		count: entries.length,
		totalBytes: totalBytes,
		maxBytes: maxStorageBytes(),
		latestAt: latestAt
	});
}

function removeMatching(query) {
	var source = query && typeof query === 'object' ? query : {};
	if (!text(source.ownerKey) && !text(source.conversationId) && !text(source.messageId) && !text(source.kind)) {
		return Promise.resolve(false);
	}
	return serializeMutation(function () {
		var entries = readIndex();
		var removed = entries.filter(function (item) { return matchesQuery(item, source); });
		if (!removed.length) return false;
		writeIndex(entries.filter(function (item) { return removed.indexOf(item) < 0; }));
		return Promise.all(removed.map(removeLocation)).then(function () { return true; });
	});
}

function removeByConversation(ownerKey, conversationId) {
	var owner = text(ownerKey);
	var conversation = text(conversationId);
	if (!owner || !conversation) return Promise.resolve(false);
	return serializeMutation(function () {
		var entries = readIndex();
		var removed = entries.filter(function (item) {
			return item.ownerKey === owner && item.conversationId === conversation;
		});
		var kept = entries.filter(function (item) { return removed.indexOf(item) < 0; });
		writeIndex(kept);
		return Promise.all(removed.map(removeLocation)).then(function () { return true; });
	});
}

function removeByOwner(ownerKey) {
	var owner = text(ownerKey);
	if (!owner) return Promise.resolve(false);
	return serializeMutation(function () {
		var entries = readIndex();
		var removed = entries.filter(function (item) { return item.ownerKey === owner; });
		writeIndex(entries.filter(function (item) { return item.ownerKey !== owner; }));
		return Promise.all(removed.map(removeLocation)).then(function () { return true; });
	});
}

function removeByConversationKind(ownerKey, conversationId, kind) {
	var owner = text(ownerKey);
	var conversation = text(conversationId);
	var mediaKind = text(kind);
	if (!owner || !conversation || !mediaKind) return Promise.resolve(false);
	return removeMatching({ ownerKey: owner, conversationId: conversation, kind: mediaKind });
}

function removeByOwnerKind(ownerKey, kind) {
	var owner = text(ownerKey);
	var mediaKind = text(kind);
	if (!owner || !mediaKind) return Promise.resolve(false);
	return removeMatching({ ownerKey: owner, kind: mediaKind });
}

function moveMessage(ownerKey, conversationId, fromMessageId, toMessageId) {
	var owner = text(ownerKey);
	var conversation = text(conversationId);
	var fromId = text(fromMessageId);
	var toId = text(toMessageId);
	if (!owner || !conversation || !fromId || !toId || fromId === toId) return Promise.resolve(false);
	return serializeMutation(function () {
		var entries = readIndex();
		var moving = entries.filter(function (item) {
			return item.ownerKey === owner && item.conversationId === conversation && item.messageId === fromId;
		});
		if (!moving.length) return false;
		return Promise.all(moving.map(function (entry) {
			revokeObjectUrl(entry.key);
			var nextKey = entry.key.replace(':' + fromId + ':', ':' + toId + ':');
			if (nextKey === entry.key) nextKey = entry.key + ':moved:' + hashKey(toId);
			if (entry.storage !== 'idb') return Promise.resolve(Object.assign({}, entry, { key: nextKey, messageId: toId }));
			return idbGet(entry.key).then(function (blob) {
				if (!blob) return null;
				return idbPut(nextKey, blob).then(function () {
					return idbDelete(entry.key);
				}).then(function () {
					return Object.assign({}, entry, { key: nextKey, messageId: toId });
				});
			});
		})).then(function (moved) {
			var movedKeys = {};
			moving.forEach(function (item) { movedKeys[item.key] = true; });
			var kept = entries.filter(function (item) { return !movedKeys[item.key]; });
			moved.filter(Boolean).forEach(function (item) { kept.push(item); });
			writeIndex(kept);
			return true;
		});
	});
}

function prune() {
	var maxBytes = maxStorageBytes();
	var entries = readIndex().slice();
	var total = entries.reduce(function (sum, item) { return sum + Math.max(0, Number(item.size) || 0); }, 0);
	if (total <= maxBytes) return Promise.resolve(false);
	entries.sort(function (a, b) { return Number(a.lastAccessAt || 0) - Number(b.lastAccessAt || 0); });
	var removed = [];
	while (entries.length && total > maxBytes) {
		var entry = entries.shift();
		removed.push(entry);
		total -= Math.max(0, Number(entry.size) || 0);
	}
	writeIndex(entries);
	return Promise.all(removed.map(removeLocation)).then(function () { return true; });
}

module.exports = {
	putDataUrl: putDataUrl,
	registerLocalUrl: registerLocalUrl,
	get: get,
	list: list,
	summary: summary,
	removeMatching: removeMatching,
	removeByConversation: removeByConversation,
	removeByOwner: removeByOwner,
	removeByConversationKind: removeByConversationKind,
	removeByOwnerKind: removeByOwnerKind,
	moveMessage: moveMessage,
	prune: prune
};
