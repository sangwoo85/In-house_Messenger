import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('messengerDesktop', {
  platform: process.platform,
  showNotification: (title: string, body: string) => ipcRenderer.invoke('notification:show', title, body),
  setBadge: (count: number) => ipcRenderer.invoke('tray:setBadge', count),
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url)
})
