type DesktopBridge = Window['messengerDesktop']

function getBridge(): DesktopBridge | null {
  return typeof window !== 'undefined' && window.messengerDesktop ? window.messengerDesktop : null
}

export const desktop = {
  platform(): NodeJS.Platform | 'unknown' {
    return getBridge()?.platform ?? 'unknown'
  },
  async showNotification(title: string, body: string): Promise<void> {
    await getBridge()?.showNotification(title, body)
  },
  async setBadge(count: number): Promise<void> {
    await getBridge()?.setBadge(count)
  },
  async openExternal(url: string): Promise<void> {
    if (getBridge()) {
      await getBridge()?.openExternal(url)
      return
    }

    window.open(url, '_blank', 'noopener,noreferrer')
  }
}
