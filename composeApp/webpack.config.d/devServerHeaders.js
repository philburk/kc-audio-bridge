// composeApp/webpack.config.d/devServerHeaders.js

// Ensure config.devServer exists, as it might not if you're not running a dev server task
if (config.devServer) {
  // Ensure headers object exists or initialize it
  config.devServer.headers = config.devServer.headers || {};

  // Add or override your specific headers
  config.devServer.headers['Cross-Origin-Opener-Policy'] = 'same-origin';
  config.devServer.headers['Cross-Origin-Embedder-Policy'] = 'require-corp';
} else {
  // You might want to log if devServer is not defined,
  // though this script primarily affects dev server runs.
  // console.log("webpack.config.d: config.devServer is not defined. This is normal for production builds.");
}

// If you need to modify other parts of the webpack config, you can do so here.
// For example:
// config.resolve.alias = { ... };
