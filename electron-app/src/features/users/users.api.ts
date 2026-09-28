import { http } from '@/services/http'

export interface DirectoryUser {
  id: number
  userId: string
  nickname: string
  profileImageUrl: string | null
  departmentId?: string | null
  department: string | null
  userGroup: string | null
  status: 'ONLINE' | 'OFFLINE' | 'AWAY'
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

/** 업무 시스템 사용자 참조에 실시간 접속 상태와 현재 프로필 사진 경로를 합쳐 조회한다. */
export async function getUsers(): Promise<DirectoryUser[]> {
  const response = await http.get<ApiResponse<DirectoryUser[]>>('/users')
  return response.data.data
}
