import { ScheduleReminders } from '@/features/schedules/ScheduleReminders'
import { Sidebar } from '@/components/Sidebar'
import { ChatLayout } from '@/features/chat/ChatLayout'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'
import { UsersPage } from '@/features/users/UsersPage'
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getChannels, heartbeat } from '@/features/channels/channels.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { useChatRealtime } from './useChatRealtime'

export function ChatShell(): JSX.Element {
  const setChannels = useChatStore((state) => state.setChannels)
  const viewMode = useUiStore((state) => state.viewMode)

  useChatRealtime()

  const channelsQuery = useQuery({
    queryKey: ['channels'],
    queryFn: getChannels,
    refetchInterval: 30000
  })

  useEffect(() => {
    if (channelsQuery.data) {
      setChannels(channelsQuery.data)
    }
  }, [channelsQuery.data, setChannels])

  useEffect(() => {
    const sendHeartbeat = (status: 'ONLINE' | 'OFFLINE') => {
      if (!useAuthStore.getState().accessToken) return
      void heartbeat(status).catch(() => undefined)
    }

    sendHeartbeat('ONLINE')
    const intervalId = window.setInterval(() => {
      sendHeartbeat('ONLINE')
    }, 20000)

    return () => {
      window.clearInterval(intervalId)
      sendHeartbeat('OFFLINE')
    }
  }, [])

  if (channelsQuery.isLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#f6f8fb] px-6">
        <div className="w-full max-w-xl rounded-xl bg-white px-10 py-14 text-center border border-slate-200">
          <p className="text-xs uppercase tracking-[0.35em] text-blue-500">Internal Messenger</p>
          <h2 className="mt-4 text-2xl font-medium text-slate-900">워크스페이스를 불러오는 중</h2>
          <p className="mt-3 text-sm leading-6 text-slate-500">채널, 읽지 않은 메시지, 알림 상태를 동기화하고 있습니다.</p>
        </div>
      </main>
    )
  }

  if (channelsQuery.isError) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#f6f8fb] px-6">
        <div className="w-full max-w-xl rounded-xl bg-white px-10 py-14 border border-slate-200">
          <p className="text-xs uppercase tracking-[0.35em] text-red-500">Connection Error</p>
          <h2 className="mt-4 text-2xl font-medium text-slate-900">채널 목록을 불러오지 못했습니다</h2>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            백엔드 실행 상태와 앱의 연결 설정을 확인한 뒤 다시 시도하세요.
          </p>
          <button
            className="mt-8 rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:bg-primary-dark"
            onClick={() => void channelsQuery.refetch()}
            type="button"
          >
            다시 시도
          </button>
        </div>
      </main>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden bg-chat-bg text-slate-900">
      <Sidebar />
      <ScheduleReminders />
      {viewMode === 'users' ? <UsersPage /> : null}
      {viewMode === 'chat' ? <ChatLayout /> : null}
      {viewMode === 'notifications' ? <NotificationsPage /> : null}
    </div>
  )
}
