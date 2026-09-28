const DEFAULT_API_ORIGIN = 'http://localhost:8082'
const DEFAULT_WS_ORIGIN = 'ws://localhost:8082'
const browserOrigin = import.meta.env.VITE_WEB_SAME_ORIGIN === 'true' ? window.location.origin : undefined

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

export const apiOrigin = trimTrailingSlash(
  window.messengerDesktop?.runtime.apiOrigin ?? browserOrigin ?? (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? DEFAULT_API_ORIGIN
)

export const apiBaseUrl = `${apiOrigin}/api/v1`

export const wsUrl = window.messengerDesktop?.runtime.wsUrl ?? `${trimTrailingSlash(
  browserOrigin?.replace(/^http/, 'ws') ?? (import.meta.env.VITE_WS_ORIGIN as string | undefined) ?? DEFAULT_WS_ORIGIN
)}/ws`
