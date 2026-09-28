import { Client, IMessage, ReconnectionTimeMode, StompSubscription } from '@stomp/stompjs'
import { wsUrl } from '@/services/runtime'

type MessageCallback = (message: IMessage) => void

class SocketService {
  private client: Client | null = null
  private accessToken: string | null = null
  private expiryListeners = new Set<() => void>()
  private connectListeners = new Set<() => void>()

  connect(accessToken: string): void {
    if (this.client?.active && this.accessToken !== accessToken) {
      this.disconnect()
    }

    if (this.client?.active) {
      return
    }

    this.accessToken = accessToken
    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`
      },
      reconnectDelay: 1000,
      maxReconnectDelay: 10000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      debug: () => undefined,
      onWebSocketClose: event => {
        if (event.code === 1008) this.expiryListeners.forEach(listener => listener())
      },
      onConnect: () => {
        this.connectListeners.forEach((listener) => listener())
      }
    })

    this.client.activate()
  }

  disconnect(): void {
    void this.client?.deactivate()
    this.client = null
    this.accessToken = null
  }

  subscribe(destination: string, callback: MessageCallback): StompSubscription | null {
    if (!this.client?.connected) {
      return null
    }

    return this.client.subscribe(destination, callback)
  }

  publish(destination: string, body: unknown): boolean {
    if (!this.client?.connected) {
      return false
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body)
    })
    return true
  }

  isConnected(): boolean {
    return Boolean(this.client?.connected)
  }

  onSessionExpired(listener: () => void): () => void {
    this.expiryListeners.add(listener)
    return () => { this.expiryListeners.delete(listener) }
  }

  onConnect(listener: () => void): () => void {
    this.connectListeners.add(listener)
    return () => {
      this.connectListeners.delete(listener)
    }
  }
}

export const socketService = new SocketService()
