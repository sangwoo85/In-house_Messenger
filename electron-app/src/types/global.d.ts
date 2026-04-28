/// <reference types="vite/client" />

export {}

declare global {
  interface Window {
    messengerDesktop: {
      platform: NodeJS.Platform
      showNotification: (title: string, body: string) => Promise<void>
      setBadge: (count: number) => Promise<void>
      openExternal: (url: string) => Promise<void>
    }
  }
}
