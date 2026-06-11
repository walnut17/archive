<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRecycleBin, restoreRecycleBin } from '@/api/archive'

const loading = ref(false)
const entityType = ref('project')
const list = ref<Record<string, unknown>[]>([])

async function fetch() {
  loading.value = true
  try {
    list.value = await listRecycleBin(entityType.value)
  } finally {
    loading.value = false
  }
}

async function onRestore(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确定恢复此项?', '恢复确认', { type: 'info' })
  } catch { return }
  await restoreRecycleBin(entityType.value, Number(row.id))
  ElMessage.success('已恢复')
  fetch()
}

onMounted(fetch)
</script>

<template>
  <div>
    <h2>回收站</h2>
    <el-radio-group v-model="entityType" @change="fetch" style="margin-bottom: 16px">
      <el-radio-button value="project">项目</el-radio-button>
      <el-radio-button value="proposal">议案</el-radio-button>
      <el-radio-button value="material">材料</el-radio-button>
    </el-radio-group>

    <el-table :data="list" v-loading="loading" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="code" label="编号" width="160" />
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column prop="deleted_at" label="删除时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="onRestore(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
