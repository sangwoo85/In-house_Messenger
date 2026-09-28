import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Avatar } from '@/components/Avatar'
import { useAuthStore } from '@/stores/auth.store'
import { removeProfileImage, uploadProfileImage } from './profile.api'

/** 프로필 사진 변경/삭제 화면. 이름과 부서는 업무 시스템에서 관리하므로 읽기 전용이다. */
export function ProfileSettings({ onClose }: { onClose: () => void }): JSX.Element {
  const user = useAuthStore(state => state.user)
  const input = useRef<HTMLInputElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const mutation = useMutation({
    mutationFn: (file: File | null) => file ? uploadProfileImage(file) : removeProfileImage(),
    onSuccess: profile => {
      const auth = useAuthStore.getState()
      if (auth.accessToken && auth.user?.userId === profile.userId) auth.setSession(auth.accessToken, profile)
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      void queryClient.invalidateQueries({ queryKey: ['organizations'] })
      setError(null)
    },
    onError: () => setError('사진을 저장하지 못했습니다. PNG/JPEG 형식과 파일 크기를 확인해 주세요.')
  })

  useEffect(() => { closeButton.current?.focus() }, [])

  /** 기본 크기/형식을 먼저 검사하며 최종 이미지 검증은 서버가 수행한다. */
  function chooseFile(file?: File): void {
    if (!file) return
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError('PNG 또는 JPEG 사진을 5MB 이하로 선택해 주세요.')
      return
    }
    setError(null)
    mutation.mutate(file)
  }

  return <div className="messenger-dialog-backdrop" onKeyDown={event => {
    if (event.key === 'Escape' && !mutation.isPending) onClose()
    if (event.key === 'Tab') {
      const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not([hidden])'))
      const first = controls[0], last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }
  }}>
    <section className="messenger-profile-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-title">
      <header><h2 id="profile-title">내 프로필</h2><button ref={closeButton} type="button" disabled={mutation.isPending} onClick={onClose}>닫기</button></header>
      <div className="messenger-profile-summary"><Avatar name={user?.nickname ?? ''} imageUrl={user?.profileImageUrl} /><div><strong>{user?.nickname}</strong><p>{user?.department ?? '부서 미지정'} · {user?.userId}</p></div></div>
      <p className="messenger-profile-help">PNG/JPEG · 최대 5MB, 4,096 × 4,096 픽셀<br />이름과 부서는 업무 시스템의 정보를 사용합니다.</p>
      <input ref={input} type="file" accept="image/png,image/jpeg" hidden onChange={event => { chooseFile(event.target.files?.[0]); event.target.value = '' }} />
      {error && <p className="messenger-profile-error" role="alert">{error}</p>}
      <div className="messenger-profile-actions">
        <button type="button" className="messenger-outline-button" disabled={mutation.isPending || !user?.profileImageUrl} onClick={() => mutation.mutate(null)}>사진 삭제</button>
        <button type="button" className="messenger-send" disabled={mutation.isPending} onClick={() => input.current?.click()}>{mutation.isPending ? '저장 중...' : '사진 변경'}</button>
      </div>
    </section>
  </div>
}
