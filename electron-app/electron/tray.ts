import { app, nativeImage, Tray, Menu } from 'electron'
import { join } from 'node:path'

let tray: Tray | null = null

export function createTray(show: () => void): Tray {
  if (tray) {
    return tray
  }

  const directory = app.isPackaged ? join(process.resourcesPath, 'assets') : join(__dirname, '../resources')
  const icon = nativeImage.createFromPath(join(directory, process.platform === 'darwin' ? 'trayTemplate.png' : 'icon.png')).resize({ width: 24, height: 24 })
  if (process.platform === 'darwin') icon.setTemplateImage(true)
  tray = new Tray(icon)
  tray.setToolTip('Internal Messenger')
  tray.setContextMenu(Menu.buildFromTemplate([{ label: '메신저 열기', click: show }, { label: '종료', click: () => app.quit() }]))
  tray.on('click', show)
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
