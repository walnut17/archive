<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/store/notification'

const router = useRouter()
const store = useNotificationStore()

onMounted(() => store.startPolling())
onUnmounted(() => store.stopPolling())

function goNotifications() {
  router.push('/notifications')
}
</script>

<template>
  <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
    <el-button :icon="Bell" circle @click="goNotifications" title="通知中心" />
  </el-badge>
</template>
