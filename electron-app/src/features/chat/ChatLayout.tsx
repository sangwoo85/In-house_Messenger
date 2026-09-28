import { useCallback, useMemo } from 'react'
import { isAxiosError } from 'axios'
import { Icon } from '@/components/Icon'
import { getUsers } from '@/features/users/users.api'
import { DragEvent, KeyboardEvent, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getMessages, markChannelRead, uploadFile, sendMessage, deleteMessage, reactToMessage, removeMessageReaction, type Message, type MessageReply, type UploadedFile } from '@/features/channels/channels.api'
import { GroupChannelDialog } from '@/features/channels/GroupChannelDialog'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { socketService } from '@/socket/socketService'
import { replySummary } from './MessageInteractions'
import { ChatMessages } from './ChatMessages'
import { FileUploadProgress, type FileTransferStatus } from './FileUploadProgress'
import './message-interactions.css'
import { SchedulePanel, ScheduleStrip } from '@/features/schedules/SchedulePanel'
const EMPTY_MESSAGES: Message[] = []
const EMPTY_TYPING_USERS: string[] = []
type Draft = { content: string; requestId: string; replyTo?: MessageReply | null }
type PendingFile = { id: string; channelId: number; file: File; uploaded?: UploadedFile; status: FileTransferStatus; percent: number; uploadedBytes: number; error?: string; replyTo?: MessageReply | null; draftRequestId?: string }

function quoteMessage(message: Message): MessageReply {
  return { id: message.id, senderUserId: message.senderUserId, content: message.deleted ? '' : message.attachment?.originalName ?? message.content, type: message.type, deleted: message.deleted }
}

function sendError(error: unknown): string {
  return isAxiosError(error) && error.response?.data?.code === 'MESSAGE_008'
    ? '답장할 원문이 삭제되었거나 더 이상 사용할 수 없습니다. 답장을 취소한 뒤 다시 전송해 주세요.'
    : '메시지를 저장하지 못했습니다. 입력을 유지했으니 다시 전송해 주세요.'
}

