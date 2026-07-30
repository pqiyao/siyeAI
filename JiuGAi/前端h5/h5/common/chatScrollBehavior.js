var BOTTOM_HIDE_DISTANCE = 56;
var BOTTOM_SHOW_DISTANCE = 144;
var USER_UP_INTENT_DISTANCE = 800;
var UNKNOWN_DISTANCE_SHOW_INTENT = 40;

function finiteNumber(value) {
	var number = Number(value);
	return Number.isFinite(number) ? number : null;
}

function normalizeState(state) {
	var source = state && typeof state === 'object' ? state : {};
	return {
		atBottom: source.atBottom !== false,
		followBottom: source.followBottom !== false,
		buttonVisible: source.buttonVisible === true,
		upwardIntent: Math.max(0, finiteNumber(source.upwardIntent) || 0)
	};
}

function decideChatScrollState(state, event) {
	var current = normalizeState(state);
	var input = event && typeof event === 'object' ? event : {};
	var distance = finiteNumber(input.distanceToBottom);
	var upwardDelta = Math.max(0, finiteNumber(input.upwardDelta) || 0);
	var downwardDelta = Math.max(0, finiteNumber(input.downwardDelta) || 0);
	var userControlled = input.userControlled === true && input.programmatic !== true;
	var upwardIntent = current.upwardIntent;

	if (userControlled && upwardDelta > 0) {
		upwardIntent += upwardDelta;
	} else if (userControlled && downwardDelta > 0) {
		upwardIntent = Math.max(0, upwardIntent - downwardDelta);
	}

	if (distance != null && distance <= BOTTOM_HIDE_DISTANCE) {
		return {
			atBottom: true,
			followBottom: true,
			buttonVisible: false,
			upwardIntent: 0,
			arrivedAtBottom: true
		};
	}

	var deliberateUp = upwardIntent >= USER_UP_INTENT_DISTANCE;
	var farEnoughToDetach = distance == null || distance >= BOTTOM_SHOW_DISTANCE;
	var shouldDetach = !current.followBottom || (deliberateUp && farEnoughToDetach);
	var followBottom = shouldDetach ? false : current.followBottom;
	var atBottom = followBottom;
	var buttonVisible = current.buttonVisible;

	if (distance != null) {
		if (distance >= BOTTOM_SHOW_DISTANCE && !followBottom) {
			buttonVisible = true;
		}
	} else if (!followBottom && upwardIntent >= UNKNOWN_DISTANCE_SHOW_INTENT) {
		buttonVisible = true;
	}

	return {
		atBottom: atBottom,
		followBottom: followBottom,
		buttonVisible: buttonVisible,
		upwardIntent: upwardIntent,
		arrivedAtBottom: false
	};
}

module.exports = {
	BOTTOM_HIDE_DISTANCE: BOTTOM_HIDE_DISTANCE,
	BOTTOM_SHOW_DISTANCE: BOTTOM_SHOW_DISTANCE,
	USER_UP_INTENT_DISTANCE: USER_UP_INTENT_DISTANCE,
	UNKNOWN_DISTANCE_SHOW_INTENT: UNKNOWN_DISTANCE_SHOW_INTENT,
	decideChatScrollState: decideChatScrollState
};
