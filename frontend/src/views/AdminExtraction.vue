<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type ExtractionMethod,
  listExtractionMethods,
  createExtractionMethod,
  updateExtractionMethod,
  deleteExtractionMethod,
} from '@/api/archive'

const methods = ref<ExtractionMethod[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref<Partial<ExtractionMethod>>({ name: '', targetField: '', promptTemplate: '', enabled: true })
const loading = ref(false)

const fieldOptions = [
  { value: 'summary', label: '摘要' },
  { value: 'riskPoint', label: '风险点' },
  { value: 'keyIndicator', label: '关键指标' },
  { value: 'decision', label: '决策依据' },
  { value: 'opinion', label: '委员意见' },
]

async function loadData() {
  loading.value = true
  try {
    methods.value = await listExtractionMethods()
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function openDialog(item?: ExtractionMethod) {
  if (item) {
    editingId.value = item.id!
    form.value = {
      name: item.name,
      targetField: item.targetField,
      promptTemplate: item.promptTemplate,
      enabled: item.enabled,
    }
  } else {
    editingId.value = null
    form.value = { name: '', targetField: '', promptTemplate: '', enabled: true }
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写名称')
    return
  }
  try {
    if (editingId.value) {
      await updateExtractionMethod(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createExtractionMethod(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

async function confirmDelete(item: ExtractionMethod) {
  if (item.isBuiltin) {
    ElMessage.warning('系统内置方法,不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除抽取方法 "${item.name}"?`, '确认')
    await deleteExtractionMethod(item.id!)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // canceled
  }
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>抽取方法管理</h2>
      <el-button type="primary" @click="openDialog()">新增方法</el-button>
    </div>

    <el-table :data="methods" v-loading="loading" size="small" stripe>
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column label="目标字段" width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ row.targetField }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="promptTemplate" label="提示词" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="truncate-text">{{ row.promptTemplate }}</span>
        </template>
      </el-table-column>
      <el-table-column label="内置" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isBuiltin" type="warning" size="small">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <el-tooltip v-if="row.isBuiltin" content="系统内置,不可删除" placement="top">
            <el-button size="small" text type="danger" disabled>删除</el-button>
          </el-tooltip>
          <el-button v-else size="small" text type="danger" @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add/Edit dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑抽取方法' : '新增抽取方法'" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如: 风险点抽取" />
        </el-form-item>
        <el-form-item label="目标字段">
          <el-select v-model="form.targetField" placeholder="选择目标字段" clearable>
            <el-option
              v-for="opt in fieldOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-input
            v-model="form.promptTemplate"
            type="textarea"
            :rows="5"
            placeholder="输入LLM提示词模板"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.truncate-text {
  display: inline-block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
