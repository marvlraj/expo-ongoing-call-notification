let plugin;
try {
  plugin = require('./build/plugin/src/index');
} catch (e) {
  try {
    plugin = require('./plugin/build/index');
  } catch (err) {
    plugin = require('./plugin/src/index');
  }
}
module.exports = plugin.default || plugin;

