const TerserPlugin = require('terser-webpack-plugin');

// Only minimize when doing production build as it takes a long time
// master branch is deployed to dev constantly so we want it to be quick
// even with the expense of having larger bundle (eg. 3.0mb => 4.5mb)
//branch = process.env["CODEBUILD_SOURCE_VERSION"] || "master";
//teet_env = process.env["TEET_ENV"] || "unknown";
//minimize = (teet_env !== "teet-dev2") && (branch !== "master")


// always do minimize unless doing testbuild now, as it is fast after upgrade to webpack 5 (~1 minute)

minimize = (process.env["TEET_TESTBUILD"] !== "1")

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
