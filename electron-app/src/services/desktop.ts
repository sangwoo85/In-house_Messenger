type DesktopBridge = Window['messengerDesktop']

/** Returns the narrow Electron bridge; browser previews cannot create native windows. */
function getBridge(): DesktopBridge | null {
  return typeof window !== 'undefined' && window.messengerDesktop ? window.messengerDesktop : null
}

export const desktop = {
  platform(): NodeJS.Platform | 'unknown' {
    return getBridge()?.platform ?? 'unknown'
  },
  /** Applies the business system's delivery option while history remains server-side. */
  async showNotification(title: string, body: string, options?: import('../../electron/desktopNotifications').DesktopNotificationOptions): Promise<void> {
    await getBridge()?.showNotification(title, body, options)
  },
  /** Removes notifications when the authenticated desktop session ends. */
  async clearNotifications(): Promise<void> {
    await getBridge()?.clearNotifications()
  },
  /** Opens notification history when the user clicks a desktop notification. */
  onNotificationOpen(callback: (channelId?: number) => void): () => void {
    return getBridge()?.onNotificationOpen(callback) ?? (() => {})
  },
  async setBadge(count: number): Promise<void> {
    await getBridge()?.setBadge(count)
  },
  async openExternal(url: string): Promise<void> {
    let parsedUrl: URL
    try {
      parsedUrl = new URL(url)
    } catch {
      return
    }
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
      return
    }

    if (getBridge()) {
      await getBridge()?.openExternal(parsedUrl.toString())
      return
    }

    window.open(parsedUrl.toString(), '_blank', 'noopener,noreferrer')
  }
}
