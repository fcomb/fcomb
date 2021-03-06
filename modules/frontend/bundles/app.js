require('normalize.css');
require('bootstrap-grid');
require('roboto-npm-webfont');

window.ReactDOM = require('react-dom');
window.React    = require('react');

var injectTapEventPlugin = require('react-tap-event-plugin');
injectTapEventPlugin();

window.ReactMarkdown        = require('react-markdown');
window.ReactCopyToClipboard = require('react-copy-to-clipboard');

window.mui          = require("material-ui");
window.mui.Styles   = require("material-ui/styles");
window.mui.SvgIcons = require('material-ui/svg-icons/index');

require('../target/scala-2.12/frontend-opt.js');

io.fcomb.frontend.Main().main();
