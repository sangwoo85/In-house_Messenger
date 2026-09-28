import { http } from '@/services/http'

export interface Channel {
  id: number
  name: string | null
  type: 'DM' | 'GROUP'
  members: string[]
  unreadCount: number
  ownerUserId: string | null
  /** Latest persisted message creation time; empty rooms have no activity date. */
  lastMessageAt: string | null
}

export interface MessageReply {
  id: number
  senderUserId: string | null
  content: string
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'NOTICE' | 'EXTERNAL'
  deleted: boolean
}

export interface Message {
  id: number
  channelId: number
  senderUserId: string | null
  content: string
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'NOTICE' | 'EXTERNAL'
  attachment: {
    id: number
    originalName: string
    mimeType: string
    fileSize: number
    downloadUrl: string
    image: boolean
  } | null
  createdAt: string
  deleted: boolean
  updatedAt: string
  clientRequestId: string | null
  readUserIds: string[]
  unreadCount: number
  reactions: { emoji: string; userIds: string[] }[]
  deletable: boolean
  replyTo: MessageReply | null
}

export interface UploadedFile {
  id: number
  originalName: string
  mimeType: string
  fileSize: number
  downloadUrl: string
  image: boolean
}

export interface MessageSlice {
  items: Message[]
  nextCursor: number | null
  hasNext: boolean
}

export interface CreateChannelPayload {
  name: string | null
  type: 'DM' | 'GROUP'
  memberUserIds: string[]
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

/** Loads the current user’s channels, unread totals, and latest message send times. */
export async function getChannels(): Promise<Channel[]> {
  const response = await http.get<ApiResponse<Channel[]>>('/channels')
  return response.data.data
}

/** Creates a two-person chat or a named group. */
export async function createChannel(payload: CreateChannelPayload): Promise<Channel> {
  const response = await http.post<ApiResponse<Channel>>('/channels', payload)
  return response.data.data
}

/** Loads one history page with per-message receipts and reactions. */
export async function getMessages(channelId: number, cursor?: number): Promise<MessageSlice> {
  const response = await http.get<ApiResponse<MessageSlice>>(`/channels/${channelId}/messages`, { params: { cursor } })
  return response.data.data
}

/** Marks messages through this ID read without moving the server cursor backwards. */
export async function markChannelRead(channelId: number, messageId: number): Promise<number> {
  const response = await http.patch<ApiResponse<number>>(`/channels/${channelId}/read`, { messageId })
  return response.data.data
}

/** Keeps the signed-in user’s current availability up to date. */
export async function heartbeat(status: 'ONLINE' | 'OFFLINE' | 'AWAY'): Promise<void> {
  await http.post('/users/presence/heartbeat', { status })
}

export interface FileUploadProgress {
  percent: number
  uploadedBytes: number
}

/** Reports transferred file volume separately from the server's upload acknowledgement. */
export async function uploadFile(file: File, onProgress?: (progress: FileUploadProgress) => void): Promise<UploadedFile> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await http.post<ApiResponse<UploadedFile>>('/files/upload', formData, {
    onUploadProgress: ({ loaded, total }) => {
      // Multipart boundaries count toward transport bytes; scale the ratio to the displayed file size.
      const totalBytes = total ?? file.size
      const ratio = totalBytes > 0 ? Math.max(0, Math.min(1, loaded / totalBytes)) : 0
      onProgress?.({ percent: Math.floor(ratio * 100), uploadedBytes: Math.floor(file.size * ratio) })
    },
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}

/** Downloads an attachment with the current bearer token. */
export async function downloadFile(fileId: number): Promise<Blob> {
  const response = await http.get<Blob>(`/files/${fileId}`, {
    responseType: 'blob'
  })
  return response.data
}

export interface SendMessagePayload { channelId: number; content: string; type: 'TEXT' | 'FILE' | 'IMAGE'; fileId?: number | null; clientRequestId: string; replyToMessageId?: number | null }
/** Waits for server acknowledgement before the composer clears its draft. */
export async function sendMessage(payload: SendMessagePayload): Promise<Message> {
  const response = await http.post<ApiResponse<Message>>(`/channels/${payload.channelId}/messages`, payload)
  return response.data.data
}
/** Requests sender-only deletion; the server rejects messages another user has read. */
export async function deleteMessage(id: number): Promise<Message> {
  return (await http.delete<ApiResponse<Message>>(`/messages/${id}`)).data.data
}
/** Adds company directory users to a group managed by the current owner. */
export async function inviteMembers(channelId: number, memberUserIds: string[]): Promise<Channel> {
  return (await http.post<ApiResponse<Channel>>(`/channels/${channelId}/members`, { memberUserIds })).data.data
}
/** Leaves a group or removes a member when the current user owns it. */
export async function removeMember(channelId: number, userId: string): Promise<void> {
  await http.delete(`/channels/${channelId}/members/${encodeURIComponent(userId)}`)
}

/** Selects one reaction; the server atomically replaces any previous choice by this user. */
export async function reactToMessage(id: number, emoji: string): Promise<Message> {
  return (await http.put<ApiResponse<Message>>(`/messages/${id}/reaction`, { emoji })).data.data
}

/** Clears the current user's reaction while preserving their read receipt. */
export async function removeMessageReaction(id: number): Promise<Message> {
  return (await http.delete<ApiResponse<Message>>(`/messages/${id}/reaction`)).data.data
}
