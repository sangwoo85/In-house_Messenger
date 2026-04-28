import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthUser } from '@/features/auth/auth.api'

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  hydrated: boolean
  setSession: (accessToken: string, user: AuthUser) => void
  clearSession: () => void
  setHydrated: (hydrated: boolean) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,
      hydrated: false,
      setSession: (accessToken, user) => set({ accessToken, user }),
      clearSession: () => set({ accessToken: null, user: null }),
      setHydrated: (hydrated) => set({ hydrated })
    }),
    {
      name: 'messenger-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        user: state.user
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true)
      }
    }
  )
)
