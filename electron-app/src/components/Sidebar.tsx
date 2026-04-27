import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'

export function Sidebar(): JSX.Element {
  const channels = useChatStore((state) => state.channels)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const selectChannel = useChatStore((state) => state.selectChannel)
  const viewMode = useUiStore((state) => state.viewMode)
  const setViewMode = useUiStore((state) => state.setViewMode)

  return (
    <aside className="flex w-72 flex-col bg-sidebar px-4 py-5 text-white">
      <div className="mb-6">
        <p className="text-xs uppercase tracking-[0.3em] text-blue-200">Messenger</p>
        <h1 className="mt-2 text-2xl font-semibold">Internal Chat</h1>
      </div>
      <div className="rounded-2xl bg-white/10 p-4">
        <div className="mb-4 grid grid-cols-2 gap-2 rounded-2xl bg-white/5 p-1">
          <button
            className={['rounded-xl px-3 py-2 text-sm', viewMode === 'chat' ? 'bg-white text-sidebar' : 'text-white'].join(' ')}
            onClick={() => setViewMode('chat')}
            type="button"
          >
            채팅
          </button>
          <button
            className={['rounded-xl px-3 py-2 text-sm', viewMode === 'notifications' ? 'bg-white text-sidebar' : 'text-white'].join(' ')}
            onClick={() => setViewMode('notifications')}
            type="button"
          >
            알림
          </button>
        </div>
        <p className="text-sm text-blue-100">채널</p>
        <div className="mt-4 space-y-3">
          {channels.map((channel) => (
            <button
              key={channel.id}
              className={[
                'w-full rounded-2xl px-3 py-3 text-left transition',
                selectedChannelId === channel.id ? 'bg-white/15' : 'bg-white/5 hover:bg-white/10'
              ].join(' ')}
              onClick={() => {
                setViewMode('chat')
                selectChannel(channel.id)
              }}
              type="button"
            >
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">{channel.name ?? channel.members.join(', ')}</p>
                {channel.unreadCount > 0 ? (
                  <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-semibold text-white">
                    {channel.unreadCount}
                  </span>
                ) : null}
              </div>
              <p className="mt-1 text-xs text-slate-300">{channel.type === 'DM' ? '1:1 대화' : `${channel.members.length}명 참여`}</p>
            </button>
          ))}
        </div>
      </div>
    </aside>
  )
}
