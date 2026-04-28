import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createChannel } from '@/features/channels/channels.api'
import { getUsers } from '@/features/users/users.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'

export function Sidebar(): JSX.Element {
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.user)
  const currentUserId = useAuthStore((state) => state.user?.userId)
  const channels = useChatStore((state) => state.channels)
  const upsertChannel = useChatStore((state) => state.upsertChannel)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const selectChannel = useChatStore((state) => state.selectChannel)
  const viewMode = useUiStore((state) => state.viewMode)
  const setViewMode = useUiStore((state) => state.setViewMode)
  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: getUsers
  })

  const openDirectMessageMutation = useMutation({
    mutationFn: async (targetUserId: string) =>
      createChannel({
        name: null,
        type: 'DM',
        memberUserIds: [targetUserId]
      }),
    onSuccess: (channel) => {
      upsertChannel(channel)
      selectChannel(channel.id)
      setViewMode('chat')
      void queryClient.invalidateQueries({ queryKey: ['channels'] })
    }
  })

  const openDirectMessage = (targetUserId: string) => {
    if (!currentUserId) {
      return
    }

    const existingChannel = channels.find(
      (channel) =>
        channel.type === 'DM' &&
        channel.members.length === 2 &&
        channel.members.includes(currentUserId) &&
        channel.members.includes(targetUserId)
    )

    if (existingChannel) {
      selectChannel(existingChannel.id)
      setViewMode('chat')
      return
    }

    openDirectMessageMutation.mutate(targetUserId)
  }

  return (
    <aside className="flex w-72 flex-col bg-sidebar px-4 py-5 text-white">
      <div className="mb-6 rounded-3xl bg-white/10 p-4">
        <p className="text-xs uppercase tracking-[0.3em] text-blue-200">Messenger</p>
        <div className="mt-4 flex items-center gap-3">
          {currentUser?.profileImageUrl ? (
            <img
              alt={currentUser.userId}
              className="h-14 w-14 rounded-2xl object-cover"
              src={currentUser.profileImageUrl}
            />
          ) : (
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/15 text-lg font-semibold text-blue-100">
              {currentUser?.userId?.slice(0, 1).toUpperCase() ?? '?'}
            </div>
          )}
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-white">{currentUser?.userId ?? '-'}</p>
            <p className="mt-1 truncate text-sm text-slate-300">&nbsp;</p>
          </div>
        </div>
      </div>
      <div className="rounded-2xl bg-white/10 p-4">
        <div className="mb-4 grid grid-cols-3 gap-2 rounded-2xl bg-white/5 p-1">
          <button
            className={['rounded-xl px-3 py-2 text-sm', viewMode === 'users' ? 'bg-white text-sidebar' : 'text-white'].join(' ')}
            onClick={() => setViewMode('users')}
            type="button"
          >
            사용자
          </button>
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
        {viewMode === 'users' ? (
          <>
            <p className="text-sm text-blue-100">사용자 목록</p>
            <div className="mt-4 space-y-3">
              {usersQuery.isLoading ? <p className="text-sm text-slate-300">사용자 목록을 불러오는 중...</p> : null}
              {usersQuery.data?.map((directoryUser) => (
                <button
                  key={directoryUser.id}
                  className="w-full rounded-2xl bg-white/5 px-3 py-3 text-left transition hover:bg-white/10"
                  onDoubleClick={() => openDirectMessage(directoryUser.userId)}
                  type="button"
                >
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-sm font-medium">{directoryUser.nickname}</p>
                    <span
                      className={[
                        'rounded-full px-2 py-0.5 text-[11px] font-semibold',
                        directoryUser.status === 'ONLINE'
                          ? 'bg-emerald-400/20 text-emerald-200'
                          : directoryUser.status === 'AWAY'
                            ? 'bg-amber-400/20 text-amber-200'
                            : 'bg-slate-400/20 text-slate-200'
                      ].join(' ')}
                    >
                      {directoryUser.status}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-slate-300">{directoryUser.userId}</p>
                </button>
              ))}
              {usersQuery.data?.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-white/15 px-4 py-6 text-sm text-slate-300">
                  표시할 사용자가 없습니다.
                </div>
              ) : null}
            </div>
          </>
        ) : viewMode === 'chat' ? (
          <>
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
                  <p className="mt-1 text-xs text-slate-300">
                    {channel.type === 'DM' ? '1:1 대화' : `${channel.members.length}명 참여`}
                  </p>
                </button>
              ))}
            </div>
          </>
        ) : (
          <>
            <p className="text-sm text-blue-100">알림 안내</p>
            <div className="mt-4 rounded-2xl bg-white/5 px-4 py-5 text-sm leading-6 text-slate-300">
              우측 패널에서 사내 프로그램 알림과 공지 이력을 확인할 수 있습니다.
            </div>
          </>
        )}
      </div>
    </aside>
  )
}
