import { net, session } from 'electron'

export type AuthAction = 'login' | 'refresh' | 'logout'
export interface AuthRequest { action: AuthAction; body?: { emprId: string; password: string }; accessToken?: string }

/** Sends credentials from the main process and keeps the HttpOnly refresh cookie out of React. */
export async function requestAuth(apiOrigin: string, input: AuthRequest): Promise<{ status: number; data: unknown }> {
  if (!['login', 'refresh', 'logout'].includes(input.action)) throw new Error('허용되지 않은 인증 요청입니다.')
  if (input.action === 'login' && (!input.body || typeof input.body.emprId !== 'string' || typeof input.body.password !== 'string')) {
    throw new Error('로그인 정보를 입력하세요.')
  }
  try {
    return await new Promise((resolve, reject) => {
      const request = net.request({
        url: `${apiOrigin}/api/v1/auth/${input.action}`,
        method: 'POST', session: session.defaultSession, useSessionCookies: true, redirect: 'error'
      })
      const timer = setTimeout(() => { request.abort(); reject(new Error('인증 서버 응답 시간이 초과되었습니다.')) }, 15000)
      request.on('close', () => clearTimeout(timer))
      request.on('error', reject)
      request.on('response', response => {
        const chunks: Buffer[] = []
        let length = 0
        response.on('data', chunk => {
          length += chunk.length
          if (length > 1024 * 1024) { request.abort(); reject(new Error('인증 응답이 너무 큽니다.')); return }
          chunks.push(Buffer.from(chunk))
        })
        response.on('error', reject)
        response.on('end', () => {
          try {
            const text = Buffer.concat(chunks).toString('utf8')
            resolve({ status: response.statusCode, data: text ? JSON.parse(text) : null })
          } catch { reject(new Error('인증 서버 응답 형식이 올바르지 않습니다.')) }
        })
      })
      request.setHeader('Content-Type', 'application/json')
      if (input.accessToken) request.setHeader('Authorization', `Bearer ${input.accessToken}`)
      request.end(input.action === 'login' ? JSON.stringify(input.body) : '{}')
    })
  } finally {
    if (input.action === 'logout') await session.defaultSession.cookies.remove(apiOrigin, 'refreshToken')
    await session.defaultSession.cookies.flushStore()
  }
}
