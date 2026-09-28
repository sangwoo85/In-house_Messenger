import { http } from '@/services/http'
export interface Schedule { id: number; channelId: number; creator: string; title: string; startAt: number; endAt: number; location: string | null; audience: 'ROOM' | 'SELF'; reminderMinutes: number; revision: number }
export interface ScheduleInput { title: string; startAt: number; endAt: number; hasLocation: boolean; location: string | null; audience: 'ROOM' | 'SELF'; reminderMinutes: number; revision?: number }
export interface Reminder { id: number; dueAt: number; schedule: Schedule }
export const getSchedules = async (channel: number): Promise<Schedule[]> => (await http.get(`/channels/${channel}/schedules`)).data.data
export const getRooms = async (): Promise<string[]> => (await http.get('/schedule-rooms')).data.data
export const saveSchedule = async (channel: number, input: ScheduleInput, id?: number): Promise<Schedule> => (await (id ? http.put(`/channels/${channel}/schedules/${id}`, input) : http.post(`/channels/${channel}/schedules`, input))).data.data
export const cancelSchedule = async (schedule: Schedule): Promise<void> => { await http.delete(`/channels/${schedule.channelId}/schedules/${schedule.id}`, { params: { revision: schedule.revision } }) }
export const getReminders = async (): Promise<Reminder[]> => (await http.get('/schedule-reminders')).data.data
export const actOnReminder = async (id: number, action: 'ack' | 'snooze'): Promise<void> => { await http.post(`/schedule-reminders/${id}/${action}`) }
export const scheduleTime = (time: number): string => new Date(time).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit' })
