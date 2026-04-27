import { http } from '@/services/http'

export interface NotificationItem {
  id: number
  title: string
  content: string
  linkUrl: string | null
  read: boolean
  createdAt: string
}

export interface NotificationPage {
  items: NotificationItem[]
  page: number
  size: number
  totalElements: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export async function getNotifications(): Promise<NotificationPage> {
  const response = await http.get<ApiResponse<NotificationPage>>('/notifications?page=0&size=20')
  return response.data.data
}

export async function markNotificationRead(id: number): Promise<void> {
  await http.patch(`/notifications/${id}/read`)
}

