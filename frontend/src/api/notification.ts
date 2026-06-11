import http, { getData } from './http'
import type { PageResponse } from './archive'

export interface NotificationItem {
  id: number
  userId: number
  type: 'TODO' | 'PROPOSAL' | 'FACT' | 'SYSTEM'
  title: string
  content?: string
  link?: string
  read: boolean
  createdAt: string
}

function unwrapPage<T>(body: any): PageResponse<T> {
  const page = body?.data ?? body
  return {
    content: page.content ?? [],
    page: page.number ?? page.page ?? 0,
    size: page.size ?? 20,
    totalElements: page.totalElements ?? 0,
    totalPages: page.totalPages ?? 0,
    first: page.first ?? true,
    last: page.last ?? true,
  }
}

export async function listNotifications(params: {
  unread?: boolean
  page?: number
  size?: number
}): Promise<PageResponse<NotificationItem>> {
  const res = await http.get<any>('/notifications', { params })
  return unwrapPage<NotificationItem>(res.data)
}

export async function listUnread(): Promise<PageResponse<NotificationItem>> {
  return listNotifications({ unread: true, size: 50 })
}

export async function markRead(id: number): Promise<void> {
  await http.patch(`/notifications/${id}/read`)
}

export async function markAllRead(): Promise<void> {
  await http.post('/notifications/mark-all-read')
}
