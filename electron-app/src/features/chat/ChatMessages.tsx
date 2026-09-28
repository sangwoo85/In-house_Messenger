import { Fragment, memo, useEffect, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { downloadFile, type Message, type MessageReply } from '@/features/channels/channels.api'
import type { DirectoryUser } from '@/features/users/users.api'
import { MessageReactions, ReplyQuote } from './MessageInteractions'

type MessageAttachment = NonNullable<Message['attachment']>
const dateFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
const timeFormatter = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' })

/** Shows authenticated image previews and downloads without exposing a public file URL. */
function AttachmentView({ attachment, image }: { attachment: MessageAttachment; image: boolean }): JSX.Element {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [hasError, setHasError] = useState(false)

  useEffect(() => {
    if (!image) {
      return
    }

    let cancelled = false
    let objectUrl: string | null = null
    void downloadFile(attachment.id)
      .then((blob) => {
        if (cancelled) {
          return
        }
        objectUrl = URL.createObjectURL(blob)
        setPreviewUrl(objectUrl)
      })
      .catch(() => {
        if (!cancelled) {
          setHasError(true)
        }
      })

    return () => {
      cancelled = true
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [attachment.id, image])

  /** Downloads the file with its original name and releases temporary browser URLs. */
  const handleDownload = async () => {
    try {
      setHasError(false)
      const objectUrl = previewUrl ?? URL.createObjectURL(await downloadFile(attachment.id))
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = attachment.originalName
      link.click()
      if (!previewUrl) {
        window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
      }
    } catch {
      setHasError(true)
    }
  }

  return (
    <div className="messenger-attachment">
      {image && previewUrl ? (
        <img
          alt={attachment.originalName}
          className="max-h-72 rounded-2xl object-cover"
          src={previewUrl}
        />
      ) : null}
      {image && !previewUrl && !hasError ? <p className="text-xs opacity-70">이미지 불러오는 중...</p> : null}
      <button className="messenger-file" onClick={() => void handleDownload()} type="button">
        <Icon name="file" /><span>{attachment.originalName}<small>{(attachment.fileSize / 1024 / 1024).toFixed(1)} MB · 다운로드</small></span>
      </button>
      {hasError ? <p className="mt-2 text-xs text-red-600">파일을 불러오지 못했습니다.</p> : null}
    </div>
  )
}

interface ChatMessagesProps {
  messages: Message[]
  userId: string | undefined
  person: (id: string | null) => DirectoryUser | undefined
  displayName: (id: string | null) => string
  resolveReply: (reply: MessageReply) => MessageReply
  highlightedId: number | null
  mutationPending: boolean
  reactionPicker: number | null
  setReactionPicker: (id: number | null) => void
  jumpToMessage: (id: number) => void
  toggleReaction: (message: Message, emoji: string) => Promise<void>
  selectReply: (message: Message | null) => void
  removeMessage: (message: Message) => Promise<void>
}

/** Draft keystrokes and upload progress must not render the loaded conversation again. */
export const ChatMessages = memo(function ChatMessages({ messages, userId, person, displayName, resolveReply,
  highlightedId, mutationPending, reactionPicker, setReactionPicker, jumpToMessage, toggleReaction, selectReply, removeMessage
}: ChatMessagesProps): JSX.Element {
  return <div>{messages.map((message, index) => <Fragment key={message.id}>
        {(index === 0 || new Date(messages[index - 1].createdAt).toDateString() !== new Date(message.createdAt).toDateString()) && <div className="messenger-date"><span>{dateFormatter.format(new Date(message.createdAt))}</span></div>}
        <div data-message-id={message.id} tabIndex={-1}
          className={`messenger-message ${message.senderUserId === userId ? 'is-own' : ''} ${highlightedId === message.id ? 'is-highlighted' : ''}`}>
          {message.senderUserId !== userId && <Avatar name={displayName(message.senderUserId)} imageUrl={person(message.senderUserId)?.profileImageUrl} small />}
          <article className="messenger-message-body">
            {message.senderUserId !== userId && <p className="messenger-sender">{displayName(message.senderUserId)}<span>{person(message.senderUserId)?.department}</span></p>}
            <div className={`messenger-bubble ${message.deleted ? 'is-deleted' : ''}`}>
              {!message.deleted && message.replyTo && <ReplyQuote reply={resolveReply(message.replyTo)}
                sender={displayName(message.replyTo.senderUserId)} onClick={() => jumpToMessage(message.replyTo!.id)} />}
              {message.deleted ? <p>삭제된 메시지입니다.</p> : message.attachment ? <AttachmentView attachment={message.attachment} image={message.type === 'IMAGE'} /> : <p className="whitespace-pre-wrap break-words">{message.content}</p>}
            </div>
            {!message.deleted && <MessageReactions message={message} userId={userId ?? ''} displayName={displayName}
              pending={mutationPending} open={reactionPicker === message.id}
              onOpenChange={open => setReactionPicker(open ? message.id : null)}
              onReact={emoji => void toggleReaction(message, emoji)} onReply={() => selectReply(message)} />}
            <div className="messenger-message-meta"><time>{timeFormatter.format(new Date(message.createdAt))}</time>
              {!message.deleted && message.senderUserId === userId && <span className="messenger-read-receipt" title={(message.readUserIds ?? []).length ? `읽은 사람: ${message.readUserIds.map(displayName).join(', ')}` : '아직 읽은 사람이 없습니다.'}>
                {(message.unreadCount ?? 0) > 0 ? `안 읽음 ${message.unreadCount}` : (message.readUserIds ?? []).length > 0 ? '읽음' : '받는 사람 없음'}
              </span>}
              {!message.deleted && message.senderUserId === userId && message.deletable && <button disabled={mutationPending} onClick={() => void removeMessage(message)} title="상대방이 읽기 전까지 삭제할 수 있습니다.">삭제</button>}
            </div>
          </article>
        </div>
      </Fragment>)}</div>
})
