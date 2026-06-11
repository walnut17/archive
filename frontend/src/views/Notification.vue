<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { useNotificationStore } from '@/store/notification'
import { listNotifications, type NotificationItem } from '@/api/notification'

const router = useRouter()
const store = useNotificationStore()
const loading = ref(false)
const list = ref<NotificationItem[]>([])
const total = ref(0)
const page = ref(1)

async function fetch() {
  loading.value = true
  try {
    const res = await listNotifications({ page: page.value - 1, size: 20 })
    list.value = res.content || []
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}

async function onMarkRead(row: NotificationItem) {
  await store.markRead(row.id)
  fetch()
}

async function onMarkAll() {
  await store.markAllRead()
  fetch()
}

function goLink(row: NotificationItem) {
  if (row.link?.startsWith('/')) router.push(row.link)
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}

onMounted(fetch)
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">通知中心</h2>
      <el-button @click="onMarkAll">全部标为已读</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="title" label="标题" min-width="200">
        <template #default="{ row }">
          <el-badge is-dot :hidden="row.read">
            <span :style="{ fontWeight: row.read ? 'normal' : 'bold' }">{{ row.title }}</span>
          </el-badge>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.link" link type="primary" @click="goLink(row)">跳转</el-button>
          <el-button v-if="!row.read" link @click="onMarkRead(row)">标为已读</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px"
      v-model:current-page="page"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetch"
    />
  </div>
</template>
