import { create } from 'zustand'
import type { Channel, Message } from '@/features/channels/channels.api'

interface ChatState {
  selectedChannelId: number | null
  channels: Channel[]
  messages: Record<number, Message[]>
  typingUsers: Record<number, string[]>
  setChannels: (channels: Channel[]) => void
  selectChannel: (channelId: number) => void
  setMessages: (channelId: number, messages: Message[]) => void
  appendMessage: (channelId: number, message: Message) => void
  setTypingUsers: (channelId: number, users: string[]) => void
  updateUnreadCount: (channelId: number, unreadCount: number) => void
}

export const useChatStore = create<ChatState>((set) => ({
  selectedChannelId: null,
  channels: [],
  messages: {},
  typingUsers: {},
  setChannels: (channels) =>
    set((state) => ({
      channels,
      selectedChannelId: state.selectedChannelId ?? channels[0]?.id ?? null
    })),
  selectChannel: (selectedChannelId) => set({ selectedChannelId }),
  setMessages: (channelId, messages) =>
    set((state) => ({ messages: { ...state.messages, [channelId]: messages } })),
  appendMessage: (channelId, message) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [channelId]: [...(state.messages[channelId] ?? []), message]
      }
    })),
  setTypingUsers: (channelId, users) =>
    set((state) => ({
      typingUsers: { ...state.typingUsers, [channelId]: users }
    })),
  updateUnreadCount: (channelId, unreadCount) =>
    set((state) => ({
      channels: state.channels.map((channel) =>
        channel.id === channelId ? { ...channel, unreadCount } : channel
      )
    }))
}))
