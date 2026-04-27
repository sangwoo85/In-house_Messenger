import { Sidebar } from '@/components/Sidebar'
import { ChatLayout } from '@/features/chat/ChatLayout'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getChannels, heartbeat } from '@/features/channels/channels.api'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { useChatRealtime } from './useChatRealtime'

export function ChatShell(): JSX.Element {
  const setChannels = useChatStore((state) => state.setChannels)
  const viewMode = useUiStore((state) => state.viewMode)

  useChatRealtime()

  const channelsQuery = useQuery({
    queryKey: ['channels'],
    queryFn: getChannels
  })

  useEffect(() => {
    if (channelsQuery.data) {
      setChannels(channelsQuery.data)
      void window.messengerDesktop.setBadge(
        channelsQuery.data.reduce((sum, channel) => sum + channel.unreadCount, 0)
      )
    }
  }, [channelsQuery.data, setChannels])

  useEffect(() => {
    void heartbeat('ONLINE')
    const intervalId = window.setInterval(() => {
      void heartbeat('ONLINE')
    }, 20000)

    return () => {
      window.clearInterval(intervalId)
      void heartbeat('OFFLINE')
    }
  }, [])

  return (
    <div className="flex min-h-screen bg-chat-bg text-slate-900">
      <Sidebar />
      {viewMode === 'chat' ? <ChatLayout /> : <NotificationsPage />}
    </div>
  )
}
