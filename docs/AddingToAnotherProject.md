[Home](/README.md)

# How to Add AudioBridge to Another Project

1. Copy com.mobileer.audiobridge packages from desktopMain, wasmJsMain, androidMain and commonMain to your source tree.
1. Copy [composeApp/webpack.config.d](../composeApp/webpack.config.d) so that the web headers are set correctly.
1. Copy [wasmJsMain/resources/kcab-output-stream.js](../composeApp/src/wasmJsMain/resources/kcab-output-stream.js) to your source.
1. Copy [wasmJsMain/resources/kcab-webaudio.js](../composeApp/src/wasmJsMain/resources/kcab-webaudio.js) to your source.
1. Modify your [wasmJsMain/resources/index.html](../composeApp/src/wasmJsMain/resources/index.html) so that it loads the webaudio.js code.

```
    <script type="application/javascript" src="composeApp.js"></script>
    <script type="module" src="webaudio.js"></script>
</head>
```
