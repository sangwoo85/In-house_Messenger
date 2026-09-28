import { useEffect, useRef } from 'react'
import type { Message, MessageReply } from '@/features/channels/channels.api'

const REACTIONS = [
  { emoji: '👍', label: '좋아요' }, { emoji: '❤️', label: '하트' },
  { emoji: '😊', label: '미소' }, { emoji: '😂', label: '웃음' },
  { emoji: '😮', label: '놀람' }, { emoji: '🙏', label: '감사' }
]

export function replySummary(reply: MessageReply): string {
  if (reply.deleted) return '삭제된 메시지입니다.'
  const prefix = reply.type === 'IMAGE' ? '사진 · ' : reply.type === 'FILE' ? '파일 · ' : ''
  return prefix + reply.content
}

export function ReplyQuote({ reply, sender, onClick }: {
  reply: MessageReply; sender: string; onClick: () => void
}): JSX.Element {
  return <button type="button" className="messenger-reply-quote" onClick={onClick}
    aria-label={`${sender}님의 원문으로 이동`} title="원문으로 이동">
    <span className="messenger-reply-author">↩ {sender}님에게 답장</span>
    <span className="messenger-reply-summary">{replySummary(reply)}</span>
  </button>
}

export function MessageReactions({ message, userId, displayName, pending, open, onOpenChange, onReact, onReply }: {
  message: Message; userId: string; displayName: (id: string | null) => string
  pending: boolean; open: boolean; onOpenChange: (open: boolean) => void
  onReact: (emoji: string) => void; onReply: () => void
}): JSX.Element {
  const pickerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const closeRef = useRef(onOpenChange)
  closeRef.current = onOpenChange

  useEffect(() => {
    if (!open) return
    pickerRef.current?.querySelector<HTMLButtonElement>('.messenger-reaction-picker button')?.focus()
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !pickerRef.current?.contains(event.target)) closeRef.current(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      closeRef.current(false)
      triggerRef.current?.focus()
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  return <div className="messenger-reactions">
    {(message.reactions ?? []).map(reaction => {
      const selected = reaction.userIds.includes(userId)
      return <button key={reaction.emoji} type="button" disabled={pending}
        aria-pressed={selected} aria-label={`${reaction.emoji} 반응 ${reaction.userIds.length}명`}
        className={`messenger-reaction ${selected ? 'is-selected' : ''}`}
        title={`${reaction.userIds.map(displayName).join(', ')}${selected ? ' · 클릭하여 취소' : ''}`}
        onClick={() => onReact(reaction.emoji)}>
        <span>{reaction.emoji}</span><span>{reaction.userIds.length}</span>
      </button>
    })}
    <div className="messenger-reaction-control" ref={pickerRef}>
      <button ref={triggerRef} type="button" className="messenger-message-action" disabled={pending}
        aria-label={`메시지 ${message.id} 반응 선택`} aria-expanded={open}
        aria-controls={open ? `reactions-${message.id}` : undefined} onClick={() => onOpenChange(!open)}>
        <span aria-hidden="true">☺</span> 공감
      </button>
      {open && <div className="messenger-reaction-picker" id={`reactions-${message.id}`} aria-label="메시지 반응" role="group">
        {REACTIONS.map(({ emoji, label }) => <button type="button" key={emoji} disabled={pending}
          aria-label={`${emoji} 선택`} title={label}
          aria-pressed={message.reactions?.some(reaction => reaction.emoji === emoji && reaction.userIds.includes(userId)) ?? false}
          onClick={() => { onReact(emoji); triggerRef.current?.focus() }}>{emoji}</button>)}
        <button type="button" aria-label="반응 선택 닫기" onClick={() => { onOpenChange(false); triggerRef.current?.focus() }}>×</button>
      </div>}
    </div>
    <button type="button" className="messenger-message-action" aria-label={`메시지 ${message.id} 답장`} onClick={onReply}>
      <span aria-hidden="true">↩</span> 답장
    </button>
  </div>
}
