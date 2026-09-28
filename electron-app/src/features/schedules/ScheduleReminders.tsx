import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { desktop } from '@/services/desktop'
import { useChatStore } from '@/stores/chat.store'
import { useUiStore } from '@/stores/ui.store'
import { actOnReminder, getReminders, scheduleTime } from './schedules.api'
import './schedules.css'

/** Unacknowledged reminders survive app restarts; polling uses server time and continues when minimized. */
export function ScheduleReminders(): JSX.Element | null {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['schedule-reminders'], queryFn: getReminders, refetchInterval: 5000, refetchIntervalInBackground: true })
  const shown = useRef(new Set<string>())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const reminder = query.data?.[0]
  useEffect(() => {
    if (!reminder) return
    const key = `${reminder.id}:${reminder.dueAt}`
    if (shown.current.has(key)) return
    shown.current.add(key)
    void desktop.showNotification(`일정 알림 · ${reminder.schedule.title}`, `${scheduleTime(reminder.schedule.startAt)} ~ ${scheduleTime(reminder.schedule.endAt)}\n${reminder.schedule.location ?? '장소 없음'}`, { displayMode: 'ALWAYS_ON_TOP', notificationType: 'SCHEDULE', channelId: reminder.schedule.channelId }).catch(() => { shown.current.delete(key) })
  }, [reminder])
  if (!reminder) return null
  const act = async (action: 'ack' | 'snooze', open = false) => {
    if (busy) return
    setBusy(true); setError('')
    try {
      await actOnReminder(reminder.id, action)
      if (open) { useChatStore.getState().selectChannel(reminder.schedule.channelId); useUiStore.getState().setViewMode('chat') }
      await client.invalidateQueries({ queryKey: ['schedule-reminders'] })
    } catch { setError('알림 상태를 변경하지 못했습니다. 다시 시도해 주세요.'); void query.refetch() } finally { setBusy(false) }
  }
  return <section className="schedule-reminder" role="dialog" aria-modal="false" aria-label="일정 알림">
    <header><span className="schedule-eyebrow">MESSENGER · 일정 알림{(query.data?.length ?? 0)>1 ? ` (${query.data!.length}건)` : ''}</span><button aria-label="알림 확인 후 닫기" disabled={busy} onClick={() => void act('ack')}>×</button></header>
    <h3>{reminder.schedule.title}</h3><p>{scheduleTime(reminder.schedule.startAt)}<br />~ {scheduleTime(reminder.schedule.endAt)}</p><p>⌖ {reminder.schedule.location ?? '장소 없음'}</p>
    {error && <p role="alert" className="schedule-error">{error}</p>}
    <footer><button disabled={busy} onClick={() => void act('snooze')}>5분 뒤 다시 알림</button><button className="schedule-primary" disabled={busy} onClick={() => void act('ack', true)}>대화방 열기</button></footer>
  </section>
}
