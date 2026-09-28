import { useState } from 'react'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { getNotifications, getNotices, markNotificationRead } from './notifications.api'
import { desktop } from '@/services/desktop'

/** Labels the chosen delivery mode without filtering silent items out of history. */
function deliveryLabel(mode: string): string {
  return mode === 'ALWAYS_ON_TOP' ? '상단 알림' : mode === 'SILENT' ? '내역만 표시' : '시스템 알림'
}

/** Shows business notifications and notices independently of their desktop presentation. */
export function NotificationsPage(): JSX.Element {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<'notifications' | 'notices'>('notifications')
  const [error, setError] = useState<string | null>(null)
  const notifications = useInfiniteQuery({ queryKey: ['notifications', 'pages'], initialPageParam: 0,
    queryFn: ({ pageParam }) => getNotifications(pageParam),
    getNextPageParam: last => (last.page + 1) * last.size < last.totalElements ? last.page + 1 : undefined })
  const notices = useInfiniteQuery({ queryKey: ['notices'], initialPageParam: 0,
    queryFn: ({ pageParam }) => getNotices(pageParam),
    getNextPageParam: last => (last.page + 1) * last.size < last.totalElements ? last.page + 1 : undefined })
  /** Marks owned notifications as read before opening an optional HTTP(S) business link. */
  const handleOpen = async (id: number, link: string | null) => {
    try {
      setError(null)
      await markNotificationRead(id)
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      if (link) await desktop.openExternal(link)
    } catch { setError('알림을 처리하지 못했습니다. 다시 시도해 주세요.') }
  }
  const selected = tab === 'notices' ? notices : notifications
  return <main className="flex min-h-0 flex-1 flex-col bg-white">
    <header className="border-b border-[#e7ecf2] bg-white px-8 py-6"><h2 className="messenger-page-title">공지와 알림</h2>
      <div className="mt-4 flex gap-2">{(['notifications', 'notices'] as const).map(value => <button type="button" key={value} onClick={() => setTab(value)}
        className={`rounded-lg px-4 py-2 text-sm ${tab === value ? 'bg-primary text-white' : 'bg-slate-100'}`}>{value === 'notifications' ? '개인 알림' : '전체 공지'}</button>)}</div>
    </header>
    <section className="min-h-0 flex-1 space-y-0 overflow-y-auto px-8 py-6">
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      {selected.isLoading && <p>불러오는 중...</p>}
      {selected.isError && <button onClick={() => void selected.refetch()} className="text-red-600">조회에 실패했습니다. 다시 시도</button>}
      {tab === 'notifications' ? notifications.data?.pages.flatMap(page => page.items).map(item =>
        <button type="button" key={item.id} onClick={() => void handleOpen(item.id, item.linkUrl)} className={`block w-full border-b border-[#e7ecf2] px-2 py-6 text-left ${item.read ? 'bg-white' : 'bg-[#f5f8ff]'}`}>
          <p className="mb-2 text-[11px] font-medium text-slate-500">{item.notificationType ?? 'GENERAL'} · {deliveryLabel(item.displayMode)}</p>
          <p className="font-medium">{item.title} {!item.read && <span className="ml-2 text-xs text-primary">NEW</span>}</p>
          <p className="mt-2 whitespace-pre-wrap text-sm text-slate-600">{item.content}</p><time className="mt-3 block text-xs text-slate-500">{new Date(item.createdAt).toLocaleString()}</time>
        </button>) : notices.data?.pages.flatMap(page => page.items).map(item =>
        <article key={item.id} className="border-b border-[#e7ecf2] bg-white px-2 py-6"><p className="mb-2 text-[11px] font-medium text-slate-500">{item.notificationType ?? 'GENERAL'} · {deliveryLabel(item.displayMode)}</p><h3 className="font-medium">{item.title}</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm text-slate-600">{item.content}</p><p className="mt-3 text-xs text-slate-500">{item.sender} · {new Date(item.createdAt).toLocaleString()}</p></article>)}
      {selected.data?.pages[0].totalElements === 0 && <p className="py-8 text-center text-slate-500">아직 받은 항목이 없습니다.</p>}
      {selected.hasNextPage && <button className="rounded-xl bg-white px-5 py-3 text-sm shadow" disabled={selected.isFetchingNextPage} onClick={() => void selected.fetchNextPage()}>{selected.isFetchingNextPage ? '불러오는 중...' : '이전 항목 더 보기'}</button>}
    </section>
  </main>
}
