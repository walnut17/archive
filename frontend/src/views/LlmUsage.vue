<script setup lang="ts">
/**
 * LLM 用量统计页面.
 *
 * - 今日/本周/本月 调用量 + token
 * - 按场景 / 按用户 聚合
 * - 最近 50 条记录
 * - admin 可切换"看全员/看自己"
 */
import { ref, onMounted, computed } from 'vue'
import http, { getData } from '@/api/http'

interface PeriodStats {
  count: number
  totalTokens: number | null
}

interface BucketRow {
  key: string
  count: number
  totalTokens: number | null
}

interface RecentCall {
  id: number
  username: string | null
  scenario: string
  durationMs: number
  status: string
  createdAt: string
}

interface LlmUsageStats {
  today: PeriodStats
  thisWeek: PeriodStats
  thisMonth: PeriodStats
  byScenario: BucketRow[]
  byUser: BucketRow[]
  recent: RecentCall[]
}

const stats = ref<LlmUsageStats | null>(null)
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const scope = ref<'all' | 'mine'>('mine')
const isAdmin = ref(false)

const scopeOptions = computed(() => {
  if (!isAdmin.value) return [{ label: '我的用量', value: 'mine' }]
  return [
    { label: '我的用量', value: 'mine' },
    { label: '全员', value: 'all' },
  ]
})

async function load() {
  loading.value = true
  try {
    const url = scope.value === 'all' && isAdmin.value
      ? '/llm/stats?recentLimit=50'
      : '/llm/my-usage?recentLimit=50'
    const resp = await http.get<any, any>(url)
    stats.value = resp.data?.data || resp.data
  } catch (e: any) {
    console.error('加载用量失败', e)
    errorMsg.value = e?.response?.data?.message || e?.message || '未知错误(可能后端 /api/llm 接口未启用,需要跑 G 迁移 + 重启后端)'
  } finally {
    loading.value = false
  }
}

function fmt(n: number | null | undefined): string {
  if (n == null) return '-'
  return n.toLocaleString()
}

function fmtMs(ms: number): string {
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(2) + ' s'
}

function statusType(status: string): string {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

onMounted(async () => {
  // 试一下能不能调 /api/llm/stats(admin 接口)判断角色
  try {
    const r = await http.get<any, any>('/llm/stats?recentLimit=1')
    if (r.data?.code === 0) {
      isAdmin.value = true
      scope.value = 'all'
    }
  } catch {
    isAdmin.value = false
    scope.value = 'mine'
  }
  await load()
})
</script>

<template>
  <div class="llm-usage">
    <el-alert
      v-if="errorMsg"
      :title="'加载失败'"
      type="error"
      :description="errorMsg"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <h2>🤖 LLM 用量统计</h2>
        <el-radio-group v-if="isAdmin" v-model="scope" @change="load">
          <el-radio-button label="all">全员</el-radio-button>
          <el-radio-button label="mine">我的</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">今日</div>
          <div class="metric-value">{{ fmt(stats?.today?.count) }} <small>次</small></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">本周</div>
          <div class="metric-value">{{ fmt(stats?.thisWeek?.count) }} <small>次</small></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">本月</div>
          <div class="metric-value">{{ fmt(stats?.thisMonth?.count) }} <small>次</small></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" v-if="scope === 'all' && isAdmin">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按场景(本月)</template>
          <el-table :data="stats?.byScenario || []" stripe>
            <el-table-column prop="key" label="场景" width="140" />
            <el-table-column prop="count" label="调用次数" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按用户(本月)</template>
          <el-table :data="stats?.byUser || []" stripe>
            <el-table-column prop="key" label="用户" width="140" />
            <el-table-column prop="count" label="调用次数" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" show-icon style="margin-top: 16px">
      <template #title>
        本期只统计调用次数。智谱 GLM 免费(60 req/min),token 统计暂未启用。
      </template>
    </el-alert>
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>最近调用</template>
      <el-table :data="stats?.recent || []" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="scenario" label="场景" width="120" />
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            {{ fmtMs(row.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.llm-usage { padding: 0; }
.header-card { margin-bottom: 16px; }
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-row h2 { margin: 0; font-size: 20px; }

.metric-card {
  text-align: center;
  padding: 16px 0;
}
.metric-label { color: #909399; font-size: 14px; }
.metric-value {
  font-size: 32px;
  font-weight: bold;
  color: #1f4e79;
  margin: 8px 0;
}
.metric-value small { font-size: 14px; color: #909399; }
.metric-sub { color: #67c23a; font-size: 14px; }
</style>
