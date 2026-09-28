import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('messengerDesktop', {
  platform: process.platform,
  runtime: ipcRenderer.sendSync('runtime:get'),
  authRequest: (input: import('./authTransport').AuthRequest) => ipcRenderer.invoke('auth:request', input),
  showNotification: (title: string, body: string, options?: import('./desktopNotifications').DesktopNotificationOptions) => ipcRenderer.invoke('notification:show', title, body, options),
  clearNotifications: () => ipcRenderer.invoke('notification:clear'),
  /** Registers only a history-open callback; the Electron event itself never crosses the bridge. */
  onNotificationOpen: (callback: (channelId?: number) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, channelId?: number) => callback(channelId)
    ipcRenderer.on('notification:open', listener)
    return () => ipcRenderer.removeListener('notification:open', listener)
  },
  setBadge: (count: number) => ipcRenderer.invoke('tray:setBadge', count),
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url)
})
