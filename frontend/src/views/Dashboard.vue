<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AnimatedModeSwitch, { type ModeSwitchValue } from '@/components/AnimatedModeSwitch.vue'
import http, { getData } from '@/api/http'

interface TodoItem {
  id: number
  title: string
  priority?: string
  status?: string
  dueAt?: string
}

const router = useRouter()
const health = ref<string>('检查中...')
const userInfo = ref<any>(null)
const todos = ref<TodoItem[]>([])
const loading = ref(false)

const todoCount = computed(() => todos.value.length)
const todoMode = computed<ModeSwitchValue>(() => (todoCount.value > 0 ? 'todo-has' : 'todo-empty'))

async function checkHealth() {
  try {
    const r = await http.get('/health').then(getData<{ status: string; time: string }>)
    health.value = `✅ ${r.status} @ ${r.time}`
  } catch {
    health.value = '❌ 后端连不上'
  }
}

async function fetchMe() {
  try {
    const r = await http.get('/auth/me').then(getData<any>)
    userInfo.value = r
  } catch {
    /* 拦截器已处理 */
  }
}

async function fetchTodos() {
  loading.value = true
  try {
    const page = await http.get('/todos', { params: { page: 0, size: 20, status: 'pending' } })
      .then(getData<{ content: TodoItem[] }>)
    todos.value = page.content ?? []
  } catch {
    todos.value = []
  } finally {
    loading.value = false
  }
}

function goKnowledge() {
  router.push({ name: 'knowledge' })
}

onMounted(() => {
  checkHealth()
  fetchMe()
  fetchTodos()
})
</script>

<template>
  <div class="dashboard">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">工作台</h2>
      <el-button type="primary" class="ask-button" @click="goKnowledge">
        问点什么
      </el-button>
    </div>

    <AnimatedModeSwitch :mode="todoMode">
      <template #default="{ mode }">
        <el-card v-if="mode === 'todo-has'" v-loading="loading">
          <template #header>
            <span style="font-weight: 500">待办事项 ({{ todoCount }})</span>
          </template>
          <el-table :data="todos" stripe style="width: 100%">
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column prop="priority" label="优先级" width="100" />
            <el-table-column prop="dueAt" label="截止日期" width="180" />
          </el-table>
        </el-card>

        <el-card v-else v-loading="loading">
          <template #header>
            <span style="font-weight: 500">待办事项</span>
          </template>
          <el-empty description="暂无待办, 一切就绪">
            <el-button type="primary" @click="goKnowledge">去提问</el-button>
          </el-empty>
        </el-card>
      </template>
    </AnimatedModeSwitch>

    <el-card style="margin-top: 16px">
      <template #header>
        <h3 style="margin: 0">系统状态</h3>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="后端健康">
          {{ health }}
        </el-descriptions-item>
        <el-descriptions-item label="当前用户">
          {{ userInfo?.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-if="userInfo?.role" type="info">{{ userInfo.role }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
