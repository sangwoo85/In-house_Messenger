import axios from 'axios'
import { useAuthStore } from '@/stores/auth.store'

export const http = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  withCredentials: true
})

http.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})