/** Coordinates history, drafts, uploads, receipts, and reactions for the selected channel. */
export function ChatLayout(): JSX.Element {
  const directory = useQuery({ queryKey: ['users'], queryFn: getUsers, staleTime: 20000 })
  const currentUser = useAuthStore(state => state.user)
  const peopleById = useMemo(() => {
    const people = new Map(directory.data?.map(user => [user.userId, user]))
    if (currentUser) people.set(currentUser.userId, currentUser)
    return people
  }, [directory.data, currentUser])
  const person = useCallback((id: string | null) => id ? peopleById.get(id) : undefined, [peopleById])
  const displayName = useCallback((id: string | null) => person(id)?.nickname ?? id ?? '시스템', [person])
  const userId = useAuthStore(state => state.user?.userId)
  const selectedChannelId = useChatStore(state => state.selectedChannelId)
  const channels = useChatStore(state => state.channels)
  const selectedChannel = channels.find(channel => channel.id === selectedChannelId)
  const messages = useChatStore(state => state.messages[selectedChannelId ?? -1] ?? EMPTY_MESSAGES)
  const typingUsers = useChatStore(state => state.typingUsers[selectedChannelId ?? -1] ?? EMPTY_TYPING_USERS)
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [active, setActive] = useState(document.hasFocus() && document.visibilityState === 'visible')
  const [atBottom, setAtBottom] = useState(true)
  const [isDragOver, setIsDragOver] = useState(false)
  const [manageGroup, setManageGroup] = useState(false)
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const [mutationPending, setMutationPending] = useState(false)
  const [reactionPicker, setReactionPicker] = useState<number | null>(null)
  const [files, setFiles] = useState<PendingFile[]>([])
  const [jumpTarget, setJumpTarget] = useState<{ channelId: number; id: number } | null>(null)
  const [highlightedId, setHighlightedId] = useState<number | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const preservedScroll = useRef<{ height: number; top: number; firstId?: number } | null>(null)
  const typingTimer = useRef<number | null>(null)
  const lastTyping = useRef(0)
  const draft = selectedChannelId ? drafts[selectedChannelId] : undefined
  const content = draft?.content ?? ''
  const messagesById = useMemo(() => new Map(messages.map(message => [message.id, message])), [messages])
  const resolveReply = useCallback((reply: MessageReply) => {
    // A fresh quote's tombstone must win over an original cached before reconnecting.
    if (reply.deleted) return reply
    const original = messagesById.get(reply.id)
    return original ? quoteMessage(original) : reply
  }, [messagesById])
  const replyTarget = draft?.replyTo ? resolveReply(draft.replyTo) : null
  const query = useInfiniteQuery({
    queryKey: ['messages', selectedChannelId],
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) => getMessages(selectedChannelId!, pageParam),
    getNextPageParam: page => page.hasNext ? page.nextCursor ?? undefined : undefined,
    enabled: selectedChannelId !== null
  })

  useEffect(() => {
    if (selectedChannelId && query.data) useChatStore.getState().setMessages(selectedChannelId, query.data.pages.flatMap(page => page.items))
  }, [query.data, selectedChannelId])
  useEffect(() => {
    const update = () => setActive(document.hasFocus() && document.visibilityState === 'visible')
    window.addEventListener('focus', update); window.addEventListener('blur', update); document.addEventListener('visibilitychange', update)
    return () => { window.removeEventListener('focus', update); window.removeEventListener('blur', update); document.removeEventListener('visibilitychange', update) }
  }, [])
  useEffect(() => {
    setAtBottom(true); setError(null); setManageGroup(false); setReactionPicker(null); setJumpTarget(null); setHighlightedId(null); preservedScroll.current = null
    return () => {
      window.clearTimeout(typingTimer.current ?? undefined)
      if (selectedChannelId) socketService.publish('/app/chat.typing', { channelId: selectedChannelId, typing: false })
    }
  }, [selectedChannelId])
  useEffect(() => {
    useChatStore.getState().setReadingChannel(active && atBottom ? selectedChannelId : null)
    return () => useChatStore.getState().setReadingChannel(null)
  }, [active, atBottom, selectedChannelId])
  const lastMessageId = messages[messages.length - 1]?.id
  useEffect(() => {
    if (!active || !atBottom || !selectedChannelId || !lastMessageId) return
    let cancelled = false
    void markChannelRead(selectedChannelId, lastMessageId).then(count => {
      if (!cancelled) useChatStore.getState().updateUnreadCount(selectedChannelId, count)
    }).catch(() => { if (!cancelled) setError('읽음 상태를 저장하지 못했습니다. 다시 창을 선택하면 재시도합니다.') })
    return () => { cancelled = true }
  }, [active, atBottom, selectedChannelId, lastMessageId])
  useLayoutEffect(() => {
    const panel = scrollRef.current
    if (!panel) return
    if (jumpTarget?.channelId === selectedChannelId) {
      const original = panel.querySelector<HTMLElement>(`[data-message-id="${jumpTarget.id}"]`)
      if (original) {
        original.scrollIntoView({ block: 'center' })
        original.focus({ preventScroll: true })
        setHighlightedId(jumpTarget.id)
        setJumpTarget(null)
        preservedScroll.current = null
      }
      return
    }
    const position = preservedScroll.current
    if (position && messages[0]?.id !== position.firstId) {
      panel.scrollTop = position.top + panel.scrollHeight - position.height
      preservedScroll.current = null
    } else if (atBottom) panel.scrollTop = panel.scrollHeight
  }, [messages, atBottom, jumpTarget, selectedChannelId])

  useEffect(() => {
    if (highlightedId === null) return
    const timer = window.setTimeout(() => setHighlightedId(null), 2400)
    return () => window.clearTimeout(timer)
  }, [highlightedId])

  // Keep history contiguous when following a quote beyond the pages already loaded.
  useEffect(() => {
    if (!jumpTarget || jumpTarget.channelId !== selectedChannelId || messagesById.has(jumpTarget.id) || query.isFetching) return
    if (query.data?.pages.some(page => page.items.some(message => message.id === jumpTarget.id))) return
    if (!query.hasNextPage) {
      setError('원문을 찾을 수 없습니다. 대화 내용을 새로 불러온 뒤 다시 시도해 주세요.')
      setJumpTarget(null)
      return
    }
    void query.fetchNextPage().then(result => {
      if (result.isError) {
        setJumpTarget(current => current === jumpTarget ? null : current)
        if (useChatStore.getState().selectedChannelId === jumpTarget.channelId) setError('원문을 불러오지 못했습니다. 다시 시도해 주세요.')
      }
    })
  }, [jumpTarget, selectedChannelId, messagesById, query.data, query.isFetching, query.hasNextPage, query.fetchNextPage])

  const selectReply = useCallback((message: Message | null) => {
    if (!selectedChannelId) return
    setDrafts(current => ({ ...current, [selectedChannelId]: {
      content: current[selectedChannelId]?.content ?? '', requestId: crypto.randomUUID(), replyTo: message ? quoteMessage(message) : null
    } }))
    setReactionPicker(null)
    setError(null)
    composerRef.current?.focus()
  }, [selectedChannelId])

  const jumpToMessage = useCallback((id: number) => {
    if (!selectedChannelId) return
    setError(null)
    setAtBottom(false)
    setHighlightedId(null)
    preservedScroll.current = null
    setJumpTarget({ channelId: selectedChannelId, id })
  }, [selectedChannelId])

  /** Keeps a separate retry-safe draft for each channel and emits throttled typing hints. */
  const changeContent = (value: string) => {
    if (!selectedChannelId) return
    setDrafts(current => ({ ...current, [selectedChannelId]: { ...current[selectedChannelId], content: value, requestId: crypto.randomUUID() } }))
    setError(null)
    if (Date.now() - lastTyping.current > 1500) {
      socketService.publish('/app/chat.typing', { channelId: selectedChannelId, typing: Boolean(value) })
      lastTyping.current = Date.now()
    }
    window.clearTimeout(typingTimer.current ?? undefined)
    typingTimer.current = window.setTimeout(() => socketService.publish('/app/chat.typing', { channelId: selectedChannelId, typing: false }), 2000)
  }
  /** Sends the current draft once and clears it only after a matching server acknowledgement. */
  const submit = async () => {
    if (!selectedChannelId || !draft?.content.trim() || sending || replyTarget?.deleted) return
    if (draft.content.trim().length > 4000) { setError('메시지는 4,000자까지 전송할 수 있습니다.'); return }
    const channelId = selectedChannelId
    const epoch = useAuthStore.getState().epoch
    setSending(true); setError(null)
    try {
      const message = await sendMessage({ channelId, content: draft.content.trim(), type: 'TEXT', clientRequestId: draft.requestId, replyToMessageId: draft.replyTo?.id })
      if (useAuthStore.getState().epoch !== epoch) return
      useChatStore.getState().appendMessage(channelId, message)
      setDrafts(current => current[channelId]?.requestId === draft.requestId ? { ...current, [channelId]: { content: '', requestId: crypto.randomUUID() } } : current)
      socketService.publish('/app/chat.typing', { channelId, typing: false })
      if (useChatStore.getState().selectedChannelId === channelId) { setJumpTarget(null); setAtBottom(true) }
    } catch (failure) { if (useChatStore.getState().selectedChannelId === channelId) setError(sendError(failure)) }
    finally { setSending(false) }
  }
  /** Reuses a successful upload and request ID if file-message delivery needs a retry. */
  const transmitFile = async (pending: PendingFile) => {
    let uploaded = pending.uploaded
    setFiles(current => current.map(item => item.id === pending.id ? {
      ...item, status: uploaded ? 'sending' : 'uploading', percent: uploaded ? 100 : 0,
      uploadedBytes: uploaded ? pending.file.size : 0, error: undefined
    } : item))
    try {
      uploaded ??= await uploadFile(pending.file, progress => {
        setFiles(current => current.map(item => item.id === pending.id && (item.status === 'uploading' || item.status === 'processing')
          ? { ...item, ...progress, status: progress.percent === 100 ? 'processing' : 'uploading' } : item))
      })
      const attachment = uploaded
      setFiles(current => current.map(item => item.id === pending.id
        ? { ...item, uploaded: attachment, percent: 100, uploadedBytes: pending.file.size, status: 'sending' } : item))
      const message = await sendMessage({ channelId: pending.channelId, content: uploaded.originalName, type: uploaded.image ? 'IMAGE' : 'FILE', fileId: uploaded.id, clientRequestId: pending.id, replyToMessageId: pending.replyTo?.id })
      useChatStore.getState().appendMessage(pending.channelId, message)
      setFiles(current => current.filter(item => item.id !== pending.id))
      setDrafts(current => pending.draftRequestId && current[pending.channelId]?.requestId === pending.draftRequestId
        ? { ...current, [pending.channelId]: { ...current[pending.channelId], replyTo: null, requestId: crypto.randomUUID() } } : current)
      if (useChatStore.getState().selectedChannelId === pending.channelId) { setJumpTarget(null); setAtBottom(true) }
    } catch (failure) {
      const detail = isAxiosError(failure) && failure.response?.data?.code === 'MESSAGE_008'
        ? '답장할 원문이 삭제되었습니다. 이 첨부를 취소하고 원문 선택을 해제한 뒤 다시 보내 주세요.'
        : uploaded ? '파일 업로드는 완료됐지만 메시지 전송에 실패했습니다. 재시도해 주세요.'
          : '파일 업로드에 실패했습니다. 연결 상태를 확인한 뒤 재시도해 주세요.'
      setFiles(current => current.map(item => item.id === pending.id ? { ...item, status: 'failed', error: detail } : item))
    }
  }
  /** Queues dropped or selected files for the current channel. */
  const addFiles = (list: FileList | null) => {
    if (!list || !selectedChannelId) return
    if (replyTarget?.deleted) { setError('원문이 삭제되었습니다. 답장을 취소한 뒤 파일을 전송해 주세요.'); return }
    const pending: PendingFile[] = [...list].map(file => ({ id: crypto.randomUUID(), file, channelId: selectedChannelId, status: 'uploading', percent: 0, uploadedBytes: 0, replyTo: draft?.replyTo, draftRequestId: draft?.requestId }))
    setFiles(current => [...current, ...pending])
    pending.forEach(file => void transmitFile(file))
    if (fileInputRef.current) fileInputRef.current.value = ''
  }
  /** Requests deletion and refreshes stale read state if a recipient read the message first. */
  const removeMessage = useCallback(async (message: Message) => {
    setMutationPending(true); setError(null)
    try {
      const result = await deleteMessage(message.id)
      useChatStore.getState().appendMessage(result.channelId, result)
    } catch { setError('상대방이 읽은 메시지는 삭제할 수 없습니다. 최신 상태를 확인해 주세요.'); void query.refetch() }
    finally { setMutationPending(false) }
  }, [query.refetch])
  /** Clicking the current emoji removes it; another emoji replaces the user's one selection. */
  const toggleReaction = useCallback(async (message: Message, emoji: string) => {
    if (!userId || mutationPending) return
    const selected = message.reactions?.some(reaction => reaction.emoji === emoji && reaction.userIds.includes(userId))
    setMutationPending(true); setError(null)
    try {
      const result = selected ? await removeMessageReaction(message.id) : await reactToMessage(message.id, emoji)
      useChatStore.getState().appendMessage(result.channelId, result)
      setReactionPicker(null)
    } catch { setError('반응을 저장하지 못했습니다. 다시 시도해 주세요.') }
    finally { setMutationPending(false) }
  }, [userId, mutationPending])
  /** Sends on Enter while preserving multiline input and Korean IME composition. */
  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Escape' && !event.nativeEvent.isComposing && replyTarget) { event.preventDefault(); selectReply(null); return }
    if (event.nativeEvent.isComposing || event.keyCode === 229 || event.key !== 'Enter' || event.shiftKey) return
    event.preventDefault(); void submit()
  }
  /** Preserves the scroll position while another history page is prepended. */
  const loadOlder = () => {
    const panel = scrollRef.current
    if (panel) preservedScroll.current = { height: panel.scrollHeight, top: panel.scrollTop, firstId: messages[0]?.id }
    setAtBottom(false)
    void query.fetchNextPage()
  }
  /** Adds files from a desktop drag-and-drop operation. */
  const drop = (event: DragEvent<HTMLElement>) => { event.preventDefault(); setIsDragOver(false); addFiles(event.dataTransfer.files) }
  if (!selectedChannel) return <main className="messenger-empty"><span className="messenger-empty-icon"><Icon name="chat" /></span><h2>대화를 시작해 보세요</h2><p>동료를 선택하거나 새로운 그룹 대화를 만들어 주세요.</p></main>

  return <main className={`messenger-chat ${isDragOver ? 'is-dragging' : ''}`} onDragOver={event => { event.preventDefault(); setIsDragOver(true) }} onDragLeave={() => setIsDragOver(false)} onDrop={drop}>
    <header className="messenger-chat-header"><div><h2 className="messenger-page-title">{selectedChannel.name ?? selectedChannel.members.filter(id => id !== userId).map(displayName).join(', ')}</h2><p className="messenger-page-subtitle">{selectedChannel.type === 'DM' ? '1:1 대화' : `그룹 대화 · ${selectedChannel.members.length}명 참여`}</p></div>
      <div className="schedule-header-actions"><button type="button" className="messenger-outline-button" aria-expanded={scheduleOpen} onClick={() => setScheduleOpen(!scheduleOpen)}><Icon name="calendar" />일정</button>
      {selectedChannel.type === 'GROUP' && <button type="button" onClick={() => setManageGroup(true)} className="messenger-outline-button"><Icon name="users" />멤버 관리</button>}</div>
    </header>
    {manageGroup && <GroupChannelDialog channel={selectedChannel} onClose={() => setManageGroup(false)} />}
    <ScheduleStrip channelId={selectedChannel.id} onOpen={() => setScheduleOpen(true)} />
    <div className="schedule-chat-body"><div className="schedule-chat-column">
    <div ref={scrollRef} onScroll={() => { const panel = scrollRef.current; if (panel) setAtBottom(panel.scrollHeight - panel.scrollTop - panel.clientHeight < 60) }} className="messenger-chat-history">
      {query.hasNextPage && <button type="button" disabled={query.isFetchingNextPage || !!jumpTarget} onClick={loadOlder} className="mb-5 w-full rounded-xl bg-white py-3 text-sm">{query.isFetchingNextPage ? '불러오는 중...' : '이전 메시지 더 보기'}</button>}
      {query.isLoading && <p>메시지를 불러오는 중...</p>}
      {query.isError && <button className="text-red-600" onClick={() => void query.refetch()}>메시지 조회 실패 · 다시 시도</button>}
      <ChatMessages messages={messages} userId={userId} person={person} displayName={displayName} resolveReply={resolveReply}
        highlightedId={highlightedId} mutationPending={mutationPending} reactionPicker={reactionPicker}
        setReactionPicker={setReactionPicker} jumpToMessage={jumpToMessage} toggleReaction={toggleReaction}
        selectReply={selectReply} removeMessage={removeMessage} />
      {typingUsers.length > 0 && <p className="mt-4 text-xs text-slate-500">{typingUsers.join(', ')} 님이 입력 중입니다.</p>}
    </div>
    <footer className="messenger-composer">
      {jumpTarget && <div className="messenger-jump-status" role="status">원문을 찾는 중…<button type="button" onClick={() => setJumpTarget(null)}>취소</button></div>}
      {!atBottom && <button className="mb-3 text-sm text-primary" onClick={() => { setJumpTarget(null); setAtBottom(true) }}>최신 메시지로 이동 ↓</button>}
      {error && <p role="alert" className="mb-3 text-sm text-red-600">{error}</p>}
      <div className="messenger-upload-list">
        {files.filter(file => file.channelId === selectedChannelId).map(file => <FileUploadProgress key={file.id}
          file={file.file} percent={file.percent} uploadedBytes={file.uploadedBytes} status={file.status} error={file.error}
          onRetry={() => void transmitFile(file)} onCancel={() => setFiles(current => current.filter(item => item.id !== file.id))} />)}
      </div>
      <div className="messenger-inputbox">
        {replyTarget && <div className={`messenger-reply-composer ${replyTarget.deleted ? 'is-deleted' : ''}`}>
          <div><span className="messenger-reply-author">↩ {displayName(replyTarget.senderUserId)}님에게 답장</span>
            <p className="messenger-reply-summary">{replySummary(replyTarget)}</p>
            {replyTarget.deleted && <p className="messenger-reply-unavailable" role="status">답장을 취소하면 일반 메시지로 보낼 수 있습니다.</p>}
          </div>
          <button type="button" aria-label="답장 취소" title="답장 취소 (Esc)" onClick={() => selectReply(null)}>×</button>
        </div>}
        <input ref={fileInputRef} type="file" hidden multiple onChange={event => addFiles(event.target.files)} />
        <textarea ref={composerRef} aria-label="메시지" rows={2} maxLength={4000} value={content} onChange={event => changeContent(event.target.value)} onKeyDown={keyDown} placeholder={replyTarget ? '답장을 입력하세요' : '메시지를 입력하세요'} />
        <div className="messenger-composer-tools"><button type="button" className="messenger-file-button" onClick={() => fileInputRef.current?.click()}><Icon name="plus" />파일</button><span className="messenger-compose-hint">Enter로 전송 · Shift + Enter로 줄바꿈</span><span className="messenger-char-count">{content.length.toLocaleString()} / 4,000</span><button type="button" disabled={sending || !content.trim() || replyTarget?.deleted} onClick={() => void submit()} className="messenger-send">{sending ? '전송 중...' : '전송'}<Icon name="arrow" /></button></div>
      </div>
    </footer>
    </div>{scheduleOpen && <SchedulePanel key={selectedChannel.id} channelId={selectedChannel.id} onClose={() => setScheduleOpen(false)} />}</div>
  </main>
}
