<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProposal, listMaterials, createMaterial, deleteMaterial,
  listVersions, uploadVersion, switchCurrentVersion, deleteVersion,
  downloadVersionUrl, reparseVersion, listSections,
  type Proposal, type Material, type MaterialVersion, type Section,
  materialStatusOptions, materialCategoryOptions,
} from '../api/archive'

const route = useRoute()
const router = useRouter()
const proposalId = ref(Number(route.params.id))
const proposal = ref<Proposal | null>(null)
const materials = ref<Material[]>([])
const loading = ref(false)

const showMaterialForm = ref(false)
const editingMaterial = ref<Material | null>(null)
const materialForm = ref<Material>({
  proposalId: proposalId.value,
  title: '',
  category: '尽调报告',
  status: '草稿',
  description: '',
  tags: '',
})

const showVersions = ref(false)
const currentMaterial = ref<Material | null>(null)
const versions = ref<MaterialVersion[]>([])
const showSections = ref(false)
const sections = ref<Section[]>([])

async function fetch() {
  loading.value = true
  try {
    const [p, ms] = await Promise.all([
      getProposal(proposalId.value),
      listMaterials({ proposalId: proposalId.value, size: 100 }),
    ])
    proposal.value = p
    materials.value = ms.content || []
  } finally {
    loading.value = false
  }
}

function goCreateMaterial() {
  editingMaterial.value = null
  materialForm.value = { proposalId: proposalId.value, title: '', category: '尽调报告', status: '草稿', description: '', tags: '' }
  showMaterialForm.value = true
}

function goEditMaterial(row: Material) {
  editingMaterial.value = row
  materialForm.value = { ...row }
  showMaterialForm.value = true
}

async function onSaveMaterial() {
  if (!materialForm.value.title) {
    ElMessage.error('材料标题必填')
    return
  }
  try {
    if (editingMaterial.value) {
      await (await import('../api/archive')).updateMaterial(editingMaterial.value.id!, materialForm.value)
      ElMessage.success('更新成功')
    } else {
      await createMaterial(materialForm.value)
      ElMessage.success('创建成功')
    }
    showMaterialForm.value = false
    fetch()
  } catch {}
}

