<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  listProjects, deleteProject,
  type Project,
  projectStatusOptions, projectCategoryOptions,
} from '../api/archive'

const router = useRouter()
const loading = ref(false)
const list = ref<Project[]>([])
const total = ref(0)
const query = ref({
  page: 1,
  size: 20,
  status: '',
  keyword: '',
})

async function fetch() {
  loading.value = true
  try {
    const page = await listProjects({ ...query.value, page: query.value.page - 1 })
    list.value = page.content
    total.value = page.totalElements
  } finally {
    loading.value = false
  }
}

function reset() {
  query.value = { page: 1, size: 20, status: '', keyword: '' }
  fetch()
}

async function onDelete(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${row.name}」?此操作不可恢复。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteProject(row.id!)
  ElMessage.success('删除成功')
  fetch()
}

function goCreate() {
  router.push({ name: 'project-create-upload' })
}

function goEdit(row: Project) {
  router.push({ name: 'project-form', params: { id: String(row.id) } })
}

function goDetail(row: Project) {
  router.push({ name: 'project-detail', params: { id: String(row.id) } })
}

function statusTagType(status: string) {
  switch (status) {
    case '通过': return 'success'
    case '否决': return 'danger'
    case '暂缓': return 'warning'
    case '审议中': return 'primary'
    case '待审议': return 'info'
    default: return 'default'
  }
}

onMounted(fetch)
</script>

<template>
  <div>
    <h2>项目列表</h2>

    <el-form inline :model="query" style="margin-bottom: 12px">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="编号/名称/摘要" clearable style="width: 200px" @keyup.enter="fetch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="s in projectStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetch">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" @click="goCreate">+ 新建项目</el-button>
        <el-button @click="router.push('/projects/board')">项目看板</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe border>
      <el-table-column prop="code" label="编号" width="140" />
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="amountWan" label="金额(万)" width="100" align="right" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="scheduledMeetingAt" label="审议日期" width="120" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="fetch"
      @size-change="fetch"
    />
  </div>
</template>
