import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createChannel, inviteMembers, removeMember, type Channel } from './channels.api'
import { getUsers } from '@/features/users/users.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'

export function GroupChannelDialog({ channel, onClose }: { channel?: Channel; onClose: () => void }): JSX.Element {
  const queryClient = useQueryClient()
  const userId = useAuthStore(state => state.user?.userId)
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers })
  const owner = channel?.ownerUserId === userId
  const mutation = useMutation({
    mutationFn: async (action: { kind: 'save' | 'remove'; target?: string }) => {
      if (action.kind === 'remove' && channel && action.target) {
        await removeMember(channel.id, action.target)
        return null
      }
      return channel ? inviteMembers(channel.id, selected) : createChannel({ name: name.trim(), type: 'GROUP', memberUserIds: selected })
    },
    onSuccess: async (result, action) => {
      if (result) {
        useChatStore.getState().upsertChannel(result)
        useChatStore.getState().selectChannel(result.id)
        useUiStore.getState().setViewMode('chat')
      }
      setSelected([])
      await queryClient.invalidateQueries({ queryKey: ['channels'] })
      if (!channel || action.target === userId) onClose()
    }
  })
  const candidates = users.data?.filter(user => !channel?.members.includes(user.userId)) ?? []
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-6" role="presentation">
    <section role="dialog" aria-modal="true" aria-label={channel ? '그룹 멤버 관리' : '새 그룹 대화'} className="flex max-h-[85vh] w-full max-w-lg flex-col rounded-3xl bg-white p-6 text-slate-900 shadow-xl">
      <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">{channel ? `${channel.name} · 멤버 관리` : '새 그룹 대화'}</h2>
        <button type="button" aria-label="닫기" onClick={onClose} className="rounded-lg px-3 py-2">닫기</button></div>
      {!channel && <input aria-label="그룹 이름" maxLength={100} placeholder="그룹 이름" value={name} onChange={event => setName(event.target.value)} className="mt-4 rounded-xl border p-3" />}
      <div className="mt-4 min-h-0 flex-1 overflow-y-auto">
        {channel && <div className="mb-4 space-y-2">{channel.members.map(id => <div key={id} className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2">
          <span className="text-sm">{users.data?.find(user => user.userId === id)?.nickname ?? id} {id === channel.ownerUserId ? '(방장)' : ''}</span>
          {owner && id !== userId && <button type="button" disabled={mutation.isPending} onClick={() => mutation.mutate({ kind: 'remove', target: id })} className="text-xs text-red-600">내보내기</button>}
        </div>)}</div>}
        {(!channel || owner) && <><p className="mb-2 text-sm text-slate-500">{channel ? '초대할 사용자' : '참여할 사용자'}</p>
          {users.isLoading && <p className="text-sm">사용자를 불러오는 중...</p>}
          {users.isError && <button onClick={() => void users.refetch()} className="text-sm text-red-600">사용자 조회 실패 · 다시 시도</button>}
          {candidates.map(user => <label key={user.userId} className="flex cursor-pointer items-center gap-3 rounded-xl p-3 hover:bg-slate-50">
            <input type="checkbox" checked={selected.includes(user.userId)} onChange={event => setSelected(current => event.target.checked ? [...current, user.userId] : current.filter(id => id !== user.userId))} />
            <span className="text-sm">{user.nickname} <span className="text-xs text-slate-500">{user.department} · {user.userId}</span></span>
          </label>)}</>}
      </div>
      {mutation.isError && <p role="alert" className="mt-3 text-sm text-red-600">요청을 처리하지 못했습니다. 권한과 사용자 정보를 확인해 주세요.</p>}
      <div className="mt-5 flex items-center justify-between gap-3">
        {channel ? <button type="button" disabled={mutation.isPending} onClick={() => mutation.mutate({ kind: 'remove', target: userId })} className="rounded-xl border border-red-200 px-4 py-3 text-sm text-red-600">그룹 나가기</button> : <span />}
        {(!channel || owner) && <button type="button" disabled={mutation.isPending || selected.length === 0 || (!channel && !name.trim())} onClick={() => mutation.mutate({ kind: 'save' })} className="rounded-xl bg-primary px-5 py-3 text-sm text-white disabled:opacity-40">{mutation.isPending ? '처리 중...' : channel ? '초대' : '그룹 만들기'}</button>}
      </div>
      {channel && owner && <p className="mt-3 text-xs text-slate-500">방장이 나가면 가장 먼저 참여한 남은 멤버에게 방장 권한이 넘어갑니다.</p>}
    </section>
  </div>
}
