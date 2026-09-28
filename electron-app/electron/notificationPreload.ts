import { contextBridge, ipcRenderer } from 'electron'

/** The popup can only read its own text, dismiss itself, or open the notification history. */
contextBridge.exposeInMainWorld('messengerNotification', {
  get: () => ipcRenderer.invoke('notification-popup:get'),
  dismiss: () => ipcRenderer.invoke('notification-popup:dismiss'),
  open: () => ipcRenderer.invoke('notification-popup:open')
})
