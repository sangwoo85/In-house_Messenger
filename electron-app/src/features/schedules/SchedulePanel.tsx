import { FormEvent, useEffect, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth.store'
import { cancelSchedule, getRooms, getSchedules, saveSchedule, scheduleTime, type Schedule } from './schedules.api'
import './schedules.css'

function failure(error: unknown): string {
  return isAxiosError(error) && typeof error.response?.data?.message === 'string' ? error.response.data.message : '일정을 저장하지 못했습니다. 연결 상태를 확인하고 다시 시도하세요.'
}
export function SchedulePanel({ channelId, onClose }: { channelId: number; onClose: () => void }): JSX.Element {
  const query = useQuery({ queryKey: ['schedules', channelId], queryFn: () => getSchedules(channelId), refetchInterval: 15000 })
  const client = useQueryClient()
  const user = useAuthStore(state => state.user?.userId)
  const [editing, setEditing] = useState<Schedule | 'new' | null>(null)
  const [canceling, setCanceling] = useState<Schedule | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [past, setPast] = useState(false)
  const refresh = () => { void client.invalidateQueries({ queryKey: ['schedules', channelId] }); void client.invalidateQueries({ queryKey: ['schedule-reminders'] }) }
  const remove = async () => {
    if (!canceling || busy) return
    setBusy(true); setError('')
    try { await cancelSchedule(canceling); setCanceling(null); refresh() } catch (error) { setError(failure(error)) } finally { setBusy(false) }
  }
  const items = (query.data ?? []).filter(item => past || item.endAt > Date.now())
  return <aside className="schedule-panel" aria-label="대화방 일정">
    <header><div><span className="schedule-eyebrow">ROOM SCHEDULE</span><h3>대화방 일정</h3></div><button aria-label="일정 패널 닫기" onClick={onClose}>×</button></header>
    <button className="schedule-primary" onClick={() => setEditing('new')}>＋ 새 일정 등록</button>
    <label className="schedule-check"><input type="checkbox" checked={past} onChange={e => setPast(e.target.checked)} />지난 일정 포함</label>
    {query.isLoading && <p role="status">일정을 불러오는 중…</p>}
    {query.isError && <button onClick={() => void query.refetch()}>일정 조회 실패 · 다시 시도</button>}
    {error && <p role="alert" className="schedule-error">{error}</p>}
    {!query.isLoading && !query.isError && !items.length && <div className="schedule-empty">예정된 일정이 없어요.<br />회의나 중요한 약속을 등록해 보세요.</div>}
    <div className="schedule-list">{items.map(item => <article key={item.id} className="schedule-card">
      <span className="schedule-tag">{item.audience === 'SELF' ? '나만 보기' : '대화방 전체'}</span><h4>{item.title}</h4>
      <p>{scheduleTime(item.startAt)}<br />~ {scheduleTime(item.endAt)}</p><p>⌖ {item.location ?? '장소 없음'}</p>
      <small>{item.reminderMinutes ? `${item.reminderMinutes}분 전 알림` : '시작 시간에 알림'}</small>
      {item.creator === user && <div className="schedule-card-actions"><button onClick={() => setEditing(item)}>수정</button><button onClick={() => setCanceling(item)}>일정 취소</button></div>}
      {canceling?.id === item.id && <div className="schedule-confirm"><p>이 일정을 취소할까요?</p><button disabled={busy} onClick={() => void remove()}>취소 확정</button><button disabled={busy} onClick={() => setCanceling(null)}>유지</button></div>}
    </article>)}</div>
    <p className="schedule-footnote">전체 알림은 등록·수정 시 참여 중인 동료에게 전달됩니다.</p>
    {editing && <ScheduleForm channelId={channelId} initial={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh() }} />}
  </aside>
}
export function ScheduleStrip({ channelId, onOpen }: { channelId: number; onOpen: () => void }): JSX.Element | null {
  const query = useQuery({ queryKey: ['schedules', channelId], queryFn: () => getSchedules(channelId), refetchInterval: 15000 })
  const next = query.data?.find(item => item.endAt > Date.now())
  return next ? <button className="schedule-strip" onClick={onOpen}><span>다가오는 일정</span><strong>{next.title}</strong><span>{scheduleTime(next.startAt)}{next.location ? ` · ${next.location}` : ''}</span><span>보기 ›</span></button> : null
}
function localDateTime(ms: number): string { const d = new Date(ms); return new Date(ms - d.getTimezoneOffset()*60000).toISOString().slice(0,16) }
function ScheduleForm({ channelId, initial, onClose, onSaved }: { channelId: number; initial?: Schedule; onClose: () => void; onSaved: () => void }): JSX.Element {
  const dialog = useRef<HTMLDialogElement>(null)
  const rooms = useQuery({ queryKey: ['schedule-rooms'], queryFn: getRooms, staleTime: 300000 })
  const [title, setTitle] = useState(initial?.title ?? '')
  const [start, setStart] = useState(localDateTime(initial?.startAt ?? Date.now()+3600000))
  const [end, setEnd] = useState(localDateTime(initial?.endAt ?? Date.now()+7200000))
  const [hasLocation, setHasLocation] = useState(Boolean(initial?.location))
  const [location, setLocation] = useState(initial?.location ?? '')
  const [minutes, setMinutes] = useState(initial?.reminderMinutes ?? 10)
  const [audience, setAudience] = useState<'ROOM' | 'SELF'>(initial?.audience ?? 'ROOM')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { const el = dialog.current; el?.showModal(); return () => el?.close() }, [])
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const startAt = new Date(start).getTime(), endAt = new Date(end).getTime()
    if (!title.trim() || !Number.isFinite(startAt) || !Number.isFinite(endAt) || startAt <= Date.now() || endAt <= startAt) { setError('미래의 시작 시간과 시작 이후의 종료 시간을 입력해 주세요.'); return }
    if (hasLocation && !location) { setError('미팅 장소를 선택해 주세요.'); return }
    if (busy) return
    setBusy(true); setError('')
    try { await saveSchedule(channelId, { title: title.trim(), startAt, endAt, hasLocation, location: hasLocation ? location : null, audience, reminderMinutes: minutes, revision: initial?.revision }, initial?.id); onSaved() }
    catch (error) { setError(failure(error)) } finally { setBusy(false) }
  }
  return <dialog ref={dialog} className="schedule-dialog" aria-labelledby="schedule-form-title" onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <form onChange={() => setError('')} onSubmit={event => void submit(event)}><header><div><span className="schedule-eyebrow">SCHEDULE</span><h2 id="schedule-form-title">{initial ? '일정 수정' : '새 일정 등록'}</h2></div><button type="button" aria-label="일정 등록 닫기" disabled={busy} onClick={onClose}>×</button></header>
      <label>일정 제목<input autoFocus required maxLength={200} placeholder="예: 서비스 기획 회의" value={title} onChange={e => setTitle(e.target.value)} /></label>
      <div className="schedule-fields"><label>시작 일시<input type="datetime-local" required value={start} onChange={e => setStart(e.target.value)} /></label><label>종료 일시<input type="datetime-local" required value={end} onChange={e => setEnd(e.target.value)} /></label></div>
      <fieldset><legend>장소 유/무</legend><label><input type="radio" name="location" checked={!hasLocation} onChange={() => { setHasLocation(false); setLocation('') }} />장소 없음</label><label><input type="radio" name="location" checked={hasLocation} onChange={() => setHasLocation(true)} />장소 있음</label></fieldset>
      {hasLocation && <label>미팅 장소<select required value={location} onChange={e => setLocation(e.target.value)}><option value="">장소를 선택하세요</option>{rooms.data?.map(room => <option key={room}>{room}</option>)}</select>{rooms.isError && <button type="button" onClick={() => void rooms.refetch()}>장소 조회 실패 · 다시 시도</button>}</label>}
      <div className="schedule-fields"><label>알림 시간<select value={minutes} onChange={e => setMinutes(Number(e.target.value))}>{[0,5,10,30,60].map(n => <option value={n} key={n}>{n ? `${n}분 전` : '시작 시간'}</option>)}</select></label><label>공개 및 알림 대상<select value={audience} onChange={e => setAudience(e.target.value as 'ROOM' | 'SELF')}><option value="ROOM">대화방 전체</option><option value="SELF">나만</option></select></label></div>
      <p className="schedule-footnote">이 기기의 시간대를 기준으로 표시합니다. 회의실 예약 시스템과는 연동되지 않습니다.</p>
      {error && <p className="schedule-error" role="alert">{error}</p>}
      <footer><button type="button" disabled={busy} onClick={onClose}>취소</button><button className="schedule-primary" disabled={busy} type="submit">{busy ? '저장 중…' : initial ? '변경 저장' : '일정 등록'}</button></footer>
    </form>
  </dialog>
}
