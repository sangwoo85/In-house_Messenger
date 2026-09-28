import { create } from 'zustand'
import type { Channel, Message } from '@/features/channels/channels.api'

interface ChatState {
  readingChannelId: number | null
  setReadingChannel: (id: number | null) => void
  selectedChannelId: number | null
  channels: Channel[]
  messages: Record<number, Message[]>
  typingUsers: Record<number, string[]>
  setChannels: (channels: Channel[]) => void
  upsertChannel: (channel: Channel) => void
  selectChannel: (channelId: number) => void
  setMessages: (channelId: number, messages: Message[]) => void
  appendMessage: (channelId: number, message: Message) => void
  setTypingUsers: (channelId: number, users: string[]) => void
  updateUnreadCount: (channelId: number, unreadCount: number) => void
  incrementUnreadCount: (channelId: number) => void
  reset: () => void
}

/** Keeps newer read/reaction/tombstone updates when a slower history request arrives afterward. */
function mergeMessages(current: Message[], incoming: Message[]): Message[] {
  const byId = new Map(current.map(message => [message.id, message]))
  incoming.forEach(message => {
    const previous = byId.get(message.id)
    if (!previous || message.updatedAt >= previous.updatedAt) byId.set(message.id, message)
  })
  return Array.from(byId.values()).sort((left, right) => left.id - right.id)
}

/** Server timestamps use the same ISO format; delayed responses must not move activity backwards. */
function latestSentAt(current: string | null | undefined, incoming: string | null | undefined): string | null {
  if (!current) return incoming ?? null
  return incoming && incoming > current ? incoming : current
}

/** Includes messages received before the channel list and keeps newer socket/send acknowledgements. */
function mergeChannelActivity(channel: Channel, previous: Channel | undefined, messages: Message[] = []): Channel {
  let lastMessageAt = latestSentAt(channel.lastMessageAt, previous?.lastMessageAt)
  for (const message of messages) lastMessageAt = latestSentAt(lastMessageAt, message.createdAt)
  return { ...channel, lastMessageAt }
}

/** Shares message history and active-view read state between React screens and socket callbacks. */
export const useChatStore = create<ChatState>((set) => ({
  readingChannelId: null,
  setReadingChannel: readingChannelId => set({ readingChannelId }),
  selectedChannelId: null,
  channels: [],
  messages: {},
  typingUsers: {},
  setChannels: (channels) =>
    set((state) => ({
      channels: channels.map(channel => mergeChannelActivity(channel,
        state.channels.find(previous => previous.id === channel.id), state.messages[channel.id])),
      messages: Object.fromEntries(Object.entries(state.messages).filter(([id]) => channels.some(channel => channel.id === Number(id)))),
      selectedChannelId: channels.some((channel) => channel.id === state.selectedChannelId)
        ? state.selectedChannelId
        : channels[0]?.id ?? null
    })),
  upsertChannel: (channel) =>
    set((state) => {
      const existingIndex = state.channels.findIndex((item) => item.id === channel.id)
      if (existingIndex === -1) {
        return { channels: [mergeChannelActivity(channel, undefined, state.messages[channel.id]), ...state.channels] }
      }

      const nextChannels = [...state.channels]
      nextChannels[existingIndex] = mergeChannelActivity(channel, state.channels[existingIndex], state.messages[channel.id])
      return { channels: nextChannels }
    }),
  selectChannel: (selectedChannelId) => set({ selectedChannelId }),
  setMessages: (channelId, messages) =>
    set(state => ({
      messages: { ...state.messages, [channelId]: mergeMessages(state.messages[channelId] ?? [], messages) },
      channels: state.channels.map(channel => channel.id === channelId ? mergeChannelActivity(channel, undefined, messages) : channel)
    })),
  appendMessage: (channelId, message) =>
    set(state => ({
      messages: { ...state.messages, [channelId]: mergeMessages(state.messages[channelId] ?? [], [message]) },
      // Both HTTP sends and socket receipts use createdAt, never read/reaction/deletion updatedAt.
      channels: state.channels.map(channel => channel.id === channelId
        ? { ...channel, lastMessageAt: latestSentAt(channel.lastMessageAt, message.createdAt) } : channel)
    })),
  setTypingUsers: (channelId, users) =>
    set((state) => ({
      typingUsers: { ...state.typingUsers, [channelId]: users }
    })),
  updateUnreadCount: (channelId, unreadCount) =>
    set((state) => ({
      channels: state.channels.map((channel) =>
        channel.id === channelId ? { ...channel, unreadCount: Math.max(0, unreadCount) } : channel
      )
    })),
  incrementUnreadCount: (channelId) =>
    set((state) => ({
      channels: state.channels.map((channel) =>
        channel.id === channelId ? { ...channel, unreadCount: channel.unreadCount + 1 } : channel
      )
    })),
  reset: () =>
    set({
      selectedChannelId: null,
      readingChannelId: null,
      channels: [],
      messages: {},
      typingUsers: {}
    })
}))
