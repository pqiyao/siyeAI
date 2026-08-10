function fieldText(value, fallback) {
	if (value === undefined || value === null || value === '') {
		return String(fallback);
	}
	return String(value);
}

function requiredNumber(value) {
	const text = value === undefined || value === null ? '' : String(value).trim();
	if (!text) {
		return { valid: false, value: NaN };
	}
	const number = Number(text);
	return { valid: Number.isFinite(number), value: number };
}

module.exports = {
	fieldText,
	requiredNumber
};
