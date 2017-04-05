'use strict';

var webpack = require('webpack'),
    CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin,
    CompressionPlugin = require("compression-webpack-plugin"),
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
      inject: true,
      hash: true,
      minify: {
        collapseBooleanAttributes: true,
        collapseInlineTagWhitespace: true,
        collapseWhitespace: true,
        decodeEntities: true,
        html5: true,
        minifyCSS: true,
        minifyJS: true,
        removeAttributeQuotes: true,
        removeComments: true,
        removeEmptyAttributes: true,
        removeRedundantAttributes: true,
        removeScriptTypeAttributes: true,
        removeStyleLinkTypeAttributes: true,
        sortAttributes: true,
        useShortDoctype: true
      }
    }),
    new webpack.NoEmitOnErrorsPlugin(),
    new CommonsChunkPlugin({
      name: 'app'
    }),
    new webpack.DefinePlugin({
      'process.env':{
        'NODE_ENV': JSON.stringify('production')
      }
    }),
    new webpack.optimize.UglifyJsPlugin({
      output: {
        comments: false
      },
      compressor: {
        warnings: false
      }
    }),
    new webpack.optimize.AggressiveMergingPlugin(),
    new CompressionPlugin({
      asset: "[path].gz",
      algorithm: "gzip",
      test: /\.(js|html|css|ttf)$/,
      threshold: 10240
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
          'image-webpack-loader?optimizationLevel=7&progressive=true'
        ]
      },
      {
        test: /\.json$/,
        loader: 'json-loader'
      },
      {
        test: /\.html$/,
        loader: 'html-loader'
      },
      {
        test: /\.(ttf|eot|svg|woff|woff2)(\?[\s\S]+)?$/,
        loader: 'file-loader'
      }
    ]
  }
};
