import { FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { login } from './auth.api'
import { useAuthStore } from '@/stores/auth.store'

export function AuthPage(): JSX.Element {
  const setSession = useAuthStore((state) => state.setSession)
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setSession(data.accessToken, data.user)
    }
  })

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    mutation.mutate({ userId, password })
  }

  return (
    <div className="flex min-h-screen flex-1 items-center justify-center bg-[radial-gradient(circle_at_top,_#dbeafe,_#eff6ff_45%,_#dbe4f0)] px-6">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-[32px] bg-white shadow-[0_30px_80px_rgba(30,42,59,0.18)] lg:grid-cols-[1.1fr_0.9fr]">
        <section className="hidden bg-sidebar px-10 py-12 text-white lg:block">
          <p className="text-xs uppercase tracking-[0.35em] text-blue-200">Internal Messenger</p>
          <h1 className="mt-6 text-5xl font-semibold leading-tight">
            사내 대화를
            <br />
            한 화면에 정리합니다.
          </h1>
          <p className="mt-6 max-w-md text-sm leading-7 text-slate-300">
            로그인 후 채널, 공지, 외부 알림을 하나의 데스크탑 클라이언트에서 이어서 처리합니다.
          </p>
        </section>
        <section className="px-8 py-10 sm:px-12">
          <div className="mx-auto max-w-md">
            <p className="text-sm font-medium text-primary">보안 로그인</p>
            <h2 className="mt-3 text-3xl font-semibold text-slate-900">사내 계정으로 접속</h2>
            <p className="mt-3 text-sm leading-6 text-slate-500">
              인증은 메신저 백엔드가 사내 인증 서버로 프록시 호출합니다.
            </p>
            <form className="mt-10 space-y-5" onSubmit={handleSubmit}>
              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">아이디</span>
                <input
                  className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 outline-none transition focus:border-primary focus:bg-white"
                  onChange={(event) => setUserId(event.target.value)}
                  placeholder="user01"
                  value={userId}
                />
              </label>
              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">비밀번호</span>
                <input
                  className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 outline-none transition focus:border-primary focus:bg-white"
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="비밀번호"
                  type="password"
                  value={password}
                />
              </label>
              {mutation.isError ? (
                <p className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-600">
                  로그인에 실패했습니다. 계정 정보와 백엔드 실행 상태를 확인하세요.
                </p>
              ) : null}
              <button
                className="w-full rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-white transition hover:bg-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
                disabled={mutation.isPending}
                type="submit"
              >
                {mutation.isPending ? '로그인 중...' : '로그인'}
              </button>
            </form>
          </div>
        </section>
      </div>
    </div>
  )
}
