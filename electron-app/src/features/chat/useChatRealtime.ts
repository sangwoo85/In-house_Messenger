import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import type { NotificationItem, NoticeItem } from '@/features/notifications/notifications.api'
import { socketService } from '@/socket/socketService'
import { desktop } from '@/services/desktop'
import type { Message } from '@/features/channels/channels.api'
import type { StompSubscription } from '@stomp/stompjs'

/** Subscribes once per session and updates data before applying desktop delivery options. */
export function useChatRealtime(): void {
  const queryClient = useQueryClient()
  const accessToken = useAuthStore(state => state.accessToken)
  const userId = useAuthStore(state => state.user?.userId)
  const selectedChannelId = useChatStore(state => state.selectedChannelId)
  const [connectionVersion, setConnectionVersion] = useState(0)

  useEffect(() => {
    if (!accessToken) return
    let subscriptions: StompSubscription[] = []
    const clearSubscriptions = () => { subscriptions.forEach(item => { try { item.unsubscribe() } catch { /* Disconnected. */ } }); subscriptions = [] }
    const expired = () => {
      useAuthStore.getState().clearSession()
      void desktop.showNotification('세션 만료', '다시 로그인해 주세요.')
    }
    const connect = () => {
      clearSubscriptions()
      const subscribe = (destination: string, callback: (body: string) => void) => {
        const subscription = socketService.subscribe(destination, frame => callback(frame.body))
        if (subscription) subscriptions.push(subscription)
      }
      // A user queue exists independently of the channel list, including newly-created DMs.
      subscribe('/user/queue/messages', body => {
        const { kind, message } = JSON.parse(body) as { kind: 'CREATED' | 'UPDATED' | 'DELETED'; message: Message }
        const state = useChatStore.getState()
        const duplicate = (state.messages[message.channelId] ?? []).some(item => item.id === message.id)
        state.appendMessage(message.channelId, message)
        const channel = state.channels.find(item => item.id === message.channelId)
        if (!channel || kind !== 'CREATED') void queryClient.invalidateQueries({ queryKey: ['channels'] })
        if (kind !== 'CREATED' || duplicate || message.senderUserId === userId) return
        const reading = state.readingChannelId === message.channelId && document.visibilityState === 'visible' && document.hasFocus()
        if (!reading) {
          state.incrementUnreadCount(message.channelId)
          void desktop.showNotification(channel?.name ?? '새 메시지', `${message.senderUserId}: ${message.content}`)
        }
      })
      subscribe('/user/queue/channel-events', body => {
        const event = JSON.parse(body) as { channelId: number; kind: string }
        void queryClient.invalidateQueries({ queryKey: ['channels'] })
        void queryClient.invalidateQueries({ queryKey: ['schedules', event.channelId] })
        void queryClient.invalidateQueries({ queryKey: ['schedule-reminders'] })
        if (event.kind === 'MEMBERSHIP') void queryClient.invalidateQueries({ queryKey: ['messages', event.channelId] })
      })
      subscribe('/user/queue/notifications', body => {
        const item = JSON.parse(body) as NotificationItem
        void queryClient.invalidateQueries({ queryKey: ['notifications'] })
        void desktop.showNotification(item.title, item.content, { displayMode: item.displayMode, notificationType: item.notificationType })
      })
      subscribe('/topic/notice', body => {
        const item = JSON.parse(body) as NoticeItem
        void queryClient.invalidateQueries({ queryKey: ['notices'] })
        void desktop.showNotification(item.title, item.content, { displayMode: item.displayMode, notificationType: item.notificationType })
      })
      subscribe('/user/queue/session-expired', expired)
      setConnectionVersion(version => version + 1)
      for (const key of ['channels', 'messages', 'notifications', 'notices', 'users', 'schedules', 'schedule-reminders']) void queryClient.invalidateQueries({ queryKey: [key] })
    }
    const unsubscribeNotificationOpen = desktop.onNotificationOpen(channelId => {
      if (channelId && useChatStore.getState().channels.some(channel => channel.id === channelId)) {
        useChatStore.getState().selectChannel(channelId); useUiStore.getState().setViewMode('chat')
      } else useUiStore.getState().setViewMode('notifications')
    })
    const unsubscribeConnect = socketService.onConnect(connect)
    const unsubscribeExpiry = socketService.onSessionExpired(expired)
    socketService.connect(accessToken)
    if (socketService.isConnected()) connect()
    return () => {
      unsubscribeNotificationOpen()
      unsubscribeConnect()
      unsubscribeExpiry()
      clearSubscriptions()
      socketService.disconnect()
      void desktop.clearNotifications()
    }
  }, [accessToken, queryClient, userId])

  useEffect(() => {
    if (!selectedChannelId || !socketService.isConnected()) return
    const timers = new Map<string, number>()
    const update = () => useChatStore.getState().setTypingUsers(selectedChannelId, [...timers.keys()])
    const subscription = socketService.subscribe(`/topic/channel/${selectedChannelId}/typing`, frame => {
      const event = JSON.parse(frame.body) as { userId: string; typing: boolean }
      if (event.userId === userId) return
      window.clearTimeout(timers.get(event.userId))
      timers.delete(event.userId)
      if (event.typing) timers.set(event.userId, window.setTimeout(() => { timers.delete(event.userId); update() }, 5000))
      update()
    })
    return () => {
      timers.forEach(timer => window.clearTimeout(timer))
      useChatStore.getState().setTypingUsers(selectedChannelId, [])
      try { subscription?.unsubscribe() } catch { /* Disconnected. */ }
    }
  }, [selectedChannelId, connectionVersion, userId])
}
