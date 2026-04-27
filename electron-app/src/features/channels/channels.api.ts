import { http } from '@/services/http'

export interface Channel {
  id: number
  name: string | null
  type: 'DM' | 'GROUP'
  members: string[]
  unreadCount: number
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

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export async function getChannels(): Promise<Channel[]> {
  const response = await http.get<ApiResponse<Channel[]>>('/channels')
  return response.data.data
}

export async function getMessages(channelId: number): Promise<MessageSlice> {
  const response = await http.get<ApiResponse<MessageSlice>>(`/channels/${channelId}/messages`)
  return response.data.data
}

export async function markChannelRead(channelId: number, messageId: number): Promise<void> {
  await http.patch(`/channels/${channelId}/read`, { messageId })
}

export async function heartbeat(status: 'ONLINE' | 'OFFLINE' | 'AWAY'): Promise<void> {
  await http.post('/users/presence/heartbeat', { status })
}

export async function uploadFile(file: File): Promise<UploadedFile> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await http.post<ApiResponse<UploadedFile>>('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}
