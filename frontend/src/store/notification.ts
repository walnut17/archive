import { defineStore } from 'pinia'
import * as notificationApi from '@/api/notification'
import type { NotificationItem } from '@/api/notification'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    notifications: [] as NotificationItem[],
    unreadCount: 0,
    pollingTimer: null as number | null,
  }),
  actions: {
    startPolling() {
      this.fetchUnread()
      if (this.pollingTimer) return
      this.pollingTimer = window.setInterval(() => this.fetchUnread(), 30_000)
    },
    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer)
        this.pollingTimer = null
      }
    },
    async fetchUnread() {
      try {
        const res = await notificationApi.listUnread()
        this.notifications = res.content || []
        this.unreadCount = this.notifications.filter(n => !n.read).length
      } catch {
        // silent on poll failure
      }
    },
    async markRead(id: number) {
      await notificationApi.markRead(id)
      await this.fetchUnread()
    },
    async markAllRead() {
      await notificationApi.markAllRead()
      await this.fetchUnread()
    },
  },
})
