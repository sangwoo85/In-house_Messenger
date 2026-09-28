import { OrganizationTree } from '@/features/users/OrganizationTree'
import { getOrganizations } from '@/features/users/organizations.api'
import { ProfileSettings } from '@/features/users/ProfileSettings'
import { Avatar } from './Avatar'
import { Icon } from './Icon'
import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { GroupChannelDialog } from '@/features/channels/GroupChannelDialog'
import { createChannel } from '@/features/channels/channels.api'
import { formatChannelTime } from '@/features/channels/channelTime'
import { logout } from '@/features/auth/auth.api'
import { getNotifications } from '@/features/notifications/notifications.api'
import { getUsers } from '@/features/users/users.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { desktop } from '@/services/desktop'

type NameSortDirection = 'asc' | 'desc'

/** 메뉴와 대화방의 안읽음 개수를 짧은 배지로 표시한다. */
function formatBadgeCount(count: number): string {
  return count > 99 ? '99+' : String(count)
}

/** 읽지 않은 항목이 있을 때만 배지를 노출한다. */
function TabBadge({ count }: { count: number }): JSX.Element | null {
  if (count <= 0) {
    return null
  }

  return (
    <span className="ml-1 inline-flex min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-bold leading-5 text-white">
      {formatBadgeCount(count)}
    </span>
  )
}

