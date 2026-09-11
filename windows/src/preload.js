const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('pexpoDesktop', {
  platform: process.platform,
  version: '1.1.0'
});
