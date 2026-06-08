<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type TriggerRule,
  listTriggerRules,
  createTriggerRule,
  updateTriggerRule,
  deleteTriggerRule,
} from '@/api/archive'

const rules = ref<TriggerRule[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const loading = ref(false)

const eventTypeOptions = [
  { value: '材料分类', label: '材料分类' },
  { value: '状态变更', label: '状态变更' },
]

const actionTypeOptions = [
  { value: '创建待办', label: '创建待办' },
  { value: '发送通知', label: '发送通知' },
]

const priorityOptions = [
  { value: '高', label: '高' },
  { value: '中', label: '中' },
  { value: '低', label: '低' },
]

const form = ref<{
  name: string
  description: string
  eventType: string
  actionType: string
  enabled: boolean
  // action config fields (simplified)
  todoTitle: string
  todoPriority: string
  todoDueDays: number
}>({
  name: '',
  description: '',
  eventType: '',
  actionType: '',
  enabled: true,
  todoTitle: '',
  todoPriority: '中',
  todoDueDays: 3,
})

const showTodoFields = computed(() => form.value.actionType === '创建待办')

function buildActionConfig(): string {
  const config: Record<string, any> = {}
  if (form.value.actionType === '创建待办') {
    config.title = form.value.todoTitle
    config.priority = form.value.todoPriority
    config.dueDays = form.value.todoDueDays
  } else if (form.value.actionType === '发送通知') {
    config.message = form.value.description || form.value.name
  }
  return JSON.stringify(config)
}

function parseActionConfig(configStr: string | undefined): void {
  if (!configStr) return
  try {
    const config = JSON.parse(configStr)
    if (config.title) form.value.todoTitle = config.title
    if (config.priority) form.value.todoPriority = config.priority
    if (config.dueDays != null) form.value.todoDueDays = config.dueDays
  } catch {
    // ignore parse errors
  }
}

async function loadData() {
  loading.value = true
  try {
    rules.value = await listTriggerRules()
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function openDialog(item?: TriggerRule) {
  if (item) {
    editingId.value = item.id!
    form.value = {
      name: item.name,
      description: item.description || '',
      eventType: item.eventType || '',
      actionType: item.actionType || '',
      enabled: item.enabled ?? true,
      todoTitle: '',
      todoPriority: '中',
      todoDueDays: 3,
    }
    parseActionConfig(item.actionConfig)
  } else {
    editingId.value = null
    form.value = {
      name: '',
      description: '',
      eventType: '',
      actionType: '',
      enabled: true,
      todoTitle: '',
      todoPriority: '中',
      todoDueDays: 3,
    }
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写规则名称')
    return
  }
  if (!form.value.eventType) {
    ElMessage.warning('请选择事件类型')
    return
  }
  if (!form.value.actionType) {
    ElMessage.warning('请选择动作类型')
    return
  }
  const actionConfig = buildActionConfig()
  const payload = {
    name: form.value.name,
    description: form.value.description,
    eventType: form.value.eventType,
    actionType: form.value.actionType,
    actionConfig,
    enabled: form.value.enabled,
  }
  try {
    if (editingId.value) {
      await updateTriggerRule(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createTriggerRule(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

async function confirmDelete(item: TriggerRule) {
  if (item.isBuiltin) {
    ElMessage.warning('系统内置规则,不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除触发规则 "${item.name}"?`, '确认')
    await deleteTriggerRule(item.id!)
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
      <h2>触发规则管理</h2>
      <el-button type="primary" @click="openDialog()">新增规则</el-button>
    </div>

    <el-table :data="rules" v-loading="loading" size="small" stripe>
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="事件类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.eventType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" type="success">{{ row.actionType }}</el-tag>
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑触发规则' : '新增触发规则'" width="600px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="如: 材料上传后创建待办" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="规则说明" />
        </el-form-item>
        <el-form-item label="事件类型" required>
          <el-select v-model="form.eventType" placeholder="选择事件类型" style="width:100%">
            <el-option
              v-for="opt in eventTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="动作类型" required>
          <el-select v-model="form.actionType" placeholder="选择动作类型" style="width:100%">
            <el-option
              v-for="opt in actionTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- Simplified action config: todo fields -->
        <template v-if="showTodoFields">
          <el-form-item label="待办标题">
            <el-input v-model="form.todoTitle" placeholder="如: 审核新上传的材料" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="form.todoPriority" style="width:100%">
              <el-option
                v-for="opt in priorityOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="截止天数">
            <el-input-number v-model="form.todoDueDays" :min="1" :max="30" />
          </el-form-item>
        </template>

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
</style>
