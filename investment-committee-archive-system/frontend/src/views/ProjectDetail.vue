<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProject, listProposals, createProposal, deleteProposal,
  type Project, type Proposal,
  proposalStatusOptions, proposalTypeOptions,
} from '../api/archive'

const route = useRoute()
const router = useRouter()
const projectId = ref(Number(route.params.id))
const project = ref<Project | null>(null)
const proposals = ref<Proposal[]>([])
const loading = ref(false)
const showForm = ref(false)
const editing = ref<Proposal | null>(null)

const form = ref<Proposal>({
  code: '',
  title: '',
  projectId: projectId.value,
  type: '主体',
  status: '草稿',
  summary: '',
  remark: '',
})

async function fetch() {
  loading.value = true
  try {
    const [p, pl] = await Promise.all([
      getProject(projectId.value),
      listProposals({ projectId: projectId.value, size: 100 }),
    ])
    project.value = (p as any).data
    proposals.value = ((pl as any).data.content) || []
  } finally {
    loading.value = false
  }
}

function goCreate() {
  editing.value = null
  form.value = { code: '', title: '', projectId: projectId.value, type: '主体', status: '草稿', summary: '', remark: '' }
  showForm.value = true
}

function goEdit(row: Proposal) {
  editing.value = row
  form.value = { ...row }
  showForm.value = true
}

async function onSave() {
  if (!form.value.code || !form.value.title) {
    ElMessage.error('编号和标题必填')
    return
  }
  try {
    if (editing.value) {
      await (await import('../api/archive')).updateProposal(editing.value.id!, form.value)
      ElMessage.success('更新成功')
    } else {
      await createProposal(form.value)
      ElMessage.success('创建成功')
    }
    showForm.value = false
    fetch()
  } catch (e) {
    // 错误已由拦截器弹
  }
}

async function onDelete(row: Proposal) {
  try {
    await ElMessageBox.confirm(`确定删除议案「${row.title}」?`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteProposal(row.id!)
  ElMessage.success('删除成功')
  fetch()
}

function goProposalDetail(p: Proposal) {
  router.push({ name: 'proposal-detail', params: { id: String(p.id) } })
}

onMounted(fetch)
</script>

<template>
  <div v-loading="loading" v-if="project">
    <el-page-header @back="router.back()">
      <template #content>
        <span style="font-size: 18px">项目详情</span>
      </template>
    </el-page-header>

    <el-descriptions :column="2" border style="margin-top: 16px">
      <el-descriptions-item label="编号">{{ project.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ project.name }}</el-descriptions-item>
      <el-descriptions-item label="类别">{{ project.category }}</el-descriptions-item>
      <el-descriptions-item label="金额(万)">{{ project.amountWan }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag>{{ project.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="审议日期">{{ project.scheduledMeetingAt || '—' }}</el-descriptions-item>
      <el-descriptions-item label="摘要" :span="2">{{ project.summary || '—' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ project.remark || '—' }}</el-descriptions-item>
    </el-descriptions>

    <div style="margin: 24px 0 12px; display: flex; justify-content: space-between; align-items: center">
      <h3 style="margin: 0">议案列表({{ proposals.length }})</h3>
      <el-button type="success" @click="goCreate">+ 新建议案</el-button>
    </div>

    <el-table :data="proposals" stripe border>
      <el-table-column prop="code" label="编号" width="160" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag>{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reviewedAt" label="审议日期" width="120" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goProposalDetail(row)">详情 / 材料</el-button>
          <el-button link type="primary" @click="goEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showForm" :title="editing ? '编辑议案' : '新建议案'" width="640">
      <el-form :model="form" label-width="100px">
        <el-form-item label="议案编号" required>
          <el-input v-model="form.code" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="议案标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="议案类型">
          <el-select v-model="form.type" style="width: 200px">
            <el-option v-for="t in proposalTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option v-for="s in proposalStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
