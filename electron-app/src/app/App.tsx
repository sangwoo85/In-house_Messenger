import { AuthPage } from '@/features/auth/AuthPage'
import { ChatShell } from '@/features/chat/ChatShell'
import { useAuthStore } from '@/stores/auth.store'

export function App(): JSX.Element {
  const isAuthenticated = useAuthStore((state) => Boolean(state.accessToken))

  return isAuthenticated ? <ChatShell /> : <AuthPage />
}
