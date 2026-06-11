<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProjectBoard, type ProjectBoardItem } from '@/api/archive'

const router = useRouter()
const loading = ref(false)
const view = ref<'table' | 'card' | 'kanban'>('table')
const items = ref<ProjectBoardItem[]>([])
const kanban = ref<Record<string, ProjectBoardItem[]>>({})
const total = ref(0)
const query = ref({ region: '', stage: '', sort: 'updatedAt', order: 'desc', page: 1, size: 20 })

async function fetch() {
  loading.value = true
  try {
    const res = await listProjectBoard({ ...query.value, view: view.value })
    items.value = res.items || []
    kanban.value = res.kanban || {}
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function switchView(v: 'table' | 'card' | 'kanban') {
  view.value = v
  fetch()
}

function goDetail(row: ProjectBoardItem) {
  router.push({ name: 'project-detail', params: { id: String(row.id) } })
}

onMounted(fetch)
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">项目看板</h2>
      <el-radio-group v-model="view" @change="switchView(view)">
        <el-radio-button value="table">表格</el-radio-button>
        <el-radio-button value="card">卡片</el-radio-button>
        <el-radio-button value="kanban">看板</el-radio-button>
      </el-radio-group>
    </div>

    <el-form inline :model="query" style="margin-bottom: 12px">
      <el-form-item label="区域">
        <el-input v-model="query.region" placeholder="类别/区域" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="阶段">
        <el-input v-model="query.stage" placeholder="状态" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-if="view === 'table'" :data="items" v-loading="loading" stripe border>
      <el-table-column prop="code" label="编号" width="140" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="region" label="区域" width="100" />
      <el-table-column prop="stage" label="阶段" width="100" />
      <el-table-column prop="amount" label="金额(万)" width="100" align="right" />
      <el-table-column prop="proposalCount" label="议案数" width="80" align="center" />
      <el-table-column prop="todoCount" label="待办数" width="80" align="center" />
      <el-table-column prop="lastUpdated" label="最后更新" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-row v-else-if="view === 'card'" :gutter="16" v-loading="loading">
      <el-col v-for="item in items" :key="item.id" :span="8" style="margin-bottom: 16px">
        <el-card shadow="hover" @click="goDetail(item)" style="cursor: pointer">
          <template #header>{{ item.code }}</template>
          <p><strong>{{ item.name }}</strong></p>
          <p>阶段: {{ item.stage }} | 金额: {{ item.amount ?? '—' }}万</p>
          <p>议案 {{ item.proposalCount }} / 待办 {{ item.todoCount }}</p>
        </el-card>
      </el-col>
    </el-row>

    <div v-else class="kanban" v-loading="loading">
      <div v-for="(group, stage) in kanban" :key="stage" class="kanban-col">
        <h4>{{ stage }} ({{ group.length }})</h4>
        <el-card v-for="item in group" :key="item.id" shadow="hover" style="margin-bottom: 8px; cursor: pointer" @click="goDetail(item)">
          <strong>{{ item.name }}</strong>
          <div style="font-size: 12px; color: #909399">{{ item.code }}</div>
        </el-card>
      </div>
    </div>

    <el-pagination
      v-if="view !== 'kanban'"
      style="margin-top: 16px"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetch"
    />
  </div>
</template>

<style scoped>
.kanban { display: flex; gap: 16px; overflow-x: auto; }
.kanban-col { min-width: 240px; background: #f5f7fa; padding: 12px; border-radius: 8px; }
</style>
