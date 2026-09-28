import { authRequest } from '@/services/authTransport'
import { useAuthStore } from '@/stores/auth.store'

export interface LoginPayload {
  emprId: string
  password: string
}

export interface AuthUser {
  id: number
  userId: string
  nickname: string
  profileImageUrl: string | null
  departmentId?: string | null
  department: string | null
  userGroup: string | null
  status: 'ONLINE' | 'OFFLINE' | 'AWAY'
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
  user: AuthUser
}

/** 업무 시스템 사번과 비밀번호를 Electron 인증 경로로 전달한다. */
export async function login(payload: LoginPayload): Promise<LoginResult> {
  return authRequest<LoginResult>('login', payload)
}

/** HttpOnly Refresh Token으로 세션을 복구한다. */
export async function refreshSession(): Promise<LoginResult> {
  return authRequest<LoginResult>('refresh')
}

/** 서버 로그아웃 실패 시에도 이 장치의 세션은 제거한다. */
export async function logout(): Promise<void> {
  try { await authRequest<null>('logout', undefined, useAuthStore.getState().accessToken ?? undefined) }
  finally { useAuthStore.getState().clearSession() }
}