/** 탐색 메뉴, 실시간 사용자 상태, 조직 계층과 내 프로필 진입점을 제공한다. */
export function Sidebar(): JSX.Element {
  const queryClient = useQueryClient()
  const [showProfile, setShowProfile] = useState(false)
  const [now, setNow] = useState(() => new Date())
  const [showGroupDialog, setShowGroupDialog] = useState(false)
  const currentUser = useAuthStore((state) => state.user)
  const currentUserId = useAuthStore((state) => state.user?.userId)
  const clearSession = useAuthStore((state) => state.clearSession)
  const channels = useChatStore((state) => state.channels)
  const upsertChannel = useChatStore((state) => state.upsertChannel)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const selectChannel = useChatStore((state) => state.selectChannel)
  const viewMode = useUiStore((state) => state.viewMode)
  const setViewMode = useUiStore((state) => state.setViewMode)
  const [nameSortDirection, setNameSortDirection] = useState<NameSortDirection>('asc')
  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: getUsers,
    refetchInterval: 20000
  })
  const organizationsQuery = useQuery({
    queryKey: ['organizations'],
    queryFn: getOrganizations,
    staleTime: 60000,
    refetchInterval: 60000
  })
  const notificationsQuery = useQuery({
    queryKey: ['notifications'],
    queryFn: () => getNotifications(0)
  })

  const chatUnreadCount = channels.reduce((sum, channel) => sum + channel.unreadCount, 0)
  const notificationUnreadCount =
    notificationsQuery.data?.unreadCount ?? 0

  useEffect(() => {
    void desktop.setBadge(chatUnreadCount + notificationUnreadCount)
  }, [chatUnreadCount, notificationUnreadCount])

  // Keep “today” labels correct when the app stays open across midnight.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60000)
    return () => window.clearInterval(timer)
  }, [])

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

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      clearSession()
    }
  })

  /** 기존 1:1 방이 있으면 재사용하고 없으면 서버에서 생성한다. */
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

  const nameFor = (id: string) => usersQuery.data?.find(user => user.userId === id)?.nickname ?? id
  return (
    <aside className="messenger-sidebar">
      <div className="messenger-brand"><span className="messenger-logo"><Icon name="chat" /></span><div><h1>Messenger</h1></div></div>
      <nav className="messenger-nav" aria-label="메인 메뉴">
        <button type="button" aria-pressed={viewMode === 'chat'} onClick={() => setViewMode('chat')}><Icon name="chat" />대화<TabBadge count={chatUnreadCount} /></button>
        <button type="button" aria-pressed={viewMode === 'users'} onClick={() => setViewMode('users')}><Icon name="users" />조직</button>
        <button type="button" aria-pressed={viewMode === 'notifications'} onClick={() => setViewMode('notifications')}><Icon name="bell" />알림<TabBadge count={notificationUnreadCount} /></button>
      </nav>
      <div className="messenger-sidebar-content">
        <button type="button" onClick={() => setShowGroupDialog(true)} className="messenger-new-group"><Icon name="plus" />새 그룹 대화</button>
        {showGroupDialog && <GroupChannelDialog onClose={() => setShowGroupDialog(false)} />}
        {viewMode === 'users' ? <>
          <div className="messenger-list-heading"><span>함께 일하는 동료</span><button type="button" onClick={() => setNameSortDirection(current => current === 'asc' ? 'desc' : 'asc')}>이름 {nameSortDirection === 'asc' ? '↑' : '↓'}</button></div>
          <div className="messenger-sidebar-scroll">
            {usersQuery.isLoading && <p className="messenger-sidebar-hint">사용자 목록을 불러오는 중...</p>}
            {usersQuery.isError && <p className="messenger-sidebar-error">사용자 목록을 불러오지 못했습니다.</p>}
            {openDirectMessageMutation.isError && <p className="messenger-sidebar-error">대화방을 열지 못했습니다.</p>}
            {organizationsQuery.isError && <p className="messenger-sidebar-error">조직을 불러오지 못했습니다. <button onClick={() => void organizationsQuery.refetch()}>다시 시도</button></p>}
            {organizationsQuery.data?.stale && <p className="messenger-sidebar-hint">업무 시스템 연결 지연으로 마지막 조직 정보를 표시합니다.</p>}
            <OrganizationTree departments={organizationsQuery.data?.departments ?? []} users={usersQuery.data ?? organizationsQuery.data?.users ?? []} ascending={nameSortDirection === 'asc'} onOpenChat={openDirectMessage} />
          </div>
        </> : viewMode === 'chat' ? <>
          <div className="messenger-list-heading"><span>참여 중인 대화</span><span>{channels.length}</span></div>
          <div className="messenger-sidebar-scroll">
            {channels.map(channel => {
              const title = channel.name ?? channel.members.filter(id => id !== currentUserId).map(nameFor).join(', ')
              const activity = formatChannelTime(channel.lastMessageAt, now)
              return <button key={channel.id} type="button" className="messenger-room" aria-pressed={selectedChannelId === channel.id} onClick={() => { setViewMode('chat'); selectChannel(channel.id) }}>
                <Avatar name={title} group={channel.type === 'GROUP'} /><span className="messenger-room-copy"><strong>{title}</strong><small>{channel.type === 'DM' ? '1:1 대화' : `${channel.members.length}명 참여`}</small></span>
                <span className="messenger-room-meta">
                  {activity && <time className="messenger-room-time" dateTime={channel.lastMessageAt ?? undefined} title={activity.detail} aria-label={activity.detail}>{activity.label}</time>}
                  {channel.unreadCount > 0 && <span className="messenger-unread">{formatBadgeCount(channel.unreadCount)}</span>}
                </span>
              </button>
            })}
            {!channels.length && <p className="messenger-sidebar-hint">아직 참여한 대화가 없습니다.<br />조직에서 동료를 선택해 대화를 시작하세요.</p>}
          </div>
        </> : <><div className="messenger-list-heading">공지와 알림</div><p className="messenger-sidebar-hint">회사에서 전하는 소식과 나에게 도착한 업무 알림을 확인하세요.</p></>}
      </div>
      <div className="messenger-self"><button type="button" className="messenger-profile-trigger" onClick={() => setShowProfile(true)} aria-label="내 프로필 사진 설정"><Avatar name={currentUser?.nickname ?? currentUser?.userId ?? ''} imageUrl={currentUser?.profileImageUrl} /></button><div className="messenger-room-copy"><strong>{currentUser?.nickname ?? currentUser?.userId}</strong><small>{currentUser?.department ?? '부서 미지정'}</small></div><button type="button" className="messenger-logout" disabled={logoutMutation.isPending} onClick={() => logoutMutation.mutate()}>로그아웃</button></div>
      {showProfile && <ProfileSettings onClose={() => setShowProfile(false)} />}
    </aside>
  )
}
