const TerserPlugin = require('terser-webpack-plugin');

// Only minimize when doing production build as it takes a long time
// master branch is deployed to dev constantly so we want it to be quick
// even with the expense of having larger bundle (eg. 3.0mb => 4.5mb)
branch = process.env["CODEBUILD_SOURCE_VERSION"] || "master";
minimize = branch !== "master"

module.exports = {
    entry: './out/index.js',
    output: {
        path: __dirname + "/out",
        filename: 'main.js'
    },
    optimization: {
        minimize
    }
}
