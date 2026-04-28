import { DragEvent, KeyboardEvent, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getMessages, markChannelRead, uploadFile } from '@/features/channels/channels.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { socketService } from '@/socket/socketService'
import { resolveAssetUrl } from '@/services/runtime'

const EMPTY_TYPING_USERS: string[] = []

export function ChatLayout(): JSX.Element {
  const user = useAuthStore((state) => state.user)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const channels = useChatStore((state) => state.channels)
  const messagesByChannel = useChatStore((state) => state.messages)
  const setMessages = useChatStore((state) => state.setMessages)
  const typingUsers = useChatStore((state) => state.typingUsers[selectedChannelId ?? -1] ?? EMPTY_TYPING_USERS)
  const [content, setContent] = useState('')
  const [isDragOver, setIsDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)

  const selectedChannel = channels.find((channel) => channel.id === selectedChannelId) ?? null

  const messagesQuery = useQuery({
    queryKey: ['messages', selectedChannelId],
    queryFn: () => getMessages(selectedChannelId as number),
    enabled: Boolean(selectedChannelId)
  })

  useEffect(() => {
    if (selectedChannelId && messagesQuery.data) {
      setMessages(selectedChannelId, messagesQuery.data.items)
      const lastMessage = messagesQuery.data.items[messagesQuery.data.items.length - 1]
      if (lastMessage) {
        void markChannelRead(selectedChannelId, lastMessage.id)
      }
    }
  }, [messagesQuery.data, selectedChannelId, setMessages])

  const sendMessageMutation = useMutation({
    mutationFn: async () => {
      if (!selectedChannelId || !content.trim()) {
        return
      }

      socketService.publish('/app/chat.send', {
        channelId: selectedChannelId,
        content,
        type: 'TEXT',
        fileId: null
      })
      setContent('')
      socketService.publish('/app/chat.typing', {
        channelId: selectedChannelId,
        typing: false
      })
    }
  })

  const messages = selectedChannelId ? messagesByChannel[selectedChannelId] ?? [] : []

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      block: 'end'
    })
  }, [messages, selectedChannelId, typingUsers.length])

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => uploadFile(file),
    onSuccess: (uploaded) => {
      if (!selectedChannelId) {
        return
      }

      socketService.publish('/app/chat.send', {
        channelId: selectedChannelId,
        content: uploaded.originalName,
        type: uploaded.image ? 'IMAGE' : 'FILE',
        fileId: uploaded.id
      })
    }
  })

  const handleTyping = (nextValue: string) => {
    setContent(nextValue)
    if (!selectedChannelId) {
      return
    }

    socketService.publish('/app/chat.typing', {
      channelId: selectedChannelId,
      typing: nextValue.length > 0
    })
  }

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0 || !selectedChannelId) {
      return
    }

    Array.from(files).forEach((file) => uploadMutation.mutate(file))
  }

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setIsDragOver(false)
    handleFiles(event.dataTransfer.files)
  }

  const handleSubmitMessage = () => {
    if (!content.trim()) {
      return
    }

    sendMessageMutation.mutate()
  }

  const handleInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || event.keyCode === 229) {
      return
    }

    if (event.key !== 'Enter') {
      return
    }

    event.preventDefault()
    handleSubmitMessage()
  }

  if (!selectedChannel) {
    return (
      <main className="flex flex-1 items-center justify-center bg-[linear-gradient(180deg,_#f8fbff,_#eef4fb)] px-8">
        <div className="w-full max-w-xl rounded-[32px] bg-white px-10 py-14 text-center shadow-[0_30px_80px_rgba(30,42,59,0.12)]">
          <p className="text-xs uppercase tracking-[0.35em] text-blue-500">Chat Workspace</p>
          <h2 className="mt-4 text-3xl font-semibold text-slate-900">선택된 채널이 없습니다</h2>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            좌측 사이드바에서 채널을 선택하면 메시지와 파일, 타이핑 상태가 이 영역에 표시됩니다.
          </p>
        </div>
      </main>
    )
  }

  return (
    <main
      className={[
        'flex min-h-0 flex-1 flex-col overflow-hidden transition',
        isDragOver ? 'bg-blue-50/80' : ''
      ].join(' ')}
      onDragOver={(event) => {
        event.preventDefault()
        setIsDragOver(true)
      }}
      onDragLeave={() => setIsDragOver(false)}
      onDrop={handleDrop}
    >
      <header className="shrink-0 border-b border-slate-200 bg-white px-8 py-5">
        <h2 className="text-xl font-semibold">
          {selectedChannel.name ?? selectedChannel.members.join(', ')}
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          {`${selectedChannel.members.length}명 참여`}
        </p>
      </header>
      <section className="min-h-0 flex-1 overflow-y-auto px-8 py-6">
        <div className="space-y-4">
        {messagesQuery.isLoading ? <p className="text-sm text-slate-500">메시지를 불러오는 중...</p> : null}
        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex ${message.senderUserId === user?.userId ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={[
                'max-w-xl rounded-3xl px-5 py-4 shadow-sm',
                message.senderUserId === user?.userId ? 'bg-primary text-white' : 'bg-white text-slate-900'
              ].join(' ')}
            >
              <p className="text-xs opacity-70">{message.senderUserId ?? 'system'}</p>
              {message.type === 'IMAGE' && message.attachment ? (
                <div className="mt-2">
                  <img
                    alt={message.attachment.originalName}
                    className="max-h-72 rounded-2xl object-cover"
                    src={resolveAssetUrl(message.attachment.downloadUrl)}
                  />
                  <a
                    className="mt-2 block text-xs underline underline-offset-2"
                    href={resolveAssetUrl(message.attachment.downloadUrl)}
                    rel="noreferrer"
                    target="_blank"
                  >
                    {message.attachment.originalName}
                  </a>
                </div>
              ) : message.type === 'FILE' && message.attachment ? (
                <a
                  className="mt-2 block text-sm underline underline-offset-2"
                  href={resolveAssetUrl(message.attachment.downloadUrl)}
                  rel="noreferrer"
                  target="_blank"
                >
                  {message.attachment.originalName}
                </a>
              ) : (
                <p className="mt-2 text-sm leading-6">{message.content}</p>
              )}
            </div>
          </div>
        ))}
        {typingUsers.length > 0 ? (
          <p className="text-xs text-slate-500">{typingUsers.join(', ')} 님이 입력 중입니다.</p>
        ) : null}
        {uploadMutation.isPending ? <p className="text-xs text-slate-500">파일 업로드 중...</p> : null}
          <div ref={messagesEndRef} />
        </div>
      </section>
      <footer className="shrink-0 border-t border-slate-200 bg-white px-8 py-5">
        <div className="flex items-center gap-3 rounded-3xl border border-slate-200 bg-slate-50 px-4 py-3">
          <input
            hidden
            multiple
            onChange={(event) => handleFiles(event.target.files)}
            ref={fileInputRef}
            type="file"
          />
          <button
            className="rounded-full bg-slate-200 px-3 py-2 text-sm font-medium"
            onClick={() => fileInputRef.current?.click()}
            type="button"
          >
            파일
          </button>
          <input
            className="flex-1 bg-transparent text-sm outline-none"
            onChange={(event) => handleTyping(event.target.value)}
            onKeyDown={handleInputKeyDown}
            placeholder="메시지를 입력하세요"
            value={content}
          />
          <button
            className="rounded-full bg-primary px-4 py-2 text-sm font-medium text-white"
            onClick={handleSubmitMessage}
            type="button"
          >
            전송
          </button>
        </div>
      </footer>
    </main>
  )
}
