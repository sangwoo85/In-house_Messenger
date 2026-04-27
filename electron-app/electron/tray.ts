import { app, nativeImage, Tray } from 'electron'

let tray: Tray | null = null

export function createTray(): Tray {
  if (tray) {
    return tray
  }

  tray = new Tray(nativeImage.createEmpty())
  tray.setToolTip('Internal Messenger')
  return tray
}

export function setTrayBadge(count: number): void {
  if (process.platform === 'darwin' && app.dock) {
    app.dock.setBadge(count > 0 ? String(count) : '')
  }

  if (tray) {
    tray.setTitle(count > 0 ? String(count) : '')
  }
}
