const { app, BrowserWindow, session } = require('electron');
const path = require('path');

const APP_URL = process.env.PEXPO_URL || 'https://pexpo.vercel.app/';

function createWindow() {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 960,
    minHeight: 640,
    title: 'Pexpo Music',
    backgroundColor: '#000000',
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.setTitle('Pexpo Music');
  win.loadURL(APP_URL);
}

app.whenReady().then(async () => {
  await session.defaultSession.clearCache();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
