import { app, BrowserWindow, ipcMain, Notification, shell } from 'electron'
import { join } from 'node:path'
import { createTray, setTrayBadge } from './tray'

function createWindow(): void {
  const window = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1100,
    minHeight: 720,
    title: 'Internal Messenger',
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void window.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void window.loadFile(join(__dirname, '../index.html'))
  }
}

app.whenReady().then(() => {
  createWindow()
  createTray()

  ipcMain.handle('notification:show', (_, title: string, body: string) => {
    new Notification({ title, body }).show()
  })

  ipcMain.handle('tray:setBadge', (_, count: number) => {
    setTrayBadge(count)
  })

  ipcMain.handle('shell:openExternal', (_, url: string) => shell.openExternal(url))

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
