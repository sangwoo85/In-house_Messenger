import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { createChannel } from '@/features/channels/channels.api'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { useAuthStore } from '@/stores/auth.store'
import { getUsers } from './users.api'
export function UsersPage(): JSX.Element {
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers, refetchInterval: 20000 })
  const client = useQueryClient()
  const currentUser = useAuthStore(state => state.user?.userId)
  const [search, setSearch] = useState('')
  const [department, setDepartment] = useState('')
  const mutation = useMutation({ mutationFn: (id: string) => createChannel({ type: 'DM', name: null, memberUserIds: [id] }), onSuccess: channel => {
    useChatStore.getState().upsertChannel(channel); useChatStore.getState().selectChannel(channel.id); useUiStore.getState().setViewMode('chat'); void client.invalidateQueries({ queryKey: ['channels'] })
  } })
  const departments = [...new Set(users.data?.map(user => user.department ?? '미지정'))].sort()
  const visible = users.data?.filter(user => (!department || (user.department ?? '미지정') === department) && `${user.nickname} ${user.userId} ${user.department ?? ''}`.toLowerCase().includes(search.toLowerCase().trim())) ?? []
  return <main className="messenger-directory"><header className="messenger-chat-header"><div><h2 className="messenger-page-title">함께 일하는 동료</h2><p className="messenger-page-subtitle">동료를 찾아 대화를 시작하세요 · {users.data?.length ?? 0}명</p></div><Icon name="users" /></header>
    <div className="directory-content"><div className="directory-filters"><input aria-label="동료 검색" placeholder="이름, 아이디, 부서 검색" value={search} onChange={e => setSearch(e.target.value)} /><select aria-label="부서 선택" value={department} onChange={e => setDepartment(e.target.value)}><option value="">전체 부서</option>{departments.map(item => <option key={item}>{item}</option>)}</select></div>
      {users.isLoading && <p role="status">사용자 목록을 불러오는 중…</p>}{users.isError && <button onClick={() => void users.refetch()}>목록 조회 실패 · 다시 시도</button>}
      {mutation.isError && <p role="alert" className="schedule-error">대화방을 열지 못했습니다. 다시 시도해 주세요.</p>}
      {!users.isLoading && !users.isError && !visible.length && <p className="directory-no-results">검색 결과가 없습니다.</p>}
      <div className="directory-list">{visible.map(user => <article key={user.userId} className="directory-person"><Avatar name={user.nickname} imageUrl={user.profileImageUrl} /><div className="directory-identity"><strong>{user.nickname}{user.userId === currentUser && <small>나</small>}</strong><span>{user.department ?? '부서 미지정'}{user.userGroup ? ` · ${user.userGroup}` : ''}</span></div><span className="directory-status"><i className={`messenger-presence ${user.status.toLowerCase()}`} />{user.status === 'ONLINE' ? '온라인' : user.status === 'AWAY' ? '자리 비움' : '오프라인'}</span><button className="messenger-outline-button" disabled={mutation.isPending || user.userId === currentUser} onClick={() => mutation.mutate(user.userId)}><Icon name="chat" />대화하기</button></article>)}</div>
    </div></main>
}
