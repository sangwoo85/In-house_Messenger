import { http } from '@/services/http'
import type { NotificationDisplayMode } from '../../../electron/desktopNotifications'

export interface NotificationItem {
  id: number
  title: string
  content: string
  linkUrl: string | null
  read: boolean
  createdAt: string
  notificationType: string
  displayMode: NotificationDisplayMode
}

export interface NotificationPage {
  items: NotificationItem[]
  page: number
  size: number
  totalElements: number
  unreadCount: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

/** Retrieves all persisted delivery modes, including notifications without a popup. */
export async function getNotifications(page = 0): Promise<NotificationPage> {
  const response = await http.get<ApiResponse<NotificationPage>>(`/notifications?page=${page}&size=20`)
  return response.data.data
}

/** Updates read state only after the user opens a personal notification. */
export async function markNotificationRead(id: number): Promise<void> {
  await http.patch(`/notifications/${id}/read`)
}


export interface NoticeItem { id: number; title: string; content: string; sender: string; createdAt: string; notificationType: string; displayMode: NotificationDisplayMode }
export interface NoticePage { items: NoticeItem[]; page: number; size: number; totalElements: number }
/** Retrieves company-wide notices with the same delivery metadata as personal notifications. */
export async function getNotices(page = 0): Promise<NoticePage> {
  return (await http.get<ApiResponse<NoticePage>>(`/notices?page=${page}&size=20`)).data.data
}
