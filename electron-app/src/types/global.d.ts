/// <reference types="vite/client" />

export {}

declare global {
  interface Window {
    messengerDesktop: {
      runtime: { apiOrigin: string; wsUrl: string; configPath: string }
      authRequest: (request: { action: 'login' | 'refresh' | 'logout'; body?: { emprId: string; password: string }; accessToken?: string }) => Promise<{ status: number; data: unknown }>
      platform: NodeJS.Platform
      showNotification: (title: string, body: string, options?: import('../../electron/desktopNotifications').DesktopNotificationOptions) => Promise<void>
      clearNotifications: () => Promise<void>
      onNotificationOpen: (callback: (channelId?: number) => void) => () => void
      setBadge: (count: number) => Promise<void>
      openExternal: (url: string) => Promise<void>
    }
  }
}
