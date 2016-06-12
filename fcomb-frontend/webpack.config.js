'use strict';

var webpack = require('webpack');
var CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin;
var webPath = __dirname + '/src/main/resources/web';

module.exports = {
  entry: {
    frontend: './bundles/frontend.js'
  },
  output: {
    path: webPath + '/assets',
    publicPath: '/assets/',
    filename: '[name]-bundle.js'
  },
  plugins: [
    new webpack.NoErrorsPlugin(),
    new CommonsChunkPlugin({
      name: 'frontend'
    })
  ],
  module: {
    loaders: [
      {
        test: /\.css$/,
        loader: 'style-loader!css-loader'
      },
      {
        test: /\.(png|jpg|svg)$/,
        loaders: [
          'url-loader?limit=8192',
          'image-webpack?optimizationLevel=7&progressive=true'
        ]
      }
    ]
  },
  devServer: {
    // hot: true,
    contentBase: webPath,
    proxy: {
      '/api/*': {
        target: 'http://localhost:8080',
        rewrite: function(req) {
          req.url = req.url.replace(/^\/api/, '');
        }
      }
    },
    stats: { colors: true },
    quiet: false,
    port: 8899
  }
};
