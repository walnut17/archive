<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type DictType,
  type DictItem,
  listDictTypes,
  createDictType,
  listDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
} from '@/api/archive'

// --- state ---
const types = ref<DictType[]>([])
const selectedType = ref<DictType | null>(null)
const items = ref<DictItem[]>([])

const typeDialogVisible = ref(false)
const typeForm = ref<Partial<DictType>>({ typeCode: '', typeName: '', description: '', sortOrder: 0 })

const itemDialogVisible = ref(false)
const itemForm = ref<Partial<DictItem>>({ itemKey: '', itemValue: '', sortOrder: 0, enabled: true, remark: '' })
const editingItemId = ref<number | null>(null)
const loading = ref(false)

// --- type methods ---
async function loadTypes() {
  try {
    types.value = await listDictTypes()
  } catch (e: any) {
    ElMessage.error('加载字典分类失败: ' + (e.message || e))
  }
}

function openTypeDialog() {
  typeForm.value = { typeCode: '', typeName: '', description: '', sortOrder: 0 }
  typeDialogVisible.value = true
}

async function saveType() {
  if (!typeForm.value.typeCode || !typeForm.value.typeName) {
    ElMessage.warning('请填写分类编码和名称')
    return
  }
  try {
    await createDictType(typeForm.value)
    ElMessage.success('分类创建成功')
    typeDialogVisible.value = false
    await loadTypes()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e.message || e))
  }
}

function onTypeRowClick(row: DictType) {
  selectedType.value = row
  loadItems(row.typeCode)
}

// --- item methods ---
async function loadItems(typeCode: string) {
  loading.value = true
  try {
    items.value = await listDictItems(typeCode)
  } catch (e: any) {
    ElMessage.error('加载字典项失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function openItemDialog(item?: DictItem) {
  if (item) {
    editingItemId.value = item.id!
    itemForm.value = {
      itemKey: item.itemKey,
      itemValue: item.itemValue,
      sortOrder: item.sortOrder,
      enabled: item.enabled,
      remark: item.remark,
    }
  } else {
    editingItemId.value = null
    itemForm.value = { itemKey: '', itemValue: '', sortOrder: 0, enabled: true, remark: '' }
  }
  itemDialogVisible.value = true
}

async function saveItem() {
  if (!itemForm.value.itemKey || !itemForm.value.itemValue) {
    ElMessage.warning('请填写编码和值')
    return
  }
  if (!selectedType.value) {
    ElMessage.warning('请先选择字典分类')
    return
  }
  try {
    if (editingItemId.value) {
      await updateDictItem(editingItemId.value, { ...itemForm.value, typeCode: selectedType.value.typeCode })
      ElMessage.success('更新成功')
    } else {
      await createDictItem({ ...itemForm.value, typeCode: selectedType.value.typeCode })
      ElMessage.success('创建成功')
    }
    itemDialogVisible.value = false
    await loadItems(selectedType.value.typeCode)
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

async function confirmDeleteItem(item: DictItem) {
  if (item.isSystem) {
    ElMessage.warning('系统预置,不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除字典项 "${item.itemKey}: ${item.itemValue}"?`, '确认')
    await deleteDictItem(item.id!)
    ElMessage.success('删除成功')
    if (selectedType.value) await loadItems(selectedType.value.typeCode)
  } catch {
    // canceled
  }
}

onMounted(() => {
  loadTypes()
})
</script>

<template>
  <div class="admin-dict">
    <h2>字典管理</h2>
    <div class="dict-body">
      <!-- left: type list -->
      <div class="dict-left">
        <div class="panel-header">
          <span>字典分类</span>
          <el-button size="small" type="primary" @click="openTypeDialog">新增分类</el-button>
        </div>
        <el-table
          :data="types"
          highlight-current-row
          @row-click="onTypeRowClick"
          height="calc(100vh - 240px)"
          size="small"
        >
          <el-table-column prop="typeCode" label="分类编码" width="120" />
          <el-table-column prop="typeName" label="分类名称" width="120" />
          <el-table-column prop="description" label="描述" min-width="100" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="60" />
        </el-table>
      </div>

      <!-- right: items of selected type -->
      <div class="dict-right">
        <div class="panel-header">
          <span>字典项 {{ selectedType ? '- ' + selectedType.typeName : '' }}</span>
          <el-button
            size="small"
            type="primary"
            :disabled="!selectedType"
            @click="openItemDialog()"
          >
            新增项
          </el-button>
        </div>
        <el-table
          :data="items"
          v-loading="loading"
          height="calc(100vh - 240px)"
          size="small"
        >
          <el-table-column prop="itemKey" label="编码" width="120" />
          <el-table-column prop="itemValue" label="值" width="160" />
          <el-table-column prop="sortOrder" label="排序" width="60" />
          <el-table-column label="启用" width="70">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="系统" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.isSystem" type="warning" size="small">预置</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openItemDialog(row)">编辑</el-button>
              <el-tooltip v-if="row.isSystem" content="系统预置,不可删除" placement="top">
                <el-button size="small" text type="danger" disabled>删除</el-button>
              </el-tooltip>
              <el-button v-else size="small" text type="danger" @click="confirmDeleteItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Type dialog -->
    <el-dialog v-model="typeDialogVisible" title="新增字典分类" width="500px">
      <el-form :model="typeForm" label-width="100px">
        <el-form-item label="分类编码" required>
          <el-input v-model="typeForm.typeCode" placeholder="如: project_category" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="typeForm.typeName" placeholder="如: 项目分类" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="typeForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- Item dialog -->
    <el-dialog v-model="itemDialogVisible" :title="editingItemId ? '编辑字典项' : '新增字典项'" width="500px">
      <el-form :model="itemForm" label-width="100px">
        <el-form-item label="编码" required>
          <el-input v-model="itemForm.itemKey" placeholder="如: equity" />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="itemForm.itemValue" placeholder="如: 股权类" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="itemForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="itemForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-dict {
  height: 100%;
}

.admin-dict h2 {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #303133;
}

.dict-body {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
}

.dict-left {
  width: 380px;
  flex-shrink: 0;
}

.dict-right {
  flex: 1;
  min-width: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
</style>
