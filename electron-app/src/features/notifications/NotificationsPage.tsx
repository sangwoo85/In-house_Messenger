import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getNotifications, markNotificationRead } from './notifications.api'
import { desktop } from '@/services/desktop'

export function NotificationsPage(): JSX.Element {
  const queryClient = useQueryClient()
  const notificationsQuery = useQuery({
    queryKey: ['notifications'],
    queryFn: getNotifications
  })

  useEffect(() => {
    if (notificationsQuery.data) {
      const unread = notificationsQuery.data.items.filter((item) => !item.read).length
      void desktop.setBadge(unread)
    }
  }, [notificationsQuery.data])

  const handleOpen = async (id: number, linkUrl: string | null) => {
    await markNotificationRead(id)
    await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    if (linkUrl) {
      await desktop.openExternal(linkUrl)
    }
  }

  return (
    <main className="flex flex-1 flex-col bg-[linear-gradient(180deg,_#f8fbff,_#eef4fb)]">
      <header className="border-b border-slate-200 bg-white/80 px-8 py-5 backdrop-blur">
        <h2 className="text-xl font-semibold text-slate-900">알림</h2>
        <p className="mt-1 text-sm text-slate-500">사내 프로그램 연동 알림과 읽음 상태를 관리합니다.</p>
      </header>
      <section className="flex-1 overflow-y-auto px-8 py-6">
        <div className="space-y-4">
          {notificationsQuery.data?.items.map((item) => (
            <button
              key={item.id}
              className={[
                'w-full rounded-3xl border px-5 py-4 text-left shadow-sm transition',
                item.read
                  ? 'border-slate-200 bg-white hover:border-slate-300'
                  : 'border-blue-200 bg-blue-50 hover:border-blue-300'
              ].join(' ')}
              onClick={() => void handleOpen(item.id, item.linkUrl)}
              type="button"
            >
              <div className="flex items-center justify-between gap-4">
                <p className="text-sm font-semibold text-slate-900">{item.title}</p>
                {!item.read ? (
                  <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-semibold text-white">NEW</span>
                ) : null}
              </div>
              <p className="mt-2 text-sm leading-6 text-slate-600">{item.content}</p>
              <p className="mt-3 text-xs text-slate-400">{new Date(item.createdAt).toLocaleString()}</p>
            </button>
          ))}
          {notificationsQuery.data?.items.length === 0 ? (
            <div className="rounded-3xl border border-dashed border-slate-300 bg-white/70 px-6 py-10 text-center text-sm text-slate-500">
              수신된 알림이 없습니다.
            </div>
          ) : null}
        </div>
      </section>
    </main>
  )
}
