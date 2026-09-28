import axios, { type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth.store'
import { apiBaseUrl } from './runtime'
import { authRequest } from './authTransport'
import type { LoginResult } from '@/features/auth/auth.api'

type RequestConfig = InternalAxiosRequestConfig & { _retry?: boolean; _epoch?: number }
export const http = axios.create({ baseURL: apiBaseUrl, withCredentials: false, timeout: 20000 })
let refreshPromise: Promise<string | null> | null = null
let refreshingEpoch = -1

http.interceptors.request.use(config => {
  const state = useAuthStore.getState()
  const request = config as RequestConfig
  request._epoch ??= state.epoch
  if (request._epoch !== state.epoch) throw new axios.CanceledError('세션이 변경되었습니다.')
  if (state.accessToken) request.headers.Authorization = `Bearer ${state.accessToken}`
  return request
})

http.interceptors.response.use(response => {
  if ((response.config as RequestConfig)._epoch !== useAuthStore.getState().epoch) throw new axios.CanceledError('세션이 변경되었습니다.')
  return response
}, async error => {
  const request = error.config as RequestConfig | undefined
  const state = useAuthStore.getState()
  if (error.response?.status !== 401 || !request || request._retry || !state.accessToken || request._epoch !== state.epoch) throw error
  request._retry = true
  const epoch = state.epoch
  if (!refreshPromise || refreshingEpoch !== epoch) {
    refreshingEpoch = epoch
    const promise = authRequest<LoginResult>('refresh').then(session => {
      if (useAuthStore.getState().epoch !== epoch) return null
      useAuthStore.getState().setSession(session.accessToken, session.user)
      return session.accessToken
    }).catch(refreshError => {
      if (useAuthStore.getState().epoch === epoch && refreshError.response?.status === 401) useAuthStore.getState().clearSession()
      return null
    }).finally(() => { if (refreshPromise === promise) refreshPromise = null })
    refreshPromise = promise
  }
  const token = await refreshPromise
  if (!token || useAuthStore.getState().epoch !== epoch) throw error
  request.headers.Authorization = `Bearer ${token}`
  return http(request)
})
