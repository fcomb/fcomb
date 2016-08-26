'use strict';

var webpack = require('webpack'),
    CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin,
    HtmlWebpackPlugin = require('html-webpack-plugin'),
    webPath = __dirname + '/src/main/resources/public';

module.exports = {
  entry: {
    frontend: './bundles/frontend.js'
  },
  output: {
    path: webPath,
    publicPath: '/',
    filename: '[name]-bundle.js'
  },
  plugins: [
    new HtmlWebpackPlugin({
      filename: 'index.html',
      template: 'src/main/assets/index.html',
      inject: false
    }),
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
      },
      {
        test: /\.html$/,
        loader: 'raw!html-minify'
      },
      {
        test: /\.(ttf|eot|svg|woff|woff2)(\?[\s\S]+)?$/,
        loader: 'file'
      }
    ]
  }
};
