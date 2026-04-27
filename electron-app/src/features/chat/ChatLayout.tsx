import { DragEvent, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getMessages, markChannelRead, uploadFile } from '@/features/channels/channels.api'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { socketService } from '@/socket/socketService'

export function ChatLayout(): JSX.Element {
  const user = useAuthStore((state) => state.user)
  const selectedChannelId = useChatStore((state) => state.selectedChannelId)
  const channels = useChatStore((state) => state.channels)
  const messagesByChannel = useChatStore((state) => state.messages)
  const setMessages = useChatStore((state) => state.setMessages)
  const typingUsers = useChatStore((state) => state.typingUsers[selectedChannelId ?? -1] ?? [])
  const [content, setContent] = useState('')
  const [isDragOver, setIsDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

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

  return (
    <main
      className={[
        'flex flex-1 flex-col transition',
        isDragOver ? 'bg-blue-50/80' : ''
      ].join(' ')}
      onDragOver={(event) => {
        event.preventDefault()
        setIsDragOver(true)
      }}
      onDragLeave={() => setIsDragOver(false)}
      onDrop={handleDrop}
    >
      <header className="border-b border-slate-200 bg-white px-8 py-5">
        <h2 className="text-xl font-semibold">
          {selectedChannel?.name ?? selectedChannel?.members.join(', ') ?? '채널 없음'}
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          {selectedChannel ? `${selectedChannel.members.length}명 참여` : '채널을 먼저 생성하세요'}
        </p>
      </header>
      <section className="flex-1 space-y-4 overflow-y-auto px-8 py-6">
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
                    src={`http://localhost:8080${message.attachment.downloadUrl}`}
                  />
                  <a
                    className="mt-2 block text-xs underline underline-offset-2"
                    href={`http://localhost:8080${message.attachment.downloadUrl}`}
                    rel="noreferrer"
                    target="_blank"
                  >
                    {message.attachment.originalName}
                  </a>
                </div>
              ) : message.type === 'FILE' && message.attachment ? (
                <a
                  className="mt-2 block text-sm underline underline-offset-2"
                  href={`http://localhost:8080${message.attachment.downloadUrl}`}
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
      </section>
      <footer className="border-t border-slate-200 bg-white px-8 py-5">
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
            placeholder="메시지를 입력하세요"
            value={content}
          />
          <button
            className="rounded-full bg-primary px-4 py-2 text-sm font-medium text-white"
            onClick={() => sendMessageMutation.mutate()}
            type="button"
          >
            전송
          </button>
        </div>
      </footer>
    </main>
  )
}
