import { http } from '@/services/http'
import { apiOrigin } from '@/services/runtime'
import type { AuthUser } from '@/features/auth/auth.api'

/** 본인 사진만 업로드한다. 서버는 실제 이미지 형식과 크기를 다시 검증한다. */
export async function uploadProfileImage(file: File): Promise<AuthUser> {
  const body = new FormData()
  body.append('file', file)
  return (await http.put<{ data: AuthUser }>('/users/me/profile-image', body)).data.data
}

/** 본인 사진을 지우고 기본 아바타로 되돌린다. */
export async function removeProfileImage(): Promise<AuthUser> {
  return (await http.delete<{ data: AuthUser }>('/users/me/profile-image')).data.data
}

/** 사진 요청은 메신저의 인증된 사진 API로만 보낸다. 외부 이미지 서버로 토큰을 보내지 않는다. */
export function profileImagePath(value: string | null | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value, apiOrigin)
    if (url.origin !== apiOrigin || !/^\/api\/v1\/users\/[^/]+\/profile-image$/.test(url.pathname)) return null
    return url.pathname.slice('/api/v1'.length) + url.search
  } catch {
    return null
  }
}

/** HttpOnly 쿠키 대신 공통 클라이언트의 Bearer 인증으로 사진 바이트를 가져온다. */
export async function getProfileImage(path: string): Promise<Blob> {
  return (await http.get<Blob>(path, { responseType: 'blob' })).data
}
