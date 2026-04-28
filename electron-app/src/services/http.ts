import axios from 'axios'
import { useAuthStore } from '@/stores/auth.store'
import { apiBaseUrl } from './runtime'
import type { AuthUser } from '@/features/auth/auth.api'

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

interface RefreshResult {
  accessToken: string
  expiresIn: number
  user: AuthUser
}

interface RetryableRequestConfig {
  _retry?: boolean
}

const refreshClient = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true
})

export const http = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true
})

let refreshPromise: Promise<string | null> | null = null

http.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status as number | undefined
    const originalRequest = error.config as typeof error.config & RetryableRequestConfig
    const requestUrl = String(originalRequest?.url ?? '')

    if (
      status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      requestUrl.includes('/auth/login') ||
      requestUrl.includes('/auth/refresh')
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    refreshPromise ??= refreshClient
      .post<ApiResponse<RefreshResult>>('/auth/refresh')
      .then((response) => {
        const session = response.data.data
        useAuthStore.getState().setSession(session.accessToken, session.user)
        return session.accessToken
      })
      .catch(() => {
        useAuthStore.getState().clearSession()
        return null
      })
      .finally(() => {
        refreshPromise = null
      })

    const nextAccessToken = await refreshPromise

    if (!nextAccessToken) {
      return Promise.reject(error)
    }

    originalRequest.headers.Authorization = `Bearer ${nextAccessToken}`
    return http(originalRequest)
  }
)
