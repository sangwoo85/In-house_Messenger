import { Client, IMessage, StompSubscription } from '@stomp/stompjs'

type MessageCallback = (message: IMessage) => void

class SocketService {
  private client: Client | null = null
  private reconnectAttempts = 0

  connect(accessToken: string, onConnect?: () => void): void {
    if (this.client?.active) {
      return
    }

    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`
      },
      reconnectDelay: 0,
      debug: () => undefined,
      onConnect: () => {
        this.reconnectAttempts = 0
        onConnect?.()
      },
      onWebSocketClose: () => {
        this.scheduleReconnect(accessToken, onConnect)
      },
      onStompError: () => {
        this.scheduleReconnect(accessToken, onConnect)
      }
    })

    this.client.activate()
  }

  disconnect(): void {
    void this.client?.deactivate()
    this.client = null
    this.reconnectAttempts = 0
  }

  subscribe(destination: string, callback: MessageCallback): StompSubscription | null {
    if (!this.client?.connected) {
      return null
    }

    return this.client.subscribe(destination, callback)
  }

  publish(destination: string, body: unknown): void {
    if (!this.client?.connected) {
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  private scheduleReconnect(accessToken: string, onConnect?: () => void): void {
    this.reconnectAttempts += 1
    const delay = Math.min(1000 * 2 ** (this.reconnectAttempts - 1), 10000)
    window.setTimeout(() => {
      this.disconnect()
      this.connect(accessToken, onConnect)
    }, delay)
  }
}

export const socketService = new SocketService()

