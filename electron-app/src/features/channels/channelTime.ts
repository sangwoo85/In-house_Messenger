/** Formats the last send time compactly while retaining the complete date for its tooltip. */
export function formatChannelTime(value: string | null | undefined, now = new Date()): { label: string; detail: string } | null {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const time = date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
  const today = date.toDateString() === now.toDateString()
  const label = today ? time : year === now.getFullYear() ? `${month}.${day}` : `${year}.${month}.${day}`
  return { label, detail: `마지막 메시지: ${year}.${month}.${day} ${time}` }
}
