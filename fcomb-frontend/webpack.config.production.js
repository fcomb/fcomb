'use strict';

var webpack = require('webpack'),
    CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin,
    webPath = __dirname + '/src/main/resources/web';

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
    }),
    new webpack.optimize.DedupePlugin(),
    new webpack.optimize.UglifyJsPlugin(),
    new webpack.optimize.AggressiveMergingPlugin()
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
      },
      {
        test: /\.json$/,
        loader: 'json'
      }
    ]
  }
};
