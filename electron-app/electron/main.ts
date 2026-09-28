import { app, BrowserWindow, ipcMain, Menu, shell, dialog } from 'electron'
import { join } from 'node:path'
import { createTray, setTrayBadge } from './tray'
import { readRuntimeConfig } from './runtimeConfig'
import { requestAuth, type AuthRequest } from './authTransport'
import { pathToFileURL } from 'node:url'
import { DesktopNotifications, type DesktopNotificationOptions } from './desktopNotifications'

let mainWindow: BrowserWindow | null = null
let quitting = false
let notifications: DesktopNotifications | null = null
/** Compares renderer documents independently of client-side hash navigation. */
function documentUrl(value: string): string {
  const url = new URL(value)
  url.hash = ''
  return url.href
}

const rendererUrl = documentUrl(process.env.ELECTRON_RENDERER_URL ?? pathToFileURL(join(__dirname, '../out/renderer/index.html')).href)

/** Allows only the configured application document as the main renderer. */
function isRendererUrl(value: string): boolean {
  try { return documentUrl(value) === rendererUrl }
  catch { return false }
}

/** Rejects renderer IPC from popups, subframes, and other documents. */
function trusted(event: Electron.IpcMainEvent | Electron.IpcMainInvokeEvent): void {
  if (!mainWindow || event.sender !== mainWindow.webContents || event.senderFrame !== mainWindow.webContents.mainFrame
      || !isRendererUrl(event.senderFrame.url)) throw new Error('허용되지 않은 창입니다.')
}

/** Creates the desktop-only application window and blocks browser-style reload shortcuts. */
function createWindow(): void {
  const window = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1100,
    minHeight: 720,
    title: 'Internal Messenger',
    icon: join(app.isPackaged ? join(process.resourcesPath, 'assets') : join(__dirname, '../resources'), 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false
    }
  })

  mainWindow = window
  window.webContents.on('before-input-event', (event, input) => {
    if (input.key === 'F5' || ((input.control || input.meta) && input.key.toLowerCase() === 'r')) event.preventDefault()
  })
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  window.webContents.on('will-navigate', (event, url) => {
    if (!isRendererUrl(url)) event.preventDefault()
  })
  window.on('close', event => {
    if (!quitting) { event.preventDefault(); window.hide() }
  })
  if (process.env.ELECTRON_RENDERER_URL) {
    void window.loadURL(rendererUrl)
  } else {
    void window.loadFile(join(__dirname, '../out/renderer/index.html'))
  }

  mainWindow = window
  window.on('closed', () => {
    if (mainWindow === window) {
      mainWindow = null
    }
  })
  window.on('focus', () => {
    window.flashFrame(false)
  })
}

if (process.platform === 'win32') {
  app.setAppUserModelId('com.company.messenger')
}

app.on('before-quit', () => { quitting = true; notifications?.clear() })
app.whenReady().then(() => {
  let config: ReturnType<typeof readRuntimeConfig>
  try { config = readRuntimeConfig() }
  catch (error) { dialog.showErrorBox('메신저 설정 오류', String(error)); app.quit(); return }
  // Keep editing shortcuts available without installing Electron's default Reload menu items.
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' as const }] : []),
    { role: 'editMenu' }
  ]))
  notifications = new DesktopNotifications(channelId => {
    mainWindow?.show()
    mainWindow?.focus()
    mainWindow?.webContents.send('notification:open', channelId)
  })
  ipcMain.on('runtime:get', event => { trusted(event); event.returnValue = config })
  ipcMain.handle('auth:request', (event, request: AuthRequest) => {
    trusted(event)
    if (request.action === 'logout') notifications?.clear()
    return requestAuth(config.apiOrigin, request)
  })
  createWindow()
  createTray(() => { mainWindow?.show(); mainWindow?.focus() })

  ipcMain.handle('notification:show', (event, title: string, body: string, options?: DesktopNotificationOptions) => {
    trusted(event)
    notifications?.show(title, body, options)
  })
  ipcMain.handle('notification:clear', event => { trusted(event); notifications?.clear() })

  ipcMain.handle('tray:setBadge', (event, count: number) => {
    trusted(event)
    setTrayBadge(count)
  })

  ipcMain.handle('shell:openExternal', (event, url: string) => {
    trusted(event)
    try {
      const parsedUrl = new URL(url)
      if (parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:') {
        return shell.openExternal(parsedUrl.toString())
      }
    } catch {
      return
    }
  })

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
    mainWindow?.show()
    mainWindow?.focus()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
