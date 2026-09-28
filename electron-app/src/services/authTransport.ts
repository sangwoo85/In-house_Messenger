import axios from 'axios'
import { apiBaseUrl } from './runtime'

/** 데스크톱은 제한된 인증 IPC를, 테스트용 브라우저는 같은 REST 계약을 사용한다. */
export async function authRequest<T>(action: 'login' | 'refresh' | 'logout', body?: { emprId: string; password: string }, accessToken?: string): Promise<T> {
  if (window.messengerDesktop) {
    const response = await window.messengerDesktop.authRequest({ action, body, accessToken })
    if (response.status < 200 || response.status >= 300) {
      const error = new Error('인증 요청에 실패했습니다.') as Error & { response: { status: number; data: unknown } }
      error.response = response
      throw error
    }
    return (response.data as { data: T }).data
  }
  const response = await axios.post<{ data: T }>(`${apiBaseUrl}/auth/${action}`, body ?? {}, {
    withCredentials: true, timeout: 15000,
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
  })
  return response.data.data
}
