import { create } from 'zustand'
import type { AuthUser } from '@/features/auth/auth.api'

interface AuthState {
  epoch: number
  accessToken: string | null
  user: AuthUser | null
  setSession: (accessToken: string, user: AuthUser) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  epoch: 0,
  accessToken: null,
  user: null,
  setSession: (accessToken, user) => set(state => ({ accessToken, user, epoch: state.user?.userId === user.userId ? state.epoch : state.epoch + 1 })),
  clearSession: () => set(state => ({ accessToken: null, user: null, epoch: state.epoch + 1 }))
}))