async function onDeleteMaterial(row: Material) {
  try {
    await ElMessageBox.confirm(`确定删除材料「${row.title}」?`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteMaterial(row.id!)
  ElMessage.success('删除成功')
  fetch()
}

// ---- 版本管理 ----
async function openVersions(m: Material) {
  currentMaterial.value = m
  versions.value = await listVersions(m.id!)
  showVersions.value = true
}

async function onUploadVersion(m: Material) {
  const input = document.createElement('input')
  input.type = 'file'
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    const note = prompt('版本说明(可选):') || ''
    try {
      const resp: any = await uploadVersion(m.id!, file, note)
      ElMessage.success(`上传成功 v${resp.versionNo},解析状态:${resp.parseStatus}`)
      openVersions(m)
      fetch()
    } catch {}
  }
  input.click()
}

async function onSwitchCurrent(m: Material, v: MaterialVersion) {
  await switchCurrentVersion(m.id!, v.id!)
  ElMessage.success(`已切换到 v${v.versionNo}`)
  openVersions(m)
  fetch()
}

async function onDeleteVersion(m: Material, v: MaterialVersion) {
  try {
    await ElMessageBox.confirm(`确定删除版本 v${v.versionNo}?`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteVersion(m.id!, v.id!)
  ElMessage.success('删除成功')
  openVersions(m)
  fetch()
}

async function onReparse(m: Material, v: MaterialVersion) {
  await reparseVersion(m.id!, v.id!)
  ElMessage.success('重新解析已触发')
  openVersions(m)
}

async function onShowSections(m: Material, v: MaterialVersion) {
  sections.value = await listSections(m.id!, v.id!)
  currentMaterial.value = m
  showSections.value = true
}

function parseStatusType(s: string) {
  switch (s) {
    case 'success': return 'success'
    case 'failed': return 'danger'
    case 'running': return 'primary'
    default: return 'info'
  }
}

onMounted(fetch)
</script>

<template>
  <div v-loading="loading" v-if="proposal">
    <el-page-header @back="router.back()">
      <template #content>
        <span style="font-size: 18px">议案详情</span>
      </template>
    </el-page-header>

    <el-descriptions :column="2" border style="margin-top: 16px">
      <el-descriptions-item label="编号">{{ proposal.code }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ proposal.title }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ proposal.type }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag>{{ proposal.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="审议日期">{{ proposal.reviewedAt || '—' }}</el-descriptions-item>
      <el-descriptions-item label="项目ID">{{ proposal.projectId }}</el-descriptions-item>
      <el-descriptions-item label="摘要" :span="2">{{ proposal.summary || '—' }}</el-descriptions-item>
      <el-descriptions-item v-if="proposal.decision" label="审议结论" :span="2">{{ proposal.decision }}</el-descriptions-item>
    </el-descriptions>

    <div style="margin: 24px 0 12px; display: flex; justify-content: space-between; align-items: center">
      <h3 style="margin: 0">材料列表({{ materials.length }})</h3>
      <el-button type="success" @click="goCreateMaterial">+ 新建材料</el-button>
    </div>

    <el-table :data="materials" stripe border>
      <el-table-column prop="title" label="材料标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="versionCount" label="版本数" width="80" align="right" />
      <el-table-column prop="tags" label="标签" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openVersions(row)">版本管理</el-button>
          <el-button link type="primary" @click="onUploadVersion(row)">上传版本</el-button>
          <el-button link type="primary" @click="goEditMaterial(row)">编辑</el-button>
          <el-button link type="danger" @click="onDeleteMaterial(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 材料编辑弹窗 -->
    <el-dialog v-model="showMaterialForm" :title="editingMaterial ? '编辑材料' : '新建材料'" width="640">
      <el-form :model="materialForm" label-width="100px">
        <el-form-item label="材料标题" required>
          <el-input v-model="materialForm.title" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="materialForm.category" style="width: 200px">
            <el-option v-for="c in materialCategoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="materialForm.status" style="width: 200px">
            <el-option v-for="s in materialStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="materialForm.tags" placeholder="逗号分隔,如 重要,机密" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="materialForm.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMaterialForm = false">取消</el-button>
        <el-button type="primary" @click="onSaveMaterial">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本管理弹窗 -->
    <el-dialog v-model="showVersions" :title="`版本管理 — ${currentMaterial?.title}`" width="900">
      <el-table :data="versions" stripe>
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小(B)" width="100" align="right" />
        <el-table-column prop="parseStatus" label="解析状态" width="100">
          <template #default="{ row }">
            <el-tag :type="parseStatusType(row.parseStatus)">{{ row.parseStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadedBy" label="上传人" width="100" />
        <el-table-column prop="changeNote" label="说明" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="上传时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onSwitchCurrent(currentMaterial!, row)">设当前</el-button>
            <el-button link type="primary" @click="onShowSections(currentMaterial!, row)">章节</el-button>
            <el-button link type="primary" @click="onReparse(currentMaterial!, row)">重解析</el-button>
            <el-button link>
              <a :href="downloadVersionUrl(currentMaterial!.id!, row.id!)" target="_blank">下载</a>
            </el-button>
            <el-button link type="danger" @click="onDeleteVersion(currentMaterial!, row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 章节弹窗 -->
    <el-dialog v-model="showSections" :title="`章节切分 — ${currentMaterial?.title}`" width="800">
      <el-collapse>
        <el-collapse-item v-for="s in sections" :key="s.index" :name="s.index">
          <template #title>
            <span style="font-weight: 500">[{{ s.index + 1 }}] {{ s.title }}</span>
            <el-tag size="small" style="margin-left: 8px">{{ s.length }} 字</el-tag>
          </template>
          <pre style="white-space: pre-wrap; max-height: 400px; overflow: auto; background: #f5f5f5; padding: 12px; border-radius: 4px">{{ s.content }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>
  </div>
</template>
