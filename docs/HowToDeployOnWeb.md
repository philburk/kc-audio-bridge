[Home](/README.md)

# How to Deploy AudioBridge app on the Web

The Kotlin code will be compiled to Web Assembly (WASM) and JavaScript.
These files can be placed on your website.

## Prepare your Web Server to Support WASM

You need to tell your web server to support WASM files.
For Apache, do something like the steps below. Your configuration may differ.

1. Enter:   cd /etc/httpd/conf
1. Use vi or your favorite editor:   sudo vi httpd.conf
1. Search for "AddType".
1. Add this line:    AddType application/wasm .wasm
1. Restart Apache by entering:    sudo systemctl restart httpd

## Create an Android Studio Configuration to Build an App for your Website

1. Click the red button to stop any running apps. (Android Studio build can hang when running apps on multiple devices as of 2025-09-22)
1. Go to the Configuration menu and select "Edit Configurations...
1. Click the "+" to add a configuration.
1. Select Gradle from the popup menu.
1. Set the name to:   kc-audio-bridge [wasJsBrowserDistribution]
1. Set the Run field to:   wasmJsBrowserDistribution
1. In Gradle Project menu, select: kc-audio-bridge
1. Click OK button.
  
## Build the App and Upload it Web

1. Click the triangular run button. It will build for a while.
1. When it finishes building, look for the folder "kc-audio-bridge/composeApp/build/dist/wasmJs/productionExecutable".
1. Upload the contents of that folder to the apps folder on your website.
1. Make sure you include the hidden .htaccess file. It can be seen using:   ls -al {folder}
1. You may need to run the JavaScript Console to see error messages.

