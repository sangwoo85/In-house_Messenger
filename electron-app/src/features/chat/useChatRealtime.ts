import { useEffect, useRef, useState } from 'react'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { socketService } from '@/socket/socketService'
import { desktop } from '@/services/desktop'
import type { Message } from '@/features/channels/channels.api'
import type { NotificationItem } from '@/features/notifications/notifications.api'

interface TypingPayload {
  channelId: number
  userId: string
  typing: boolean
}

export function useChatRealtime(): void {
  const accessToken = useAuthStore((state) => state.accessToken)
  const userId = useAuthStore((state) => state.user?.userId)
  const clearSession = useAuthStore((state) => state.clearSession)
  const channels = useChatStore((state) => state.channels)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const appendMessage = useChatStore((state) => state.appendMessage)
  const setTypingUsers = useChatStore((state) => state.setTypingUsers)
  const updateUnreadCount = useChatStore((state) => state.updateUnreadCount)
  const setViewMode = useUiStore((state) => state.setViewMode)
  const typingRef = useRef<Record<number, Set<string>>>({})
  const [connectionVersion, setConnectionVersion] = useState(0)

  useEffect(() => {
    if (!accessToken) {
      socketService.disconnect()
      return
    }

    const unsubscribe = socketService.onConnect(() => {
      setConnectionVersion((value) => value + 1)
    })

    socketService.connect(accessToken, () => {
      setConnectionVersion((value) => value + 1)
    })

    if (socketService.isConnected()) {
      setConnectionVersion((value) => value + 1)
    }

    return () => {
      unsubscribe()
      socketService.disconnect()
    }
  }, [accessToken])

  useEffect(() => {
    if (!socketService.isConnected()) {
      return
    }

    const subscriptions = channels.flatMap((channel) => {
      const messageSubscription = socketService.subscribe(`/topic/channel/${channel.id}`, (frame) => {
        const message = JSON.parse(frame.body) as Message
        appendMessage(channel.id, message)
        if (message.senderUserId !== userId) {
          const nextUnread = selectedChannelId === channel.id ? 0 : channel.unreadCount + 1
          updateUnreadCount(channel.id, nextUnread)
          void desktop.showNotification(
            channel.name ?? '새 메시지',
            `${message.senderUserId ?? 'system'}: ${message.content}`
          )
          const totalUnread = channels.reduce((sum, item) => {
            if (item.id === channel.id) {
              return sum + nextUnread
            }
            return sum + item.unreadCount
          }, 0)
          void desktop.setBadge(totalUnread)
        }
      })

      const typingSubscription = socketService.subscribe(`/topic/channel/${channel.id}/typing`, (frame) => {
        const payload = JSON.parse(frame.body) as TypingPayload
        if (payload.userId === userId) {
          return
        }

        const current = typingRef.current[channel.id] ?? new Set<string>()
        if (payload.typing) {
          current.add(payload.userId)
        } else {
          current.delete(payload.userId)
        }
        typingRef.current[channel.id] = current
        setTypingUsers(channel.id, Array.from(current))
      })

      return [messageSubscription, typingSubscription].filter(Boolean)
    })

    const notificationSubscription = socketService.subscribe('/user/queue/notifications', (frame) => {
      const payload = JSON.parse(frame.body) as NotificationItem
      void desktop.showNotification(payload.title, payload.content)
      setViewMode('notifications')
    })

    const sessionExpiredSubscription = socketService.subscribe('/user/queue/session-expired', async () => {
      await desktop.showNotification('세션 만료', '다른 기기에서 로그인되어 현재 세션이 종료되었습니다.')
      clearSession()
    })

    return () => {
      subscriptions.forEach((subscription) => subscription?.unsubscribe())
      notificationSubscription?.unsubscribe()
      sessionExpiredSubscription?.unsubscribe()
    }
  }, [appendMessage, channels, clearSession, connectionVersion, selectedChannelId, setTypingUsers, setViewMode, updateUnreadCount, userId])
}
