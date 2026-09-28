import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { app } from 'electron'

export function readRuntimeConfig() {
  const configuredPath = process.env.MESSENGER_DESKTOP_CONFIG
  const file = configuredPath ?? join(app.getPath('userData'), 'messenger.properties')
  if ((configuredPath || app.isPackaged) && !existsSync(file)) throw new Error(`설정 파일을 찾을 수 없습니다: ${file}`)
  const values = new Map<string, string>()
  if (existsSync(file)) {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue
      const separator = trimmed.indexOf('=')
      if (separator < 1) throw new Error(`잘못된 설정 항목: ${trimmed}`)
      values.set(trimmed.slice(0, separator).trim(), trimmed.slice(separator + 1).trim())
    }
  }
  const api = new URL(values.get('api.origin') ?? process.env.MESSENGER_API_ORIGIN ?? 'http://localhost:8082')
  if (!['http:', 'https:'].includes(api.protocol) || api.username || api.password || api.pathname !== '/' || api.search || api.hash) {
    throw new Error('api.origin에는 HTTP/HTTPS 서버 출처만 지정해야 합니다.')
  }
  const ws = new URL(values.get('websocket.origin') ?? api.origin.replace(/^http/, 'ws'))
  if (!['ws:', 'wss:'].includes(ws.protocol) || ws.username || ws.password || ws.pathname !== '/' || ws.search || ws.hash) {
    throw new Error('websocket.origin에는 WS/WSS 서버 출처만 지정해야 합니다.')
  }
  return { apiOrigin: api.origin, wsUrl: `${ws.origin}/ws`, configPath: file }
}
