import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshSession, type LoginResult } from '@/features/auth/auth.api'
import { AuthPage } from '@/features/auth/AuthPage'
import { ChatShell } from '@/features/chat/ChatShell'
import { useAuthStore } from '@/stores/auth.store'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { desktop } from '@/services/desktop'

let initialSessionRequest: Promise<LoginResult> | null = null

function restoreInitialSession(): Promise<LoginResult> {
  initialSessionRequest ??= refreshSession()
  return initialSessionRequest
}

export function App(): JSX.Element {
  const accessToken = useAuthStore((state) => state.accessToken)
  const setSession = useAuthStore((state) => state.setSession)
  const clearSession = useAuthStore((state) => state.clearSession)
  const resetChat = useChatStore((state) => state.reset)
  const setViewMode = useUiStore((state) => state.setViewMode)
  const queryClient = useQueryClient()
  const previousAccessToken = useRef<string | null>(null)
  const [isRestoringSession, setIsRestoringSession] = useState(true)

  useEffect(() => {
    let cancelled = false
    window.localStorage.removeItem('messenger-auth')

    void restoreInitialSession()
      .then((data) => {
        if (cancelled) {
          return
        }

        setSession(data.accessToken, data.user)
      })
      .catch(() => {
        if (cancelled) {
          return
        }

        clearSession()
      })
      .finally(() => {
        if (!cancelled) {
          setIsRestoringSession(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [clearSession, setSession])

  useEffect(() => {
    if (previousAccessToken.current && !accessToken) {
      resetChat()
      queryClient.clear()
      setViewMode('users')
      void desktop.setBadge(0)
    }
    previousAccessToken.current = accessToken
  }, [accessToken, queryClient, resetChat, setViewMode])

  if (isRestoringSession) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top,_#dbeafe,_#eff6ff_45%,_#dbe4f0)] px-6">
        <div className="w-full max-w-xl rounded-[32px] bg-white px-10 py-14 text-center shadow-[0_30px_80px_rgba(30,42,59,0.18)]">
          <p className="text-xs uppercase tracking-[0.35em] text-blue-500">Internal Messenger</p>
          <h1 className="mt-4 text-3xl font-semibold text-slate-900">세션을 복구하는 중</h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">저장된 로그인 상태를 확인하고 있습니다.</p>
        </div>
      </main>
    )
  }

  return accessToken ? <ChatShell /> : <AuthPage />
}
