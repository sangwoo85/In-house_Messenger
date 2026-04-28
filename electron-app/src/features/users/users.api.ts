import { http } from '@/services/http'

export interface DirectoryUser {
  id: number
  userId: string
  nickname: string
  profileImageUrl: string | null
  status: 'ONLINE' | 'OFFLINE' | 'AWAY'
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export async function getUsers(): Promise<DirectoryUser[]> {
  const response = await http.get<ApiResponse<DirectoryUser[]>>('/users')
  return response.data.data
}
