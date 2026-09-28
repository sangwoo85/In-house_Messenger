import { app, BrowserWindow, ipcMain, Notification, screen } from 'electron'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

export type NotificationDisplayMode = 'SYSTEM' | 'ALWAYS_ON_TOP' | 'SILENT'
export interface DesktopNotificationOptions {
  displayMode?: NotificationDisplayMode
  notificationType?: string
  channelId?: number
}
interface NotificationPayload { title: string; body: string; notificationType: string; channelId?: number }
interface Popup { window: BrowserWindow; payload: NotificationPayload; timer: ReturnType<typeof setTimeout> }
const POPUP_LIFETIME_MS = 15000
const MAX_POPUPS = 3
const POPUP_WIDTH = 380
const POPUP_HEIGHT = 206
const GAP = 12

/** Presents business notifications without granting a popup access to the main renderer bridge. */
export class DesktopNotifications {
  private readonly popups = new Map<number, Popup>()
  private readonly nativeNotifications = new Set<Notification>()
  private readonly popupFile = join(app.isPackaged ? join(process.resourcesPath, 'assets') : join(__dirname, '../resources'), 'notification.html')

  /** Registers popup-only IPC; its sender must be a tracked window's main frame. */
  constructor(private readonly openHistory: (channelId?: number) => void) {
    ipcMain.handle('notification-popup:get', event => this.getPopup(event).payload)
    ipcMain.handle('notification-popup:dismiss', event => this.close(this.getPopup(event).window.id))
    ipcMain.handle('notification-popup:open', event => {
      const popup = this.getPopup(event)
      this.close(popup.window.id)
      this.openHistory(popup.payload.channelId)
    })
  }

  /** Bounds renderer input and selects native, topmost, or history-only delivery. */
  show(title: string, body: string, options: DesktopNotificationOptions = {}): void {
    if (typeof title !== 'string' || typeof body !== 'string' || !options || typeof options !== 'object') return
    const mode = options.displayMode ?? 'SYSTEM'
    if (!['SYSTEM', 'ALWAYS_ON_TOP', 'SILENT'].includes(mode) || mode === 'SILENT') return
    const payload = { channelId: Number.isSafeInteger(options.channelId) && options.channelId! > 0 ? options.channelId : undefined, title: title.slice(0, 200), body: body.slice(0, 2000),
      notificationType: typeof options.notificationType === 'string' ? options.notificationType.slice(0, 50) : 'GENERAL' }
    if (mode === 'ALWAYS_ON_TOP') { this.showPopup(payload); return }
    if (!Notification.isSupported()) return
    const notification = new Notification({ title: payload.title, body: payload.body })
    this.nativeNotifications.add(notification)
    if (options.notificationType) notification.on('click', () => this.openHistory(payload.channelId))
    notification.on('close', () => this.nativeNotifications.delete(notification))
    notification.on('failed', () => this.nativeNotifications.delete(notification))
    notification.show()
  }

  /** Closes visible notifications on logout so the next user cannot see stale content. */
  clear(): void {
    for (const id of [...this.popups.keys()]) this.close(id)
    for (const notification of this.nativeNotifications) notification.close()
    this.nativeNotifications.clear()
  }

  /** Validates both popup identity and the exact local document before serving any IPC. */
  private getPopup(event: Electron.IpcMainInvokeEvent): Popup {
    const window = BrowserWindow.fromWebContents(event.sender)
    const popup = window ? this.popups.get(window.id) : undefined
    if (!popup || event.senderFrame !== event.sender.mainFrame || event.senderFrame.url !== pathToFileURL(this.popupFile).href) {
      throw new Error('허용되지 않은 알림 창입니다.')
    }
    return popup
  }

  /** Shows up to three local notification cards above normal windows without taking keyboard focus. */
  private showPopup(payload: NotificationPayload): void {
    if (this.popups.size >= MAX_POPUPS) this.close(this.popups.keys().next().value!)
    const window = new BrowserWindow({ width: POPUP_WIDTH, height: POPUP_HEIGHT, title: 'Messenger 알림',
      show: false, frame: false, resizable: false, movable: false, minimizable: false, maximizable: false,
      alwaysOnTop: true, skipTaskbar: true, backgroundColor: '#ffffff',
      webPreferences: { preload: join(__dirname, 'notificationPreload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
    })
    this.popups.set(window.id, { window, payload, timer: setTimeout(() => this.close(window.id), POPUP_LIFETIME_MS) })
    window.setAlwaysOnTop(true, 'screen-saver')
    window.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
    window.webContents.on('will-navigate', event => event.preventDefault())
    window.webContents.on('before-input-event', (event, input) => {
      if (input.key === 'F5' || ((input.control || input.meta) && input.key.toLowerCase() === 'r')) event.preventDefault()
    })
    window.once('ready-to-show', () => { if (!window.isDestroyed()) { this.arrange(); window.showInactive() } })
    window.once('closed', () => { this.close(window.id); this.arrange() })
    void window.loadFile(this.popupFile).catch(() => this.close(window.id))
  }

  /** Reflows the bounded stack inside the current monitor's work area. */
  private arrange(): void {
    const { x, y, width, height } = screen.getDisplayNearestPoint(screen.getCursorScreenPoint()).workArea
    const cards = [...this.popups.values()].reverse()
    cards.forEach(({ window }, index) => {
      if (!window.isDestroyed()) window.setPosition(x + width - POPUP_WIDTH - GAP, Math.max(y + GAP, y + height - (index + 1) * (POPUP_HEIGHT + GAP)))
    })
  }

  /** Releases a timer and removes a card before closing its native window. */
  private close(id: number): void {
    const popup = this.popups.get(id)
    if (!popup) return
    clearTimeout(popup.timer)
    this.popups.delete(id)
    if (!popup.window.isDestroyed()) popup.window.close()
    this.arrange()
  }
}
