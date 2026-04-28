const DEFAULT_API_ORIGIN = 'http://localhost:8082'
const DEFAULT_WS_ORIGIN = 'ws://localhost:8082'

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

export const apiOrigin = trimTrailingSlash(
  (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? DEFAULT_API_ORIGIN
)

export const apiBaseUrl = `${apiOrigin}/api/v1`

export const wsUrl = `${trimTrailingSlash(
  (import.meta.env.VITE_WS_ORIGIN as string | undefined) ?? DEFAULT_WS_ORIGIN
)}/ws`

export function resolveAssetUrl(path: string): string {
  if (/^https?:\/\//.test(path)) {
    return path
  }

  return `${apiOrigin}${path.startsWith('/') ? path : `/${path}`}`
}
