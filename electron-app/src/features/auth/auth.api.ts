import { http } from '@/services/http'

export interface LoginPayload {
  userId: string
  password: string
}

export interface AuthUser {
  id: number
  userId: string
  nickname: string
  profileImageUrl: string | null
  status: 'ONLINE' | 'OFFLINE' | 'AWAY'
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
  user: AuthUser
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const response = await http.post<ApiResponse<LoginResult>>('/auth/login', payload)
  return response.data.data
}

export async function refreshSession(): Promise<LoginResult> {
  const response = await http.post<ApiResponse<LoginResult>>('/auth/refresh')
  return response.data.data
}
