module.exports = function uniTemplateExportCompatLoader(source) {
  var prefix = '';

  if (!/\b(recyclableRender)\b/.test(source.replace(/export\s*\{[\s\S]*?\}/g, ''))) {
    prefix += 'var recyclableRender = false;\n';
  }

  if (!/\b(components)\b/.test(source.replace(/export\s*\{[\s\S]*?\}/g, ''))) {
    prefix += 'var components = undefined;\n';
  }

  return prefix + source;
};
