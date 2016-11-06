'use strict';

var webpack = require('webpack'),
    CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin,
    HtmlWebpackPlugin = require('html-webpack-plugin'),
    webPath = __dirname + '/src/main/resources/public';

module.exports = {
  entry: {
    app: './bundles/app.js'
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
      inject: true
    }),
    new webpack.NoErrorsPlugin(),
    new CommonsChunkPlugin({
      name: 'app'
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
      },
      {
        test: /\.json$/,
        loader: 'json'
      },
      {
        test: /\.(ttf|eot|svg|woff|woff2)(\?[\s\S]+)?$/,
        loader: 'file'
      }
    ]
  },
  'html-minify-loader': {
     empty: true,
     cdata: true,
     comments: false
  },
  devServer: {
    // hot: true,
    contentBase: webPath,
    proxy: {
      '/v1/*': {
        target: 'http://localhost:8080'
      }
    },
    stats: { colors: true },
    quiet: false,
    port: 8899
  }
};
