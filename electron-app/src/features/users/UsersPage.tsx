export function UsersPage(): JSX.Element {
  return (
    <main className="flex flex-1 items-center justify-center bg-[linear-gradient(180deg,_#f8fbff,_#eef4fb)] px-8">
      <div className="w-full max-w-2xl rounded-[32px] bg-white px-10 py-14 text-center shadow-[0_30px_80px_rgba(30,42,59,0.12)]">
        <p className="text-xs uppercase tracking-[0.35em] text-blue-500">User Directory</p>
        <h2 className="mt-4 text-3xl font-semibold text-slate-900">사용자 목록에서 대화를 시작하세요</h2>
        <p className="mt-3 text-sm leading-6 text-slate-500">
          좌측 사용자 목록에서 직원을 더블클릭하면 기존 1:1 채팅방으로 이동하거나 새 DM 채널을 생성합니다.
        </p>
      </div>
    </main>
  )
}
