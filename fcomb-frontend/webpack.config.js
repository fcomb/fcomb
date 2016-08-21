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
      template: 'src/main/assets/index.html'
    }),
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
      },
      {
        test: /\.json$/,
        loader: 'json'
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
